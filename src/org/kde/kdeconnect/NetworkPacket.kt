/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.RuntimeException
import java.net.Socket
import kotlin.concurrent.Volatile

class NetworkPacket private constructor(
    val id: Long,
    val type: String,
    private val mBody: JSONObject,
    var payload: Payload?,
    var payloadTransferInfo: JSONObject,
) {
    constructor(type: String) : this(
        id = System.currentTimeMillis(),
        type = type,
        mBody = JSONObject(),
        payload = null,
        payloadTransferInfo = JSONObject()
    )

    @Volatile
    var isCanceled: Boolean = false
        private set

    fun cancel() {
        isCanceled = true
    }

    // Most commons getters and setters defined for convenience
    fun getString(key: String): String {
        return mBody.optString(key, "")
    }

    fun getStringOrNull(key: String): String? {
        return if (mBody.has(key)) mBody.getString(key)
        else null
    }

    fun getString(key: String, defaultValue: String): String {
        return mBody.optString(key, defaultValue)
    }

    operator fun set(key: String, value: String?) {
        if (value == null) return
        try {
            mBody.put(key, value)
        } catch (ignored: Exception) {
        }
    }

    fun getInt(key: String): Int {
        return mBody.optInt(key, -1)
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return mBody.optInt(key, defaultValue)
    }

    operator fun set(key: String, value: Int) {
        try {
            mBody.put(key, value)
        } catch (ignored: Exception) {
        }
    }

    fun getLong(key: String): Long {
        return mBody.optLong(key, -1)
    }

    fun getLong(key: String, defaultValue: Long): Long {
        return mBody.optLong(key, defaultValue)
    }

    operator fun set(key: String, value: Long) {
        try {
            mBody.put(key, value)
        } catch (ignored: Exception) {
        }
    }

    fun getBoolean(key: String): Boolean {
        return mBody.optBoolean(key, false)
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return mBody.optBoolean(key, defaultValue)
    }

    operator fun set(key: String, value: Boolean) {
        try {
            mBody.put(key, value)
        } catch (ignored: Exception) {
        }
    }

    fun getDouble(key: String): Double {
        return mBody.optDouble(key, Double.NaN)
    }

    fun getDouble(key: String, defaultValue: Double): Double {
        return mBody.optDouble(key, defaultValue)
    }

    operator fun set(key: String, value: Double) {
        try {
            mBody.put(key, value)
        } catch (ignored: Exception) {
        }
    }

    fun getJSONArray(key: String): JSONArray? {
        return mBody.optJSONArray(key)
    }

    operator fun set(key: String, value: JSONArray?) {
        try {
            mBody.put(key, value)
        } catch (ignored: Exception) {
        }
    }

    fun getJSONObject(key: String): JSONObject? {
        return mBody.optJSONObject(key)
    }

    operator fun set(key: String, value: JSONObject?) {
        try {
            mBody.put(key, value)
        } catch (ignored: JSONException) {
        }
    }

    fun getStringSet(key: String): Set<String>? {
        val jsonArray = mBody.optJSONArray(key) ?: return null
        val list: MutableSet<String> = HashSet()
        val length = jsonArray.length()
        for (i in 0 until length) {
            try {
                val str = jsonArray.getString(i)
                list.add(str)
            } catch (ignored: Exception) {
            }
        }
        return list
    }

    fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? {
        return if (mBody.has(key)) getStringSet(key)
        else defaultValue
    }

    operator fun set(key: String, value: Set<String>) {
        try {
            val jsonArray = JSONArray()
            for (str in value) {
                jsonArray.put(str)
            }
            mBody.put(key, jsonArray)
        } catch (ignored: Exception) {
        }
    }

    fun getStringList(key: String): List<String>? {
        val jsonArray = mBody.optJSONArray(key) ?: return null
        val list: MutableList<String> = ArrayList()
        val length = jsonArray.length()
        for (i in 0 until length) {
            try {
                val str = jsonArray.getString(i)
                list.add(str)
            } catch (ignored: Exception) {
            }
        }
        return list
    }

    fun getStringList(key: String, defaultValue: List<String>?): List<String>? {
        return if (mBody.has(key)) getStringList(key)
        else defaultValue
    }

    operator fun set(key: String, value: List<String>) {
        try {
            val jsonArray = JSONArray()
            for (str in value) {
                jsonArray.put(str)
            }
            mBody.put(key, jsonArray)
        } catch (ignored: Exception) {
        }
    }

    fun has(key: String): Boolean {
        return mBody.has(key)
    }

    @Throws(JSONException::class)
    fun serialize(): String {
        val jo = JSONObject()
        jo.put("id", id)
        jo.put("type", type)
        jo.put("body", mBody)
        if (hasPayload()) {
            jo.put("payloadSize", payload!!.payloadSize)
            jo.put("payloadTransferInfo", payloadTransferInfo)
        }
        // QJSon does not escape slashes, but Java JSONObject does. Converting to QJson format.
        try {
            return jo.toString().replace("\\/", "/") + "\n"
        } catch (e : OutOfMemoryError) {
            throw RuntimeException("OOM serializing packet of type $type", e)
        }
    }

    val payloadSize: Long
        get() = payload?.payloadSize ?: 0

    fun hasPayload(): Boolean {
        val payload = payload
        return payload != null && payload.payloadSize != 0L
    }

    fun hasPayloadTransferInfo(): Boolean {
        return payloadTransferInfo.length() > 0
    }

    class Payload {
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

        fun close() {
            // TODO: If socket only close socket if that also closes the streams that is
            try {
                inputStream?.close()
            } catch (ignored: IOException) {
            }

            try {
                inputSocket?.close()
            } catch (ignored: IOException) {
            }
        }
    }

    companion object {
        const val PACKET_TYPE_IDENTITY: String = "kdeconnect.identity"
        const val PACKET_TYPE_PAIR: String = "kdeconnect.pair"

        @JvmStatic
        @Throws(JSONException::class)
        fun unserialize(s: String): NetworkPacket {
            val jo = JSONObject(s)
            val id = jo.getLong("id")
            val type = jo.getString("type")
            val mBody = jo.getJSONObject("body")

            val hasPayload = jo.has("payloadSize")
            val payloadTransferInfo = if (hasPayload) jo.getJSONObject("payloadTransferInfo") else JSONObject()
            val payload = if (hasPayload) Payload(jo.getLong("payloadSize")) else null
            return NetworkPacket(id, type, mBody, payload, payloadTransferInfo)
        }
    }
}
