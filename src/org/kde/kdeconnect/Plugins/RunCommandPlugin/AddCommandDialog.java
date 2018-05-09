package org.kde.kdeconnect.Plugins.RunCommandPlugin;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import org.kde.kdeconnect_tp.R;

public class AddCommandDialog extends DialogFragment {

    private EditText nameField;
    private EditText commandField;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.add_command);

        LayoutInflater inflater = getActivity().getLayoutInflater();

        View view = inflater.inflate(R.layout.addcommanddialog, null);

        nameField = (EditText) view.findViewById(R.id.addcommand_name);
        commandField = (EditText) view.findViewById(R.id.addcommand_command);

        builder.setView(view);

        builder.setPositiveButton(R.string.ok, (dialog, id) -> {

            if (getActivity() instanceof RunCommandActivity) {

                String name = nameField.getText().toString();
                String command = commandField.getText().toString();

                ((RunCommandActivity) getActivity()).dialogResult(name, command);
            }
        });
        builder.setNegativeButton(R.string.cancel, (dialog, id) -> {
        });

        return builder.create();
    }
}
