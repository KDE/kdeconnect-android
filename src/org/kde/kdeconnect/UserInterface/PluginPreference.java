package org.kde.kdeconnect.UserInterface;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Plugins.Plugin;
import org.kde.kdeconnect.Plugins.PluginFactory;
import org.kde.kdeconnect_tp.R;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreference;

public class PluginPreference extends SwitchPreference {
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

        View.OnClickListener toggleListener = v -> {
            boolean newState = !device.isPluginEnabled(pluginKey);
            setChecked(newState); //It actually works on API<14
            onStateChanged(holder, newState);
            device.setPluginEnabled(pluginKey, newState);
        };

        View content = holder.findViewById(R.id.content);
        View widget = holder.findViewById(android.R.id.widget_frame);
        View parent = holder.itemView;
        content.setOnClickListener(listener);
        widget.setOnClickListener(toggleListener);
        parent.setOnClickListener(toggleListener);

        // Disable child backgrounds when known to be unneeded to prevent duplicate ripples
        int selectableItemBackground;
        if (listener == null) {
            selectableItemBackground = 0;
        } else {
            TypedValue value = new TypedValue();
            getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
            selectableItemBackground = value.resourceId;
        }
        content.setBackgroundResource(selectableItemBackground);
        widget.setBackgroundResource(selectableItemBackground);

        onStateChanged(holder, isChecked());
    }

    private void onStateChanged(PreferenceViewHolder holder, boolean state) {
        View content = holder.findViewById(R.id.content);
        View divider = holder.findViewById(R.id.divider);
        View widget = holder.findViewById(android.R.id.widget_frame);
        View parent = holder.itemView;

        boolean hasDetails = state && listener != null;

        divider.setVisibility(hasDetails ? View.VISIBLE : View.GONE);
        content.setClickable(hasDetails);
        widget.setClickable(hasDetails);
        parent.setClickable(!hasDetails);

        if (hasDetails) {
            // Cancel duplicate ripple caused by pressed state of parent propagating down
            content.setPressed(false);
            content.getBackground().jumpToCurrentState();
            widget.setPressed(false);
            widget.getBackground().jumpToCurrentState();
        }
    }

    interface PluginPreferenceCallback {
        void onStartPluginSettingsFragment(Plugin plugin);
        void onFinish();
    }
}
