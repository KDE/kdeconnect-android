package org.kde.kdeconnect.UserInterface.List;


import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.UserInterface.DeviceActivity;
import org.kde.kdeconnect.UserInterface.PairActivity;
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
        if (device.compareProtocolVersion() != 0) {
            TextView summaryView = (TextView)v.findViewById(R.id.list_item_entry_summary);
            summaryView.setVisibility(View.VISIBLE);
            if (device.compareProtocolVersion() > 0) {
                summaryView.setText(R.string.protocol_version_newer);
            } else {
                summaryView.setText(R.string.protocol_version_older);
            }
        } else {
            v.findViewById(R.id.list_item_entry_summary).setVisibility(View.GONE);
        }

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
