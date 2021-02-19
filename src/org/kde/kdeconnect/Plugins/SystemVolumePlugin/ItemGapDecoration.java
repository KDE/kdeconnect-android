package org.kde.kdeconnect.Plugins.SystemVolumePlugin;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class ItemGapDecoration extends RecyclerView.ItemDecoration {

    private final int gap;

    ItemGapDecoration(int gap) {
        this.gap = gap;
    }

    @Override
    public void getItemOffsets(
            @NonNull Rect outRect,
            @NonNull View view,
            @NonNull RecyclerView parent,
            @NonNull RecyclerView.State state
    ) {
        super.getItemOffsets(outRect, view, parent, state);

        int itemPosition = parent.getChildAdapterPosition(view);
        RecyclerView.Adapter<?> adapter = parent.getAdapter();

        if (adapter == null) return;

        if (itemPosition >= 0 && itemPosition < adapter.getItemCount() - 1) {
            outRect.bottom = gap;
        }

    }
}
