package org.kde.kdeconnect.UserInterface.List;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

public class ButtonItem implements ListAdapter.Item {

	private final Button button;

	public ButtonItem(Button b) {
        this.button = b;
	}

    @Override
    public View inflateView(LayoutInflater layoutInflater) {
        return button;
    }

}
