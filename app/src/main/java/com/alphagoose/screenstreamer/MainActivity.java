package com.alphagoose.screenstreamer;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.widget.Button;
import android.hardware.display.DisplayManager;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.nio.ByteBuffer;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class MainActivity extends Activity {
    private WebSocketService webSocketService;
    private boolean isBound = false;

    // Define the ServiceConnection
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            WebSocketService.LocalBinder binder = (WebSocketService.LocalBinder) service;
            webSocketService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };
    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, new IntentFilter("WebSocketServiceUpdate"));
    }

    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
    }

    // Define the receiver
    private BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Extract data included in the Intent
            String message = intent.getStringExtra("message");
            // Do something with the message
            TextView textView = findViewById(R.id.websocketStatus);
            textView.setText(message);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        Button connectButton = findViewById(R.id.connectButton);
        connectButton.setOnClickListener(v -> startWebSocketService());
        Button disconnectButton = findViewById(R.id.disconnectButton);
        disconnectButton.setOnClickListener(v -> stopWebSocketService());
        Button pingButton = findViewById(R.id.pingButton);
        pingButton.setOnClickListener(v -> pingWebService());
    }


    private void startWebSocketService() {
        Intent serviceIntent = new Intent(this, WebSocketService.class);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void stopWebSocketService() {
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        Intent serviceIntent = new Intent(this, WebSocketService.class);
        stopService(serviceIntent);
    }

    private void pingWebService() {
        if (isBound) {
            webSocketService.pingSignalServer();
        }
    }
}
