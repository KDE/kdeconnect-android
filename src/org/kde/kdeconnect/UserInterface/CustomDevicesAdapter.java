/*
 * Copyright 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kde.kdeconnect.UserInterface;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.ButterKnife;

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
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.custom_device_item, parent, false);

        return new ViewHolder(v);
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
        @BindView(R.id.deviceNameOrIPBackdrop) TextView deviceNameOrIPBackdrop;
        @BindView(R.id.swipeableView) FrameLayout swipeableView;
        @BindView(R.id.deviceNameOrIP) TextView deviceNameOrIP;

        ViewHolder(@NonNull View itemView) {
            super(itemView);

            ButterKnife.bind(this, itemView);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                Drawable deleteDrawable = AppCompatResources.getDrawable(itemView.getContext(), R.drawable.ic_delete);
                deviceNameOrIPBackdrop.setCompoundDrawablesWithIntrinsicBounds(deleteDrawable, null, deleteDrawable, null);
            }

            deviceNameOrIP.setOnClickListener(v -> callback.onCustomDeviceClicked(customDevices.get(getAdapterPosition())));
        }

        void bind(String customDevice) {
            deviceNameOrIP.setText(customDevice);
        }

        @Override
        public View getSwipeableView() {
            return swipeableView;
        }
    }

    private interface SwipeableViewHolder {
        View getSwipeableView();
    }

    private static class ItemTouchHelperCallback extends ItemTouchHelper.Callback {
        @NonNull private Callback callback;

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
