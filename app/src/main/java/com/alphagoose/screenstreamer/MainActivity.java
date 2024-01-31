package com.alphagoose.screenstreamer;

import android.content.Context;
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

import java.io.IOException;
import java.nio.ByteBuffer;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class MainActivity extends Activity {
    private WebSocket webSocket;
    private MediaProjectionManager mediaProjectionManager;
    private boolean isRecording = false;
    private static final int REQUEST_CODE = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        Button connectButton = findViewById(R.id.connectButton);
        connectButton.setOnClickListener(v -> initWebSocket());
        Button disconnectButton = findViewById(R.id.disconnectButton);
        disconnectButton.setOnClickListener(v -> shutdownWebSocket());
        Button pingButton = findViewById(R.id.pingButton);
        pingButton.setOnClickListener(v -> pingSignalServer());

        Button startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(v -> startRecording());
        Button stopButton = findViewById(R.id.stopButton);
        stopButton.setOnClickListener(v -> stopRecording());

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {

            Intent intent = new Intent(this, MediaProjectionService.class);
            intent.putExtra("resultCode", resultCode);
            intent.putExtra("data", data);
            startService(intent);
            ContextCompat.startForegroundService(this, intent);
            isRecording = true;
        }
    }

    private void startRecording() {
        if (isRecording) {
            return;
        }

        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);

    }

    private void stopRecording() {
        if (!isRecording) {
            return;
        }
        //stopEncodingThread();
        isRecording = false;
        //mediaCodec.stop();
        //mediaCodec.release();
    }



    private void initWebSocket() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url("ws://10.0.2.2:8080").build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                super.onOpen(webSocket, response);
                runOnUiThread(() -> Log.i("WebSocket", "Connected to the server"));
                TextView textView = findViewById(R.id.websocketStatus);

                // Set the text value
                textView.setText("Signaling Server: Connected");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                super.onMessage(webSocket, text);
                runOnUiThread(() -> Log.i("WebSocket", "Message received: " + text));
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                super.onClosing(webSocket, code, reason);
                runOnUiThread(() -> Log.i("WebSocket", "Closing: " + reason));
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                super.onFailure(webSocket, t, response);
                runOnUiThread(() -> Log.e("WebSocket", "Error: " + t.getMessage()));
            }
        });
    }

    private void sendVideoData(ByteBuffer data) {
        if (webSocket != null) {
            webSocket.send(ByteString.of(data));
        }
    }

    private void shutdownWebSocket()
    {
        webSocket.close(1000, "Initiated by user");
    }

    private void pingSignalServer() {
    webSocket.send("Android-Ping");
    }


}
