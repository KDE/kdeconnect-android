/*
 * SPDX-FileCopyrightText: 2021 SohnyBohny <sohny.bean@streber24.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.MouseReceiverPlugin;

import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.fragment.app.DialogFragment;

import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect.UserInterface.MainActivity;
import org.kde.kdeconnect.UserInterface.StartActivityAlertDialogFragment;
import org.kde.kdeconnect_tp.R;

@PluginFactory.LoadablePlugin
@RequiresApi(api = Build.VERSION_CODES.N)
public class MouseReceiverPlugin extends Plugin {
    private final static String PACKET_TYPE_MOUSEPAD_REQUEST = "kdeconnect.mousepad.request";

    @Override
    public boolean onCreate() {
        Log.e("MouseReceiverPlugin", "onCreate()");
        return super.onCreate();
    }

    @Override
    public boolean checkRequiredPermissions() {
        return MouseReceiverService.instance != null;
    }

    @Override
    public DialogFragment getPermissionExplanationDialog() {
        return new StartActivityAlertDialogFragment.Builder()
                .setTitle(R.string.mouse_receiver_plugin_description)
                .setMessage(R.string.mouse_receiver_no_permissions)
                .setPositiveButton(R.string.open_settings)
                .setNegativeButton(R.string.cancel)
                .setIntentAction(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .setStartForResult(true)
                .setRequestCode(MainActivity.RESULT_NEEDS_RELOAD)
                .create();
    }

    @Override
    public void onDestroy() {
        Log.e("MouseReceiverPlugin", "onDestroy()");
        super.onDestroy();
    }

    @Override
    public boolean onPacketReceived(NetworkPacket np) {
        if (!np.getType().equals(PACKET_TYPE_MOUSEPAD_REQUEST)) {
            Log.e("MouseReceiverPlugin", "cannot receive packets of type: " + np.getType());
            return false;
        }

        double dx = np.getDouble("dx", 0);
        double dy = np.getDouble("dy", 0);

        boolean isSingleClick = np.getBoolean("singleclick", false);
        boolean isDoubleClick = np.getBoolean("doubleclick", false);
        boolean isMiddleClick = np.getBoolean("middleclick", false);
        boolean isForwardClick = np.getBoolean("forwardclick", false);
        boolean isBackClick = np.getBoolean("backclick", false);

        boolean isRightClick  = np.getBoolean("rightclick", false);
        boolean isSingleHold  = np.getBoolean("singlehold", false);
        boolean isSingleRelease  = np.getBoolean("singlerelease", false);
        boolean isScroll = np.getBoolean("scroll", false);

        if (isSingleClick || isDoubleClick || isMiddleClick || isRightClick || isSingleHold || isSingleRelease || isScroll || isForwardClick || isBackClick) {
            // Perform click
            if (isSingleClick) {
                // Log.i("MouseReceiverPlugin", "singleClick");
                return MouseReceiverService.click();
            } else if (isDoubleClick) { // left & right
                // Log.i("MouseReceiverPlugin", "doubleClick");
                return MouseReceiverService.recentButton();
            } else if (isMiddleClick) {
                // Log.i("MouseReceiverPlugin", "middleClick");
                return MouseReceiverService.homeButton();
            } else if (isRightClick) {
                // TODO right-click menu emulation
                return MouseReceiverService.backButton();
            } else if (isForwardClick) {
                return MouseReceiverService.recentButton();
            } else if (isBackClick) {
                return MouseReceiverService.backButton();
            } else if (isSingleHold){
                // For drag'n drop
                // Log.i("MouseReceiverPlugin", "singleHold");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    return MouseReceiverService.longClickSwipe();
                } else {
                    return MouseReceiverService.longClick();
                }
            } else if (isSingleRelease) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    return MouseReceiverService.instance.stopSwipe();
                }
            } else if (isScroll) {
                // Log.i("MouseReceiverPlugin", "scroll dx: " + dx + " dy: " + dy);
                return MouseReceiverService.scroll(dx, dy); // dx is always 0
            }

        } else {
            // Mouse Move
            if (dx != 0 || dy != 0) {
                // Log.i("MouseReceiverPlugin", "move Mouse dx: " + dx + " dy: " + dy);
                return MouseReceiverService.move(dx, dy);
            }
        }

        return super.onPacketReceived(np);
    }

    @Override
    public int getMinSdk() {
        return Build.VERSION_CODES.N;
    }

    @Override
    public String getDisplayName() {
        return context.getString(R.string.mouse_receiver_plugin_name);
    }

    @Override
    public String getDescription() {
        return context.getString(R.string.mouse_receiver_plugin_description);
    }

    @Override
    public String[] getSupportedPacketTypes() {
        return new String[]{PACKET_TYPE_MOUSEPAD_REQUEST};
    }

    @Override
    public String[] getOutgoingPacketTypes() {
        return new String[0];
    }
}
