package org.kde.connect;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;

public class MainActivity extends Activity {

    private BackgroundService service = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.button1).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("MainActivity", "Button1");

                Intent serviceIntent = new Intent(MainActivity.this, BackgroundService.class);
                startService(serviceIntent);
                bindService(serviceIntent, new ServiceConnection() {

                    public void onServiceDisconnected(ComponentName name) {
                        service = null;
                    }

                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        service = ((BackgroundService.LocalBinder)binder).getInstance();
                    }
                },BIND_AUTO_CREATE);

            }
        });

        findViewById(R.id.button2).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("MainActivity","Button2");
                if (service != null) service.sendPing();
            }
        });


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

}
