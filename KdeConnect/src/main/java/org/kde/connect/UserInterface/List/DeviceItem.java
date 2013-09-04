package org.kde.connect.UserInterface.List;


import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.kde.connect.Device;
import org.kde.connect.UserInterface.DeviceActivity;
import org.kde.connect.UserInterface.PairActivity;
import org.kde.kdeconnect_tp.R;

public class DeviceItem implements ListAdapter.Item {

	private final Device device;
    private final Activity activity;

	public DeviceItem(Activity activity, Device device) {
		this.device = device;
        this.activity = activity;
	}

    @Override
    public View inflateView(LayoutInflater layoutInflater) {
        View v = layoutInflater.inflate(R.layout.list_item_entry, null);

        TextView titleView = (TextView)v.findViewById(R.id.list_item_entry_title);
        if (titleView != null) titleView.setText(device.getName());

        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent;
                if (device.isPaired()) {
                    intent = new Intent(activity, DeviceActivity.class);
                } else {
                    intent = new Intent(activity, PairActivity.class);
                }
                intent.putExtra("deviceId", device.getDeviceId());
                activity.startActivity(intent);
            }
        });

        return v;
    }

}
