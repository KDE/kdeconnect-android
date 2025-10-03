package org.kde.kdeconnect.Plugins.ConnectivityReportPlugin

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener
import android.util.Log
import androidx.core.content.ContextCompat
import org.kde.kdeconnect.Helpers.TelephonyHelper


/**
 * Registers a listener for changes in subscriptionIDs for the device.
 * This lets you identify additions/removals of SIM cards.
 */
class TelephonySubscriptionsListener(context: Context) {

    interface SubscriptionCallback {
        fun onAdd(subscriptionID: Int)
        fun onRemove(subscriptionID: Int)
    }

    val context : Context = context.applicationContext

    companion object {
        private var instance: TelephonySubscriptionsListener? = null
        @JvmStatic
        fun getInstance(context: Context): TelephonySubscriptionsListener {
            if (instance == null) {
                instance = TelephonySubscriptionsListener(context)
            }
            return instance!!
        }
    }

    val listeners = mutableSetOf<SubscriptionCallback>()

    val activeIDs = mutableSetOf<Int>()

    val systemListener: OnSubscriptionsChangedListener = object : OnSubscriptionsChangedListener() {
        override fun onSubscriptionsChanged() {
            val nextSubs = getActiveSubscriptionIDs().toSet()

            val addedSubs = nextSubs - activeIDs
            val removedSubs = activeIDs - nextSubs

            activeIDs.removeAll(removedSubs)
            activeIDs.addAll(addedSubs)

            listeners.forEach { listener ->
                removedSubs.forEach(listener::onRemove)
                addedSubs.forEach(listener::onAdd)
            }
        }
    }

    fun listenActiveSubscriptionIDs(listener: SubscriptionCallback) {
        var wasEmpty : Boolean
        synchronized(listeners) {
            wasEmpty = listeners.isEmpty()
            listeners.add(listener)
        }
        Log.d("TelephonySubscriptionsListener", "listeners: ${listeners.size}")
        if (wasEmpty) {
            startListening()
        }
    }

    fun cancelActiveSubscriptionIDsListener(listener: SubscriptionCallback) {
        var isEmpty : Boolean
        synchronized(listeners) {
            listeners.remove(listener)
            isEmpty = listeners.isEmpty()
        }
        if (isEmpty) {
            stopListening()
        }
    }

    private fun startListening() {
        val sm = ContextCompat.getSystemService(context, SubscriptionManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sm?.addOnSubscriptionsChangedListener(context.mainExecutor, systemListener)
        } else {
            sm?.addOnSubscriptionsChangedListener(systemListener)
        }
    }

    private fun stopListening() {
        val sm = ContextCompat.getSystemService(context, SubscriptionManager::class.java)
        sm?.removeOnSubscriptionsChangedListener(systemListener)
    }

    /**
     * Get all subscriptionIDs of the device
     * As far as I can tell, this is essentially a way of identifying particular SIM cards
     */
    @Throws(SecurityException::class)
    fun getActiveSubscriptionIDs(): List<Int> {
        val subscriptionManager = ContextCompat.getSystemService(context, SubscriptionManager::class.java)
        if (subscriptionManager == null) {
            Log.w(TelephonyHelper.LOGGING_TAG, "Could not get SubscriptionManager")
            return emptyList()
        }
        val subscriptionInfos = subscriptionManager.activeSubscriptionInfoList
        if (subscriptionInfos == null) {
            // This happens when there is no SIM card inserted
            Log.w(TelephonyHelper.LOGGING_TAG, "Could not get SubscriptionInfos")
            return emptyList()
        }
        return subscriptionInfos.map { it.subscriptionId }
    }
}
