/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SystemvolumePlugin;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect_tp.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.ListFragment;

public class SystemvolumeFragment extends ListFragment implements Sink.UpdateListener, SystemVolumePlugin.SinkListener {

    private SystemVolumePlugin plugin;
    private Activity activity;
    private SinkAdapter adapter;
    private Context context;
    private boolean tracking;

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getListView().setDivider(null);
        setListAdapter(new SinkAdapter(getContext(), new Sink[0]));
    }

    @Override
    public void updateSink(final Sink sink) {

        // Don't set progress while the slider is moved
        if (!tracking) {

            activity.runOnUiThread(() -> adapter.notifyDataSetChanged());
        }
    }

    public void connectToPlugin(final String deviceId) {
        BackgroundService.RunWithPlugin(activity, deviceId, SystemVolumePlugin.class, plugin -> {
            this.plugin = plugin;
            plugin.addSinkListener(SystemvolumeFragment.this);
            plugin.requestSinkList();
        });
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        activity = getActivity();
        this.context = context;
    }

    @Override
    public void sinksChanged() {

        for (Sink sink : plugin.getSinks()) {
            sink.addListener(SystemvolumeFragment.this);
        }

        activity.runOnUiThread(() -> {
            adapter = new SinkAdapter(context, plugin.getSinks().toArray(new Sink[0]));
            setListAdapter(adapter);
        });
    }

    private class SinkAdapter extends ArrayAdapter<Sink> {

        private SinkAdapter(@NonNull Context context, @NonNull Sink[] objects) {
            super(context, R.layout.list_item_systemvolume, objects);
        }

        @NonNull
        @Override
        public View getView(final int position, @Nullable View convertView, @NonNull ViewGroup parent) {

            View view = getLayoutInflater().inflate(R.layout.list_item_systemvolume, parent, false);

            UIListener listener = new UIListener(getItem(position));

            ((TextView) view.findViewById(R.id.systemvolume_label)).setText(getItem(position).getDescription());

            final SeekBar seekBar = view.findViewById(R.id.systemvolume_seek);
            seekBar.setMax(getItem(position).getMaxVolume());
            seekBar.setProgress(getItem(position).getVolume());
            seekBar.setOnSeekBarChangeListener(listener);

            ImageButton button = view.findViewById(R.id.systemvolume_mute);
            int iconRes = getItem(position).isMute() ? R.drawable.ic_volume_mute_black : R.drawable.ic_volume_black;
            button.setImageResource(iconRes);
            button.setOnClickListener(listener);

            return view;
        }

    }

    private class UIListener implements SeekBar.OnSeekBarChangeListener, ImageButton.OnClickListener {

        private final Sink sink;

        private UIListener(Sink sink) {
            this.sink = sink;
        }

        @Override
        public void onProgressChanged(final SeekBar seekBar, int i, boolean b) {
            BackgroundService.RunCommand(activity, service -> plugin.sendVolume(sink.getName(), seekBar.getProgress()));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            tracking = true;
        }

        @Override
        public void onStopTrackingTouch(final SeekBar seekBar) {
            tracking = false;
            BackgroundService.RunCommand(activity, service -> plugin.sendVolume(sink.getName(), seekBar.getProgress()));
        }

        @Override
        public void onClick(View view) {
            plugin.sendMute(sink.getName(), !sink.isMute());
        }
    }
}
