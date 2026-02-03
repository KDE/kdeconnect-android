/*
 * SPDX-FileCopyrightText: 2025 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.plugins.connectivityreport

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Registers a listener for changes in connectivity for the device.
 */
@SuppressLint("MissingPermission")
class ConnectivityListener(context: Context) {

    val context : Context = context.applicationContext

    data class SubscriptionState(var signalStrength: Int = 0, var networkType: String = "Unknown") {
        @RequiresApi(Build.VERSION_CODES.P)
        constructor(tm: TelephonyManager) : this(ASUUtils.signalStrengthToLevel(tm.signalStrength), ASUUtils.networkTypeToString(tm.dataNetworkType))
    }

    interface StateCallback {
        fun statesChanged(states: Map<Int, SubscriptionState>)
    }

    companion object {
        private const val TAG: String = "ConnectivityListener"
        private var instance: ConnectivityListener? = null
        @JvmStatic
        fun getInstance(context: Context): ConnectivityListener {
            if (instance == null) {
                instance = ConnectivityListener(context)
            }
            return instance!!
        }
    }

    private val connectivityListeners = mutableMapOf<Int?, PhoneStateListener?>()
    private val states = mutableMapOf<Int, SubscriptionState>() // by subscription ID

    private val externalListeners = mutableSetOf<StateCallback>()

    private val activeIDs = mutableSetOf<Int>()

    private fun statesChanged() {
        val listenersCopy = synchronized(externalListeners) {
            externalListeners.toList() // copy to prevent ConcurrentModificationException
        }
        for (listener in listenersCopy) {
            listener.statesChanged(states)
        }
    }

    val subscriptionsListener: OnSubscriptionsChangedListener by lazy {
        @RequiresApi(Build.VERSION_CODES.N)
        object : OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() {
                val nextSubs = getActiveSubscriptionIDs().toSet()

                val addedSubs = nextSubs - activeIDs
                val removedSubs = activeIDs - nextSubs

                activeIDs.removeAll(removedSubs)
                activeIDs.addAll(addedSubs)

                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                for (subID in removedSubs) {
                    Log.i(TAG, "Removed subscription ID $subID")
                    try {
                        tm.listen(connectivityListeners[subID], PhoneStateListener.LISTEN_NONE)
                    } catch (_: Exception) {
                        // It seems like the subscription ID is no longer valid by this point, so this might trigger
                    }
                    connectivityListeners.remove(subID)
                    states.remove(subID)
                    statesChanged()
                }
                for (subID in addedSubs) {
                    val subTm = tm.createForSubscriptionId(subID)
                    Log.i(TAG, "Added subscription ID $subID")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        states[subID] = SubscriptionState(subTm)
                    } else {
                        states[subID] = SubscriptionState()
                    }
                    val listener = createListenerForSubscription(subID)
                    connectivityListeners[subID] = listener
                    subTm.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or PhoneStateListener.LISTEN_DATA_CONNECTION_STATE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        statesChanged()
                    }
                }
            }
        }
    }

    fun listenStateChanges(listener: StateCallback) {
        var wasEmpty : Boolean
        synchronized(externalListeners) {
            wasEmpty = externalListeners.isEmpty()
            externalListeners.add(listener)
            listener.statesChanged(states)
        }
        Log.d(TAG, "listeners: ${externalListeners.size}")
        if (wasEmpty) {
            startListening()
        }
    }

    fun cancelActiveListener(listener: StateCallback) {
        var isEmpty : Boolean
        synchronized(externalListeners) {
            externalListeners.remove(listener)
            isEmpty = externalListeners.isEmpty()
        }
        if (isEmpty) {
            stopListening()
        }
    }

    private fun startListening() {
        runOnMainThread {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Multi-SIM supported on Nougat+
                val sm = ContextCompat.getSystemService(context, SubscriptionManager::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    sm?.addOnSubscriptionsChangedListener(context.mainExecutor, subscriptionsListener)
                } else {
                    sm?.addOnSubscriptionsChangedListener(subscriptionsListener)
                }
            } else {
                // Fallback to single SIM
                connectivityListeners.put(0, createListenerForSubscription(0))
                states.put(0, SubscriptionState())
            }
        }
    }

    private fun stopListening() {
        runOnMainThread {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val sm = ContextCompat.getSystemService(context, SubscriptionManager::class.java)
                sm?.removeOnSubscriptionsChangedListener(subscriptionsListener)
            }
            for (subID in connectivityListeners.keys) {
                Log.i(TAG, "Removed subscription ID $subID")
                tm.listen(connectivityListeners[subID], PhoneStateListener.LISTEN_NONE)
            }
            connectivityListeners.clear()
            states.clear()
            activeIDs.clear()
        }
    }

    private fun runOnMainThread(r: Runnable) {
        Handler(Looper.getMainLooper()).post(r)
    }

    private fun createListenerForSubscription(subID: Int): PhoneStateListener {
        return object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                val state = states[subID]
                if (state != null) {
                    val newStrength = ASUUtils.signalStrengthToLevel(signalStrength)
                    if (newStrength != state.signalStrength) {
                        state.signalStrength = newStrength
                        statesChanged()
                    }
                }
            }

            override fun onDataConnectionStateChanged(ignore: Int, networkType: Int) {
                val state = states[subID]
                if (state != null) {
                    val newNetworkType = ASUUtils.networkTypeToString(networkType)
                    if (newNetworkType != state.networkType) {
                        state.networkType = newNetworkType
                        statesChanged()
                    }
                }
            }
        }
    }

    /**
     * Get all subscriptionIDs (SIM cards) of the device
     */
    @Throws(SecurityException::class)
    fun getActiveSubscriptionIDs(): List<Int> {
        val subscriptionManager = ContextCompat.getSystemService(context, SubscriptionManager::class.java)
        if (subscriptionManager == null) {
            Log.w(TAG, "Could not get SubscriptionManager")
            return emptyList()
        }
        val subscriptionInfos = subscriptionManager.activeSubscriptionInfoList
        if (subscriptionInfos == null) {
            // This happens when there is no SIM card inserted
            Log.w(TAG, "Could not get SubscriptionInfos")
            return emptyList()
        }
        return subscriptionInfos.map { it.subscriptionId }
    }

}
