package com.alphagoose.screenstreamer;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MainActivity extends Activity {
    private static final int REQUEST_MEDIA = 713;
    private WebSocketService webSocketService;
    private MediaProjectionService mediaProjectionService;
    private boolean isWebSocketServiceBound = false;
    private boolean isMediaProjectionServiceBound = false;


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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA && resultCode == RESULT_OK) {
            // Create an Intent for starting your MediaProjectionService
            Intent serviceIntent = new Intent(this, MediaProjectionService.class);

            // Put the resultCode and data (which contains the screen capture permission) into the Intent
            serviceIntent.putExtra("resultCode", resultCode);
            serviceIntent.putExtra("data", data);

            ContextCompat.startForegroundService(this, serviceIntent);
            bindService(serviceIntent, mediaProjectionServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

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

        Button callButton = findViewById(R.id.callButton);
        callButton.setOnClickListener(v -> callVideoReceiver());

        Button startRecordingButton = findViewById(R.id.startButton);
        startRecordingButton.setOnClickListener(v -> requestMediaProjection());

        Button stopRecordingButton = findViewById(R.id.stopButton);
        stopRecordingButton.setOnClickListener(v -> stopMediaProjectionService());
    }

    private void startWebSocketService() {
        Intent serviceIntent = new Intent(this, WebSocketService.class);
        bindService(serviceIntent, webSocketServiceConnection, Context.BIND_AUTO_CREATE);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void stopWebSocketService() {
        if (isWebSocketServiceBound) {
            unbindService(webSocketServiceConnection);
            isWebSocketServiceBound = false;
        }
        Intent serviceIntent = new Intent(this, WebSocketService.class);
        stopService(serviceIntent);
    }

    private void pingWebService() {
        if (isWebSocketServiceBound) {
            //webSocketService.pingSignalServer();
            webSocketService.sendTestData("TESTINGGGG");
        }
    }

    private void callVideoReceiver() {
        webSocketService.callVideoReceiver();
    }

    private void requestMediaProjection() {
        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA);
    }

    private void stopMediaProjectionService() {
        if (isMediaProjectionServiceBound) {
            unbindService(mediaProjectionServiceConnection);
            isMediaProjectionServiceBound = false;
            Intent serviceIntent = new Intent(this, MediaProjectionService.class);
            stopService(serviceIntent);
        }
    }


    private ServiceConnection webSocketServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            WebSocketService.LocalBinder binder = (WebSocketService.LocalBinder) service;
            webSocketService = binder.getService();
            isWebSocketServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isWebSocketServiceBound = false;
        }
    };

    private ServiceConnection mediaProjectionServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MediaProjectionService.LocalBinder binder = (MediaProjectionService.LocalBinder) service;
            mediaProjectionService = binder.getService();
            isMediaProjectionServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isMediaProjectionServiceBound = false;
        }
    };

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
}
