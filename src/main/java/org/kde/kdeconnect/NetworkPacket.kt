/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import org.kde.kdeconnect.extensions.closeSafe
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream
import java.io.StringReader
import java.net.Socket

class NetworkPacket private constructor(
    val type: String,
    private val mBody: JsonObject,
    var payload: Payload?,
    var payloadTransferInfo: JsonObject,
) {
    constructor(type: String) : this(
        type = type,
        mBody = JsonObject(),
        payload = null,
        payloadTransferInfo = JsonObject()
    )

    @Volatile
    var isCanceled: Boolean = false
        private set

    fun cancel() {
        isCanceled = true
    }

    // Returns defaultValue if the key is missing, or if its value isn't a primitive convertible by `convert`
    private inline fun <T> getPrimitive(key: String, defaultValue: T, convert: (JsonPrimitive) -> T): T {
        val primitive = mBody.get(key) as? JsonPrimitive ?: return defaultValue
        return try {
            convert(primitive)
        } catch (_: Exception) {
            defaultValue
        }
    }

    // Most commons getters and setters defined for convenience
    fun getString(key: String): String {
        return getString(key, "")
    }

    fun getStringOrNull(key: String): String? {
        val element = mBody.get(key) ?: return null
        return when {
            element.isJsonNull -> null
            element.isJsonPrimitive -> element.asString
            else -> element.toString()
        }
    }

    fun getString(key: String, defaultValue: String): String {
        return getStringOrNull(key) ?: defaultValue
    }

    operator fun set(key: String, value: String) {
        mBody.addProperty(key, value)
    }

    fun getInt(key: String): Int {
        return getInt(key, -1)
    }

    fun getIntOrNull(key: String): Int? {
        return getPrimitive(key, null) { it.asInt }
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return getPrimitive(key, defaultValue) { it.asInt }
    }

    operator fun set(key: String, value: Int) {
        mBody.addProperty(key, value)
    }

    fun getLong(key: String): Long {
        return getLong(key, -1)
    }

    fun getLong(key: String, defaultValue: Long): Long {
        return getPrimitive(key, defaultValue) { it.asLong }
    }

    operator fun set(key: String, value: Long) {
        mBody.addProperty(key, value)
    }

    fun getBoolean(key: String): Boolean {
        return getBoolean(key, false)
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return getPrimitive(key, defaultValue) {
            when {
                it.isBoolean -> it.asBoolean
                it.isString -> it.asString.lowercase().toBooleanStrictOrNull() ?: defaultValue
                else -> defaultValue
            }
        }
    }

    operator fun set(key: String, value: Boolean) {
        mBody.addProperty(key, value)
    }

    fun getDouble(key: String): Double {
        return getDouble(key, Double.NaN)
    }

    fun getDouble(key: String, defaultValue: Double): Double {
        return getPrimitive(key, defaultValue) { it.asDouble }
    }

    // Note: NaN and Infinity aren't valid JSON
    operator fun set(key: String, value: Double) {
        mBody.addProperty(key, value)
    }

    fun getJsonArray(key: String): JsonArray? {
        return mBody.get(key) as? JsonArray
    }

    operator fun set(key: String, value: JsonArray?) {
        if (value == null) {
            mBody.remove(key)
        } else {
            mBody.add(key, value)
        }
    }

    fun getJsonObject(key: String): JsonObject? {
        return mBody.get(key) as? JsonObject
    }

    operator fun set(key: String, value: JsonObject?) {
        if (value == null) {
            mBody.remove(key)
        } else {
            mBody.add(key, value)
        }
    }

    fun getStringSet(key: String): Set<String>? {
        return getStringList(key)?.toHashSet()
    }

    fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? {
        return if (mBody.has(key)) getStringSet(key)
        else defaultValue
    }

    operator fun set(key: String, value: Set<String>) {
        mBody.add(key, toJsonArray(value))
    }

    fun getStringList(key: String): List<String>? {
        val jsonArray = getJsonArray(key) ?: return null
        val list: MutableList<String> = ArrayList()
        for (element in jsonArray) {
            try {
                list.add(element.asString)
            } catch (_: Exception) {
            }
        }
        return list
    }

    fun getStringList(key: String, defaultValue: List<String>?): List<String>? {
        return if (mBody.has(key)) getStringList(key)
        else defaultValue
    }

    operator fun set(key: String, value: List<String>) {
        mBody.add(key, toJsonArray(value))
    }

    private fun toJsonArray(strings: Iterable<String>): JsonArray {
        val jsonArray = JsonArray()
        for (str in strings) {
            jsonArray.add(str)
        }
        return jsonArray
    }

    fun has(key: String): Boolean {
        return mBody.has(key)
    }

    operator fun contains(key: String): Boolean {
        return has(key)
    }

    fun serialize(): String {
        val jo = JsonObject()
        jo.addProperty("id", System.currentTimeMillis())
        jo.addProperty("type", type)
        jo.add("body", mBody)
        if (hasPayload()) {
            jo.addProperty("payloadSize", payload!!.payloadSize)
            jo.add("payloadTransferInfo", payloadTransferInfo)
        }
        try {
            return jo.toString() + "\n"
        } catch (e : OutOfMemoryError) {
            throw RuntimeException("OOM serializing packet of type $type", e)
        }
    }

    val payloadSize: Long
        get() = payload?.payloadSize ?: 0

    fun hasPayload(): Boolean {
        val payload = payload
        // FIXME: Should payload.payloadSize != 0L be an assert instead?
        return payload != null && payload.payloadSize != 0L
    }

    fun hasPayloadTransferInfo(): Boolean {
        return payloadTransferInfo.size() > 0
    }

    class Payload : Closeable {
        /**
         * **NOTE: Do not close the InputStream directly call Payload.close() instead, this is because of this [bug](https://issuetracker.google.com/issues/37018094)**
         */
        val inputStream: InputStream?
        private val inputSocket: Socket?
        val payloadSize: Long

        constructor(payloadSize: Long) : this(null, payloadSize)

        constructor(data: ByteArray) : this(ByteArrayInputStream(data), data.size.toLong())

        /**
         * **NOTE: Do not use this to set an SSLSockets InputStream as the payload, use Payload(Socket, long) instead because of this [bug](https://issuetracker.google.com/issues/37018094)**
         */
        constructor(inputStream: InputStream?, payloadSize: Long) {
            this.inputSocket = null
            this.inputStream = inputStream
            this.payloadSize = payloadSize
        }

        constructor(inputSocket: Socket, payloadSize: Long) {
            this.inputSocket = inputSocket
            this.inputStream = inputSocket.getInputStream()
            this.payloadSize = payloadSize
        }

        override fun close() {
            inputStream?.closeSafe()
            inputSocket?.closeSafe()
        }
    }

    companion object {
        const val PACKET_TYPE_IDENTITY: String = "kdeconnect.identity"
        const val PACKET_TYPE_PAIR: String = "kdeconnect.pair"

        val PROTOCOL_PACKET_TYPES: List<String> = listOf(
            PACKET_TYPE_IDENTITY,
            PACKET_TYPE_PAIR
        )

        @Throws(JsonParseException::class)
        fun parseJsonObject(s: String): JsonObject {
            val reader = JsonReader(StringReader(s))
            return JsonParser.parseReader(reader) as? JsonObject
                ?: throw JsonParseException("Not a JSON object")
        }

        @Throws(JsonParseException::class)
        fun unserialize(s: String): NetworkPacket {
            val jo = parseJsonObject(s)
            val type = (jo.get("type") as? JsonPrimitive)?.takeIf { it.isString }?.asString
                ?: throw JsonParseException("Packet has no type")
            val mBody = jo.get("body") as? JsonObject
                ?: throw JsonParseException("Packet has no body")

            val hasPayload = jo.has("payloadSize")
            val payloadTransferInfo = if (hasPayload) {
                jo.get("payloadTransferInfo") as? JsonObject
                    ?: throw JsonParseException("Packet has payloadSize but no payloadTransferInfo")
            } else {
                JsonObject()
            }
            val payload = if (hasPayload) {
                val payloadSize = try {
                    jo.get("payloadSize").asLong
                } catch (e: Exception) {
                    throw JsonParseException("Invalid payloadSize", e)
                }
                Payload(payloadSize)
            } else {
                null
            }
            return NetworkPacket(type, mBody, payload, payloadTransferInfo)
        }
    }
}
