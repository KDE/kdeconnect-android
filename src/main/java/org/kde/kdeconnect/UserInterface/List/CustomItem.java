package org.kde.kdeconnect.UserInterface.List;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

public class CustomItem implements ListAdapter.Item {

	private final View view;

	public CustomItem(View v) {
        this.view = v;
	}

    @Override
    public View inflateView(LayoutInflater layoutInflater) {
        return view;
    }

}
