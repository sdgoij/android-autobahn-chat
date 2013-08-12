package com.github.sdgoij.wschat;

import android.app.DialogFragment;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import de.tavendo.autobahn.WebSocketException;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends FragmentActivity {

    public static final String TAG = "ws.autobahn";

    private EventDispatcher dispatcher = new EventDispatcher();
    private String username = "";

    private EditText message;
    private TextView chat;
    private Button send;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        if (!dispatcher.isConnected()) {
            DialogFragment d = new ConnectDialogFragment();
            d.show(getFragmentManager(), "ConnectDialogFragment");
        }

        chat = (TextView)findViewById(R.id.chat);
        message = (EditText)findViewById(R.id.message);
        send = (Button)findViewById(R.id.send);

        setSendOnClickListener();

        dispatcher.bind("open", new EventDispatcher.EventHandler() {
            @Override public void handle(EventDispatcher.Event event) throws Exception {
                Log.d(TAG, "Connection open");
                try {
                    Map<String, Object> data = new HashMap<String, Object>();
                    data.put("username", username);
                    dispatcher.send("register", data);
                } catch (Exception e) {
                    Log.d(TAG, e.getMessage());
                }
            }
        });

        dispatcher.bind("message", new EventDispatcher.EventHandler() {
            @Override public void handle(EventDispatcher.Event event) throws Exception {
                Map<String, Object> data = event.getData();
                if (!data.containsKey("username") || !data.containsKey("message")) {
                    throw new Exception("Invalid event data.");
                }
                String user = (String)(data.get("username")) + ": ";
                SpannableString text = new SpannableString(user + (String)data.get("message") + "\n");
                text.setSpan(new StyleSpan(Typeface.BOLD), 0, user.length()-1, 0);
                text.setSpan(new UnderlineSpan(), 0, user.length()-1, 0);
                chat.append(text);
            }
        });

        dispatcher.bind("close", new EventDispatcher.EventHandler() {
            @Override public void handle(EventDispatcher.Event event) throws Exception {
                Log.d(TAG, "Connection closed");
                setConnectOnClickListener();
            }
        });

        dispatcher.bind("registration", new EventDispatcher.EventHandler() {
            @Override public void handle(EventDispatcher.Event event) throws Exception {
                Map<String, Object> data = event.getData();
                if (!data.containsKey("username")) {
                    throw new Exception("Invalid event data.");
                }
                Log.d(TAG, "username=" + (String)data.get("username"));
                data = new HashMap<String, Object>();
                data.put("message", "[Connected]");
                dispatcher.send("message", data);
            }
        });
    }

    public void connect(String uri, String name) throws  WebSocketException {
        Log.d(TAG, "Connecting NAME: " +  name + " URI: " + uri + " ...");
        if (dispatcher.isConnected()) {
            Log.d(TAG, "Connected, disconnecting ...");
            dispatcher.disconnect();
            Log.d(TAG, "Disconnected");
        }
        username = name;
        dispatcher.connect(uri);
    }

    private void setSendOnClickListener() {
        send.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Map<String, Object> data = new HashMap<String, Object>();
                    data.put("message", message.getText().toString());
                    data.put("username", username);
                    dispatcher.send("message", data);
                } catch (Exception e) {
                    Log.d(TAG, e.getMessage());
                }
                message.setText("");
            }
        });
        send.setText(R.string.button_send);
    }

    private void setConnectOnClickListener() {
        send.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                DialogFragment d = new ConnectDialogFragment();
                d.show(getFragmentManager(), "ConnectDialogFragment");
            }
        });
        send.setText(R.string.button_connect);
    }
}
