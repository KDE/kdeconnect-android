package org.kde.kdeconnect.UserInterface.List;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.kde.kdeconnect_tp.R;

public class SmallEntryItem implements ListAdapter.Item {

	private final String title;
    private final View.OnClickListener clickListener;

	public SmallEntryItem(String title) {
		this.title = title;
        this.clickListener = null;
	}

    public SmallEntryItem(String title, View.OnClickListener clickListener) {
        this.title = title;
        this.clickListener = clickListener;
    }

    @Override
    public View inflateView(LayoutInflater layoutInflater) {
        View v = layoutInflater.inflate(android.R.layout.simple_list_item_1, null);

        TextView titleView = (TextView)v.findViewById(android.R.id.text1);
        if (titleView != null) titleView.setText(title);
        if (clickListener != null) {
            titleView.setOnClickListener(clickListener);
            v.setBackgroundDrawable(layoutInflater.getContext().getResources().getDrawable(R.drawable.kitkatcompatselector_list_selector_holo_dark));

        }

        return v;
    }

}
