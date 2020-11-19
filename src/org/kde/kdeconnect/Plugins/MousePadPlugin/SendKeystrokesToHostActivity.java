package org.kde.kdeconnect.Plugins.MousePadPlugin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPacket;
import org.kde.kdeconnect.UserInterface.List.EntryItem;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.List.SectionItem;
import org.kde.kdeconnect.UserInterface.ThemeUtil;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivitySendkeystrokesBinding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SendKeystrokesToHostActivity extends AppCompatActivity {
    private ActivitySendkeystrokesBinding binding;
    private boolean sendingAppIsTrusted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setUserPreferredTheme(this);

        binding = ActivitySendkeystrokesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1) // needed for this.getReferrer()
    @Override
    protected void onStart() {
        super.onStart();


        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean(getString(R.string.pref_sendkeystrokes_enabled), true)){
            Toast.makeText(getApplicationContext(), R.string.sendkeystrokes_disabled_toast,Toast.LENGTH_LONG).show();
            finish();
        } else {

            final Intent intent = getIntent();
            String type = intent.getType();
            if ("text/keystrokes".equals(type)) {
                String callingActivity = this.getReferrer().getHost();
                String toSend = intent.getStringExtra(Intent.EXTRA_TEXT);
                binding.textToSend.setText(toSend);
                binding.switchAlwaysTrustSender.setText(Html.fromHtml( // format as HTML to allow a part of the text to be bold
                        getResources().getString(R.string.sendkeystrokes_always_trust, Html.escapeHtml(callingActivity))
                        )
                );

                // subscribe to new connected devices
                BackgroundService.RunCommand(this, service -> {
                    service.onNetworkChange();
                    service.addDeviceListChangedCallback("SendKeystrokesToHostActivity", this::updateComputerList);
                });

                // list all currently connected devices
                updateComputerList();


                // set the "trusted app" switch, which allows sending keystrokes without confirmation
                // not sure, why this is coming from strings.xml - just keeping it with the existing code style
                String prefsTrustedAppsKey = getString(R.string.sendkeystrokes_pref_trusted_apps);
                binding.switchAlwaysTrustSender.setOnCheckedChangeListener((elem, isSet) -> {
                    // make a mutable copy of the set from the preferences
                    Set<String> trustedApps = new HashSet<>(prefs.getStringSet(prefsTrustedAppsKey, Collections.emptySet()));

                    if (isSet) {
                        trustedApps.add(callingActivity);
                    } else {
                        trustedApps.remove(callingActivity);
                    }
                    prefs.edit().putStringSet(prefsTrustedAppsKey, trustedApps).apply();
                    binding.alwaysTrustWarning.setVisibility(isSet ? View.VISIBLE : View.GONE);
                });

                sendingAppIsTrusted = prefs.getStringSet(prefsTrustedAppsKey, Collections.emptySet()).contains(callingActivity);
                binding.switchAlwaysTrustSender.setChecked(sendingAppIsTrusted);
            } else {
                Toast.makeText(getApplicationContext(), R.string.sendkeystrokes_wrong_data,Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void sendKeys(Device deviceId) {
        String toSend;
        if (binding.textToSend.getText() != null && (toSend = binding.textToSend.getText().toString().trim()).length() > 0) {
            final NetworkPacket np = new NetworkPacket(MousePadPlugin.PACKET_TYPE_MOUSEPAD_REQUEST);
            np.set("key", toSend);
            BackgroundService.RunWithPlugin(this, deviceId.getDeviceId(), MousePadPlugin.class, plugin -> plugin.sendKeyboardPacket(np));
            Toast.makeText(
                    getApplicationContext(),
                    getString(R.string.sendkeystrokes_sent_text,toSend, deviceId.getName()),
                    Toast.LENGTH_SHORT
            ).show();

        }
    }


    private void updateComputerList() {
        BackgroundService.RunCommand(this, service -> {

            Collection<Device> devices = service.getDevices().values();
            final ArrayList<Device> devicesList = new ArrayList<>();
            final ArrayList<ListAdapter.Item> items = new ArrayList<>();

            SectionItem section = new SectionItem(getString(R.string.sendkeystrokes_send_to));
            items.add(section);

            for (Device d : devices) {
                if (d.isReachable() && d.isPaired()) {
                    devicesList.add(d);
                    items.add(new EntryItem(d.getName()));
                    section.isEmpty = false;
                }
            }
            runOnUiThread(() -> {
                binding.devicesList.setAdapter(new ListAdapter(SendKeystrokesToHostActivity.this, items));
                binding.devicesList.setOnItemClickListener((adapterView, view, i, l) -> {
                    Device device = devicesList.get(i - 1); // NOTE: -1 because of the title!
                    sendKeys(device);
                    this.finish(); // close the activity
                });
            });

            // only one device is connected and we trust the sending app identifier -> send it and close the activity
            if (devicesList.size() == 1 && sendingAppIsTrusted) {
                Device device = devicesList.get(0);
                sendKeys(device);
                this.finish(); // close the activity
            }
        });
    }
}

