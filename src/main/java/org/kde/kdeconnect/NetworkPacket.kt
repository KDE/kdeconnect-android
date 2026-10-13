/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.kde.kdeconnect.extensions.closeSafe
import org.kde.kdeconnect.helpers.JsonHelper.json
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream
import java.net.Socket

class NetworkPacket private constructor(
    val type: String,
    private val mBody: MutableMap<String, JsonElement>,
    var payload: Payload?,
    var payloadTransferInfo: JsonObject,
) {
    constructor(type: String) : this(
        type = type,
        mBody = LinkedHashMap(),
        payload = null,
        payloadTransferInfo = JsonObject(emptyMap())
    )

    @Volatile
    var isCanceled: Boolean = false
        private set

    fun cancel() {
        isCanceled = true
    }

    // Returns the value as a primitive, or null if it's missing, JSON null or not a primitive
    private fun primitive(key: String): JsonPrimitive? {
        val value = mBody[key]
        return if (value is JsonPrimitive && value !is JsonNull) value else null
    }

    fun getString(key: String): String {
        return getString(key, "")
    }

    fun getStringOrNull(key: String): String? {
        return when (val value = mBody[key]) {
            null, JsonNull -> null
            is JsonPrimitive -> value.content
            else -> value.toString()
        }
    }

    fun getString(key: String, defaultValue: String): String {
        return getStringOrNull(key) ?: defaultValue
    }

    operator fun set(key: String, value: String) {
        mBody[key] = JsonPrimitive(value)
    }

    fun getInt(key: String): Int {
        return getInt(key, -1)
    }

    fun getIntOrNull(key: String): Int? {
        val content = primitive(key)?.content ?: return null
        return content.toIntOrNull() ?: content.toDoubleOrNull()?.toInt()
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return getIntOrNull(key) ?: defaultValue
    }

    operator fun set(key: String, value: Int) {
        mBody[key] = JsonPrimitive(value)
    }

    fun getLong(key: String): Long {
        return getLong(key, -1)
    }

    fun getLong(key: String, defaultValue: Long): Long {
        val content = primitive(key)?.content ?: return defaultValue
        return content.toLongOrNull() ?: content.toDoubleOrNull()?.toLong() ?: defaultValue
    }

    operator fun set(key: String, value: Long) {
        mBody[key] = JsonPrimitive(value)
    }

    fun getBoolean(key: String): Boolean {
        return getBoolean(key, false)
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val content = primitive(key)?.content ?: return defaultValue
        return when {
            content.equals("true", ignoreCase = true) -> true
            content.equals("false", ignoreCase = true) -> false
            else -> defaultValue
        }
    }

    operator fun set(key: String, value: Boolean) {
        mBody[key] = JsonPrimitive(value)
    }

    fun getDouble(key: String): Double {
        return getDouble(key, Double.NaN)
    }

    fun getDouble(key: String, defaultValue: Double): Double {
        return primitive(key)?.content?.toDoubleOrNull() ?: defaultValue
    }

    operator fun set(key: String, value: Double) {
        // NaN and infinity can't be represented in JSON
        if (value.isFinite()) {
            mBody[key] = JsonPrimitive(value)
        }
    }

    fun getJsonArray(key: String): JsonArray? {
        return mBody[key] as? JsonArray
    }

    operator fun set(key: String, value: JsonArray?) {
        if (value == null) {
            mBody.remove(key)
        } else {
            mBody[key] = value
        }
    }

    fun getJsonObject(key: String): JsonObject? {
        return mBody[key] as? JsonObject
    }

    operator fun set(key: String, value: JsonObject?) {
        if (value == null) {
            mBody.remove(key)
        } else {
            mBody[key] = value
        }
    }

    private fun JsonArray.toStringList(): List<String> {
        return mapNotNull { element ->
            when (element) {
                JsonNull -> null
                is JsonPrimitive -> element.content
                else -> element.toString()
            }
        }
    }

    fun getStringSet(key: String): Set<String>? {
        return getJsonArray(key)?.toStringList()?.toHashSet()
    }

    fun getStringSet(key: String, defaultValue: Set<String>): Set<String> {
        return getStringSet(key) ?: defaultValue
    }

    operator fun set(key: String, value: Set<String>) {
        mBody[key] = JsonArray(value.map(::JsonPrimitive))
    }

    fun getStringList(key: String): List<String>? {
        return getJsonArray(key)?.toStringList()
    }

    fun getStringList(key: String, defaultValue: List<String>): List<String> {
        return getStringList(key) ?: defaultValue
    }

    operator fun set(key: String, value: List<String>) {
        mBody[key] = JsonArray(value.map(::JsonPrimitive))
    }

    fun has(key: String): Boolean {
        return mBody.containsKey(key)
    }

    operator fun contains(key: String): Boolean {
        return has(key)
    }

    @Throws(SerializationException::class)
    fun serialize(): String {
        val hasPayload = hasPayload()
        val serialized = SerializedPacket(
            id = System.currentTimeMillis(),
            type = type,
            body = JsonObject(mBody),
            payloadSize = if (hasPayload) payload!!.payloadSize else null,
            payloadTransferInfo = if (hasPayload) payloadTransferInfo else null,
        )
        try {
            return json.encodeToString(serialized) + "\n"
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
        return payloadTransferInfo.isNotEmpty()
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

    /** The on-the-wire representation of a packet */
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    private class SerializedPacket(
        val id: Long = 0,
        val type: String,
        val body: JsonObject,
        @EncodeDefault(EncodeDefault.Mode.NEVER) // omit instead of writing null, peers check for the key's presence
        val payloadSize: Long? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val payloadTransferInfo: JsonObject? = null,
    )

    companion object {
        const val PACKET_TYPE_IDENTITY: String = "kdeconnect.identity"
        const val PACKET_TYPE_PAIR: String = "kdeconnect.pair"

        val PROTOCOL_PACKET_TYPES: List<String> = listOf(
            PACKET_TYPE_IDENTITY,
            PACKET_TYPE_PAIR
        )

        @Throws(SerializationException::class)
        fun unserialize(s: String): NetworkPacket {
            val serialized = json.decodeFromString<SerializedPacket>(s)
            val payloadSize = serialized.payloadSize
            val payload = if (payloadSize != null) Payload(payloadSize) else null
            val payloadTransferInfo = if (payloadSize != null) {
                serialized.payloadTransferInfo ?: throw SerializationException("Packet has payloadSize but no payloadTransferInfo")
            } else {
                JsonObject(emptyMap())
            }
            return NetworkPacket(serialized.type, LinkedHashMap(serialized.body), payload, payloadTransferInfo)
        }
    }
}
