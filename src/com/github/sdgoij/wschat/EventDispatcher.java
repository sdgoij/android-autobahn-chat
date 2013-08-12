package com.github.sdgoij.wschat;

import android.util.Log;

import de.tavendo.autobahn.WebSocket;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;

import org.codehaus.jackson.map.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventDispatcher {
    final public static String TAG = MainActivity.TAG + "/EventDispatcher";

    private WebSocketConnection ws = new WebSocketConnection();
    private Map<String, List<EventHandler>> handlers = new HashMap<String, List<EventHandler>>();
    private ObjectMapper mapper = new ObjectMapper();

    public void connect(String uri) throws WebSocketException {
        ws.connect(uri, this.new Handler(uri));
    }

    public void bind(String name, EventHandler handler) {
        if (!handlers.containsKey(name))
            handlers.put(name, new ArrayList<EventHandler>());
        List<EventHandler> list = handlers.get(name);
        list.add(handler);
    }

    public void send(Event event) throws Exception {
        ws.sendTextMessage(mapper.writeValueAsString(event));
    }

    public void send(String name, Map<String, Object> data) throws Exception {
        Event event = new Event(name);
        event.setData(data);
        send(event);
    }

    public static class Event {

        private String event;
        private Map<String, Object> data;

        public Event() {}
        public Event(String name) { event = name; }

        public String getEvent() { return event; }
        public void setEvent(String e) { event = e; }

        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> d) { data = d; }
    }

    public interface EventHandler {
        public void handle(Event event) throws Exception;
    }

    private class Handler implements WebSocket.ConnectionHandler {

        private String uri;

        public Handler(String u) {
            uri = u;
        }

        public void onClose(int code, String m) {
            Log.d(TAG, "Disconnected: " + m);
            handle(new Event("close"));
        }

        public void onOpen() {
            Log.d(TAG, "Connected to " + uri);
            handle(new Event("open"));
        }

        public void onTextMessage(String m) {
            Log.d(TAG, "Message: " + m);
            try {
                Event event = mapper.readValue(m, Event.class);
                handle(event);
            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
            }
        }

        public void onRawTextMessage(byte[] payload) {
            onTextMessage(new String(payload));
        }

        public void onBinaryMessage(byte[] payload) {
            onTextMessage(new String(payload));
        }

        private void handle(Event event) {
            String name = event.getEvent();
            if (!handlers.containsKey(name)) {
                Log.d(TAG, "Event Handler for '" + name + "' not found!");
                return;
            }
            for (EventHandler h : handlers.get(name)) {
                try {
                    h.handle(event);
                } catch (Exception e) {
                    Log.d(TAG, e.getMessage());
                }
            }
        }
    }

    public boolean isConnected() {
        return (null != ws) && ws.isConnected();
    }

    public void disconnect() {
        ws.disconnect();
    }
}
