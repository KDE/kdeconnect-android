package org.kde.kdeconnect.UserInterface;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import java.util.ArrayList;
import java.util.List;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Helpers.TrustedNetworkHelper;
import org.kde.kdeconnect_tp.R;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class TrustedNetworksActivity extends AppCompatActivity {

    private ListView trustedNetworksView;
    private List<String> trustedNetworks;

    private boolean dialogAlreadyShown = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        TrustedNetworkHelper trustedNetworkHelper = new TrustedNetworkHelper(getApplicationContext());
        super.onCreate(savedInstanceState);
        trustedNetworks = new ArrayList<>(trustedNetworkHelper.read());
        ThemeUtil.setUserPreferredTheme(this);
        setContentView(R.layout.trusted_network_list);
        trustedNetworksView = findViewById(android.R.id.list);
        emptyListMessage(trustedNetworkHelper);

        trustedNetworkListView(trustedNetworkHelper);

        CheckBox allowAllCheckBox = findViewById(R.id.trust_all_networks_checkBox);
        allowAllCheckBox.setChecked(trustedNetworkHelper.allAllowed());
        allowAllCheckBox.setOnCheckedChangeListener((v, isChecked) -> {
            trustedNetworkHelper.allAllowed(isChecked);
            trustedNetworkListView(trustedNetworkHelper);
        });

    }

    private void emptyListMessage(TrustedNetworkHelper trustedNetworkHelper) {
        boolean isVisible = trustedNetworks.isEmpty() && !trustedNetworkHelper.allAllowed();
        findViewById(R.id.trusted_network_list_empty)
                .setVisibility(isVisible ? VISIBLE : GONE );
    }

    private void trustedNetworkListView(TrustedNetworkHelper trustedNetworkHelper) {
        Boolean allAllowed = trustedNetworkHelper.allAllowed();
        emptyListMessage(trustedNetworkHelper);
//        trustedNetworksView.setVisibility(allAllowed ? GONE : VISIBLE);
        trustedNetworksView.setVisibility(allAllowed ? VISIBLE : VISIBLE);
//        if (allAllowed){
//            return;
//        }
        trustedNetworksView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, trustedNetworks));
        trustedNetworksView.setOnItemClickListener(onItemClickGenerator(trustedNetworkHelper));
        addNetworkButton(trustedNetworkHelper);
    }

    @NonNull
    private AdapterView.OnItemClickListener onItemClickGenerator(TrustedNetworkHelper trustedNetworkHelper) {
        return (parent, view, position, id) -> {
            if (dialogAlreadyShown) {
                return;
            }
            String targetItem = trustedNetworks.get(position);

            // remove touched item after confirmation
            DialogInterface.OnClickListener confirmationListener = (dialog, which) -> {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        trustedNetworks.remove(position);
                        trustedNetworkHelper.update(trustedNetworks);
                        ((ArrayAdapter) trustedNetworksView.getAdapter()).notifyDataSetChanged();
                        addNetworkButton(trustedNetworkHelper);
                        emptyListMessage(trustedNetworkHelper);
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                }
            };

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Delete " + targetItem + " ?");
            builder.setPositiveButton("Yes", confirmationListener);
            builder.setNegativeButton("No", confirmationListener);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) { //DismissListener
                dialogAlreadyShown = true;
                builder.setOnDismissListener(dialog -> dialogAlreadyShown = false);
            }

            builder.show();
        };
    }

    private void addNetworkButton(TrustedNetworkHelper trustedNetworkHelper) {
        String currentSSID = trustedNetworkHelper.currentSSID();
        if (!currentSSID.isEmpty() && trustedNetworks.indexOf(currentSSID) == -1) {
            Button addButton = (Button) findViewById(android.R.id.button1);
            String buttonText = getString(R.string.add_trusted_network, currentSSID);
            addButton.setText(buttonText);
            addButton.setOnClickListener(saveCurrentSSIDAsTrustedNetwork(currentSSID, trustedNetworkHelper));
            addButton.setVisibility(VISIBLE);
        }
    }


    @NonNull
    private View.OnClickListener saveCurrentSSIDAsTrustedNetwork(String ssid, TrustedNetworkHelper trustedNetworkHelper) {
        return v -> {
            if (trustedNetworks.indexOf(ssid) != -1){
                return;
            }
            trustedNetworks.add(ssid);
            trustedNetworkHelper.update(trustedNetworks);
            ((ArrayAdapter) trustedNetworksView.getAdapter()).notifyDataSetChanged();
            v.setVisibility(GONE);
        };
    }



    @Override
    protected void onStart() {
        super.onStart();
        BackgroundService.addGuiInUseCounter(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        BackgroundService.removeGuiInUseCounter(this);
    }

}
