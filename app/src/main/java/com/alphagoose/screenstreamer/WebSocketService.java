package com.alphagoose.screenstreamer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketService extends Service {
    private static final String CHANNEL_ID = "WebSocketServiceChannel";
    private WebSocket webSocket;
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        WebSocketService getService() {
            return WebSocketService.this;
        }
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // Public methods that you can call from the activity
    public void pingSignalServer() {
        webSocket.send("AndroidStudio: PING");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        int flags = 0;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            // Use FLAG_IMMUTABLE for Android 6.0 (API level 23) and above
            flags = PendingIntent.FLAG_IMMUTABLE;
        }

        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WebSocket Service")
                .setContentText("Running...")
                .setSmallIcon(R.drawable.ic_launcher_background) // Use your own icon
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);
        initWebSocket();
    }

    private void initWebSocket() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url("ws://10.0.2.2:8080").build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                mainThreadHandler.post(() -> {
                    Log.i("WebSocket", "Connected to the server");
                    Intent intent = new Intent("WebSocketServiceUpdate");
                    intent.putExtra("message", "Connected to the server");
                    LocalBroadcastManager.getInstance(WebSocketService.this).sendBroadcast(intent);
                });
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                mainThreadHandler.post(() -> {
                    Log.i("WebSocket", "Message received: " + text);
                    // Update UI or log appropriately
                });
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                mainThreadHandler.post(() -> Log.i("WebSocket", "Closing: " + reason));
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                mainThreadHandler.post(() -> Log.e("WebSocket", "Error: " + t.getMessage()));
            }
        });
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "WebSocket Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

}