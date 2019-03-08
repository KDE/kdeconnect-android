package org.kde.kdeconnect.UserInterface;

import android.content.Context;
import android.view.View;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

import androidx.annotation.NonNull;
import androidx.preference.CheckBoxPreference;
import androidx.preference.PreferenceViewHolder;

public class PluginPreference extends CheckBoxPreference {
    private final Device device;
    private final String pluginKey;
    private final View.OnClickListener listener;

    public PluginPreference(@NonNull final Context context, @NonNull final String pluginKey,
                            @NonNull final Device device, @NonNull PluginPreferenceCallback callback) {
        super(context);

        setLayoutResource(R.layout.preference_with_button/*R.layout.preference_with_button_androidx*/);

        this.device = device;
        this.pluginKey = pluginKey;

        PluginFactory.PluginInfo info = PluginFactory.getPluginInfo(pluginKey);
        setTitle(info.getDisplayName());
        setSummary(info.getDescription());
setIcon(android.R.color.transparent);
        setChecked(device.isPluginEnabled(pluginKey));

        Plugin plugin = device.getPlugin(pluginKey);
        if (info.hasSettings() && plugin != null) {
            this.listener = v -> {
                Plugin plugin1 = device.getPlugin(pluginKey);
                if (plugin1 != null) {
                    callback.onStartPluginSettingsFragment(plugin1);
                } else { //Could happen if the device is not connected anymore
                    callback.onFinish();
                }
            };
        } else {
            this.listener = null;
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        final View button = holder.findViewById(R.id.settingsButton);

        if (listener == null) {
            button.setVisibility(View.GONE);
        } else {
            button.setEnabled(isChecked());
            button.setVisibility(View.VISIBLE);
            button.setOnClickListener(listener);
        }

        holder.itemView.setOnClickListener(v -> {
            boolean newState = !device.isPluginEnabled(pluginKey);
            setChecked(newState); //It actually works on API<14
            button.setEnabled(newState);
            device.setPluginEnabled(pluginKey, newState);
        });
    }

    interface PluginPreferenceCallback {
        void onStartPluginSettingsFragment(Plugin plugin);
        void onFinish();
    }
}
