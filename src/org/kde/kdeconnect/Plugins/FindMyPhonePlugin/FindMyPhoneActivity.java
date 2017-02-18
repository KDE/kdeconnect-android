package org.kde.kdeconnect.Plugins.FindMyPhonePlugin;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import org.kde.kdeconnect_tp.R;

public class FindMyPhoneActivity extends Activity {
    Ringtone ringtone;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if(ringtone != null) {
            // If this activity was already open and we received the ring packet again, just finish it
            finish();
        }
        // otherwise the activity will become active again
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_my_phone);

        Window window = this.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        findViewById(R.id.bFindMyPhone).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        ringtone = RingtoneManager.getRingtone(getApplicationContext(), ringtoneUri);
        if (ringtone == null) {
            ringtoneUri = RingtoneManager.getValidRingtoneUri(getApplicationContext());
            if (ringtoneUri == null) {
                Log.e("FindMyPhone", "Could not find a ringtone to play!");
                return;
            }
            ringtone = RingtoneManager.getRingtone(getApplicationContext(), ringtoneUri);
        }

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            AudioAttributes.Builder b = new AudioAttributes.Builder();
            b.setUsage(AudioAttributes.USAGE_ALARM);
            ringtone.setAudioAttributes(b.build());
        } else {
            ringtone.setStreamType(AudioManager.STREAM_ALARM);
        }

        ringtone.play();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if(ringtone != null) {
            ringtone.stop();
            ringtone = null;
        }
    }

}
