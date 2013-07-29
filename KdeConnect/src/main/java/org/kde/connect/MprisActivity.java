package org.kde.connect;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.kde.connect.PackageInterfaces.MprisControlPackageInterface;
import org.kde.kdeconnect.R;

public class MprisActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mpris_control);

        findViewById(R.id.play_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(MprisActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        MprisControlPackageInterface mpris = (MprisControlPackageInterface)service.getPackageInterface(MprisControlPackageInterface.class);
                        mpris.sendAction("PlayPause");
                    }
                });
            }
        });

        findViewById(R.id.prev_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(MprisActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        MprisControlPackageInterface mpris = (MprisControlPackageInterface)service.getPackageInterface(MprisControlPackageInterface.class);
                        mpris.sendAction("Previous");
                    }
                });
            }
        });

        findViewById(R.id.next_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BackgroundService.RunCommand(MprisActivity.this, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        MprisControlPackageInterface mpris = (MprisControlPackageInterface)service.getPackageInterface(MprisControlPackageInterface.class);
                        mpris.sendAction("Next");
                    }
                });
            }
        });
    }

}
