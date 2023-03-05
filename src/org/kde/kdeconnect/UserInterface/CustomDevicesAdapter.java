/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface;

import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.kde.kdeconnect_tp.databinding.CustomDeviceItemBinding;

import java.util.ArrayList;

public class CustomDevicesAdapter extends RecyclerView.Adapter<CustomDevicesAdapter.ViewHolder> {
    private ArrayList<String> customDevices;
    private final Callback callback;

    CustomDevicesAdapter(@NonNull Callback callback) {
        this.callback = callback;

        customDevices = new ArrayList<>();
    }

    void setCustomDevices(ArrayList<String> customDevices) {
        this.customDevices = customDevices;

        notifyDataSetChanged();
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(
                new ItemTouchHelperCallback(adapterPos -> callback.onCustomDeviceDismissed(customDevices.get(adapterPos))));
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        CustomDeviceItemBinding itemBinding =
                CustomDeviceItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);

        return new ViewHolder(itemBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(customDevices.get(position));
    }

    @Override
    public int getItemCount() {
        return customDevices.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder implements SwipeableViewHolder {
        private final CustomDeviceItemBinding itemBinding;

        ViewHolder(@NonNull CustomDeviceItemBinding itemBinding) {
            super(itemBinding.getRoot());
            this.itemBinding = itemBinding;
            itemBinding.deviceNameOrIP.setOnClickListener(v -> callback.onCustomDeviceClicked(customDevices.get(getAdapterPosition())));
        }

        void bind(String customDevice) {
            itemBinding.deviceNameOrIP.setText(customDevice);
        }

        @Override
        public View getSwipeableView() {
            return itemBinding.swipeableView;
        }
    }

    private interface SwipeableViewHolder {
        View getSwipeableView();
    }

    private static class ItemTouchHelperCallback extends ItemTouchHelper.Callback {
        @NonNull private final Callback callback;

        private ItemTouchHelperCallback(@NonNull Callback callback) {
            this.callback = callback;
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            return makeMovementFlags(0, ItemTouchHelper.START | ItemTouchHelper.END);
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            getDefaultUIUtil().clearView(((SwipeableViewHolder)viewHolder).getSwipeableView());
        }

        @Override
        public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
            super.onSelectedChanged(viewHolder, actionState);

            if (viewHolder != null) {
                getDefaultUIUtil().onSelected(((SwipeableViewHolder) viewHolder).getSwipeableView());
            }
        }

        @Override
        public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
            getDefaultUIUtil().onDraw(c, recyclerView, ((SwipeableViewHolder)viewHolder).getSwipeableView(), dX, dY, actionState, isCurrentlyActive);
        }

        @Override
        public void onChildDrawOver(@NonNull Canvas c, @NonNull RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
            getDefaultUIUtil().onDrawOver(c, recyclerView, ((SwipeableViewHolder)viewHolder).getSwipeableView(), dX, dY, actionState, isCurrentlyActive);
        }

        @Override
        public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
            return 0.75f;
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            callback.onItemDismissed(viewHolder.getAdapterPosition());
        }

        private interface Callback {
            void onItemDismissed(int adapterPosition);
        }
    }

    public interface Callback {
        void onCustomDeviceClicked(String customDevice);
        void onCustomDeviceDismissed(String customDevice);
    }
}
