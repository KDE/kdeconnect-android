/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SystemVolumePlugin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.kde.kdeconnect.Helpers.VolumeHelperKt;
import org.kde.kdeconnect.KdeConnect;
import org.kde.kdeconnect.Plugins.MprisPlugin.MprisPlugin;
import org.kde.kdeconnect.Plugins.MprisPlugin.VolumeKeyListener;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ListItemSystemvolumeBinding;
import org.kde.kdeconnect_tp.databinding.SystemVolumeFragmentBinding;

import java.util.ArrayList;
import java.util.List;

public class SystemVolumeFragment
        extends Fragment
        implements Sink.UpdateListener, SystemVolumePlugin.SinkListener, VolumeKeyListener {

    private SystemVolumePlugin plugin;
    private RecyclerSinkAdapter recyclerAdapter;
    private boolean tracking;
    private final Consumer<Boolean> trackingConsumer = aBoolean -> tracking = aBoolean;
    private SystemVolumeFragmentBinding systemVolumeFragmentBinding;

    public static SystemVolumeFragment newInstance(String deviceId) {
        SystemVolumeFragment systemvolumeFragment = new SystemVolumeFragment();

        Bundle arguments = new Bundle();
        arguments.putString(MprisPlugin.DEVICE_ID_KEY, deviceId);

        systemvolumeFragment.setArguments(arguments);

        return systemvolumeFragment;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        if (systemVolumeFragmentBinding == null) {
            systemVolumeFragmentBinding = SystemVolumeFragmentBinding.inflate(inflater);

            RecyclerView recyclerView = systemVolumeFragmentBinding.audioDevicesRecycler;

            int gap = requireContext().getResources().getDimensionPixelSize(R.dimen.activity_vertical_margin);
            recyclerView.addItemDecoration(new ItemGapDecoration(gap));
            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

            recyclerAdapter = new RecyclerSinkAdapter();
            recyclerView.setAdapter(recyclerAdapter);
        }

        connectToPlugin(getDeviceId());

        return systemVolumeFragmentBinding.getRoot();
    }

    @Override
    public void onDestroyView() {
        disconnectFromPlugin(getDeviceId());
        super.onDestroyView();
    }

    @Override
    public void updateSink(@NonNull final Sink sink) {

        // Don't set progress while the slider is moved
        if (!tracking) {

            requireActivity().runOnUiThread(() -> recyclerAdapter.notifyDataSetChanged());
        }
    }

    private void connectToPlugin(final String deviceId) {
        SystemVolumePlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, SystemVolumePlugin.class);
        if (plugin == null) {
            return;
        }
        this.plugin = plugin;
        plugin.addSinkListener(SystemVolumeFragment.this);
        plugin.requestSinkList();
    }

    private void disconnectFromPlugin(final String deviceId) {
        SystemVolumePlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, SystemVolumePlugin.class);
        if (plugin == null) {
            return;
        }
        plugin.removeSinkListener(SystemVolumeFragment.this);
    }

    @Override
    public void sinksChanged() {

        for (Sink sink : plugin.getSinks()) {
            sink.addListener(SystemVolumeFragment.this);
        }

        requireActivity().runOnUiThread(() -> {
            List<Sink> newSinks = new ArrayList<>(plugin.getSinks());
            recyclerAdapter.submitList(newSinks);
        });
    }

    @Override
    public void onVolumeUp() {
        updateDefaultSinkVolume(5);
    }

    @Override
    public void onVolumeDown() {
        updateDefaultSinkVolume(-5);
    }

    private void updateDefaultSinkVolume(int percent) {
        if (plugin == null) return;

        Sink defaultSink = SystemVolumeUtilsKt.getDefaultSink(plugin);
        if (defaultSink == null) return;

        int newVolume = VolumeHelperKt.calculateNewVolume(
                defaultSink.getVolume(),
                defaultSink.getMaxVolume(),
                percent
        );

        if (defaultSink.getVolume() == newVolume) return;

        plugin.sendVolume(defaultSink.getName(), newVolume);
    }

    private String getDeviceId() {
        return requireArguments().getString(MprisPlugin.DEVICE_ID_KEY);
    }

    private class RecyclerSinkAdapter extends ListAdapter<Sink, SinkItemHolder> {

        public RecyclerSinkAdapter() {
            super(new SinkItemCallback());
        }

        @NonNull
        @Override
        public SinkItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

            LayoutInflater inflater = getLayoutInflater();
            ListItemSystemvolumeBinding viewBinding = ListItemSystemvolumeBinding.inflate(inflater, parent, false);

            return new SinkItemHolder(viewBinding, plugin, trackingConsumer);
        }

        @Override
        public void onBindViewHolder(@NonNull SinkItemHolder holder, int position) {
            holder.bind(getItem(position));
        }
    }
}
