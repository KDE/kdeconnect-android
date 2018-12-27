package org.kde.kdeconnect.Plugins.RunCommandPlugin;

import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.widget.ListView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Collections;

import androidx.appcompat.app.AppCompatActivity;

public class RunCommandWidgetDeviceSelector extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.widget_remotecommandplugin_dialog);

        BackgroundService.RunCommand(this, service -> runOnUiThread(() -> {
            ListView view = findViewById(R.id.runcommandsdevicelist);

            final ArrayList<ListAdapter.Item> deviceItems = new ArrayList<>();

            for (Device device : service.getDevices().values()) {
                if (device.isPaired() && device.isReachable()) {
                    deviceItems.add(
                            new CommandEntry(
                                    device.getName(),
                                    null,
                                    device.getDeviceId()
                            )
                    );
                }
            }

            Collections.sort(deviceItems, (lhs, rhs) -> ((CommandEntry) lhs).getName().compareTo(((CommandEntry) rhs).getName()));

            ListAdapter adapter = new ListAdapter(RunCommandWidgetDeviceSelector.this, deviceItems);

            view.setAdapter(adapter);
            view.setOnItemClickListener((adapterView, viewContent, i, l) -> {
                CommandEntry entry = (CommandEntry) deviceItems.get(i);
                RunCommandWidget.setCurrentDevice(entry.getKey());

                Intent updateWidget = new Intent(RunCommandWidgetDeviceSelector.this, RunCommandWidget.class);
                RunCommandWidgetDeviceSelector.this.sendBroadcast(updateWidget);

                finish();
            });
        }));
    }
}