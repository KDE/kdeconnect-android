package org.kde.kdeconnect.UserInterface.List;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import android.R;

public class TextItem implements ListAdapter.Item {

	private final String title;

	public TextItem(String title) {
        this.title = title;
    }

    @Override
    public View inflateView(LayoutInflater layoutInflater) {

        TextView v = new TextView(layoutInflater.getContext());
        v.setText(title);
        v.setTextAppearance(layoutInflater.getContext(), R.style.TextAppearance_DeviceDefault_Medium);
        return v;

    }

}
