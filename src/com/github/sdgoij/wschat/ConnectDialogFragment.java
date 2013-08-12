package com.github.sdgoij.wschat;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class ConnectDialogFragment extends DialogFragment {

    private EditText name;
    private EditText uri;

    public static ConnectDialogFragment newInstance() {
        ConnectDialogFragment fragment = new ConnectDialogFragment();
        fragment.setArguments(new Bundle());
        return fragment;
    }

    @Override public Dialog onCreateDialog(Bundle state) {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        View root = inflater.inflate(R.layout.dialog_connect, null);

        name = (EditText)root.findViewById(R.id.edit_username);
        uri = (EditText)root.findViewById(R.id.edit_ws_server);

        Button connect = (Button)root.findViewById(R.id.button_connect);
        connect.setText(R.string.button_connect);
        connect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Log.d(MainActivity.TAG, "Clicked!");
                try {
                    MainActivity main = (MainActivity)getActivity();
                    main.connect(uri.getText().toString(), name.getText().toString());
                    dismiss();
                } catch (Exception e) {
                    Log.d(MainActivity.TAG, "Error: " + e.getMessage());
                    showErrorMessage("Error:" + e.getMessage());
                }
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(root);

        return builder.create();
    }

    public void showErrorMessage(String message) {
        Dialog d = getDialog();
        if (d != null) {
            TextView t = (TextView)d.findViewById(R.id.text_message);
            t.setVisibility(View.VISIBLE);
            t.setText(message);
        }
    }
}
