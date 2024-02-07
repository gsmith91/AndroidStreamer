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

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketService extends Service {
    private static final String CHANNEL_ID = "WebSocketServiceChannel";
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private final IBinder binder = new LocalBinder();
    private WebSocket webSocket;
    private PeerConnection peerConnection;

    public class LocalBinder extends Binder {
        WebSocketService getService() {
            return WebSocketService.this;
        }
    }

    public void pingSignalServer() {
        webSocket.send("AndroidStudio: PING");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
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

                    initializeWebRTC();
                });
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                mainThreadHandler.post(() -> {
                    Log.i("WebSocket", "Message received: " + text);
                    try {
                        JSONObject message = new JSONObject(text);
                        String type = message.optString("type");
                        switch (type) {
                            case "offer":
                                handleOffer(message);
                                break;
                            case "answer":
                                handleAnswer(message);
                                break;
                            case "candidate":
                                handleIceCandidate(message);
                                break;
                            default:
                                Log.e("WebSocket", "Unknown message type: " + type);
                                break;
                        }
                    } catch (JSONException e) {
                        Log.e("WebSocket", "Failed to parse message: " + e.getMessage());
                    }
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

    private void initializeWebRTC()
    {
        // Inside your Activity or Service
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        PeerConnectionFactory factory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);

        peerConnection = factory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {}

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {}

            @Override
            public void onIceConnectionReceivingChange(boolean b) {}

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                try {
                    JSONObject candidateMessage = new JSONObject();
                    candidateMessage.put("type", "candidate");
                    candidateMessage.put("sdpMid", iceCandidate.sdpMid);
                    candidateMessage.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
                    candidateMessage.put("candidate", iceCandidate.sdp);
                    webSocket.send(candidateMessage.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}

            @Override
            public void onAddStream(MediaStream mediaStream) {}

            @Override
            public void onRemoveStream(MediaStream mediaStream) {}

            @Override
            public void onDataChannel(DataChannel dataChannel) {}

            @Override
            public void onRenegotiationNeeded() {}

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {}

        });

        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription offer) {
                // Set local description
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {}

                    @Override
                    public void onSetSuccess() {
                        // Send the offer to the remote peer via WebSocket
                        try {
                            JSONObject offerMessage = new JSONObject();
                            offerMessage.put("type", "offer");
                            offerMessage.put("sdp", offer.description);
                            webSocket.send(offerMessage.toString());
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onCreateFailure(String error) {}

                    @Override
                    public void onSetFailure(String error) {}
                }, offer);
            }

            @Override
            public void onSetSuccess() {}

            @Override
            public void onCreateFailure(String error) {}

            @Override
            public void onSetFailure(String error) {}
        }, new MediaConstraints());

    }

    private void handleOffer(JSONObject message) throws JSONException {
        SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, message.getString("sdp"));
        peerConnection.setRemoteDescription(new SdpObserverAdapter() {
            @Override
            public void onSetSuccess() {
                peerConnection.createAnswer(new SdpObserverAdapter() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        peerConnection.setLocalDescription(new SdpObserverAdapter(), sessionDescription);
                        sendAnswer(sessionDescription);
                    }
                }, new MediaConstraints());
            }
        }, offer);
    }

    private void sendAnswer(SessionDescription answer) {
        try {
            JSONObject answerMessage = new JSONObject();
            answerMessage.put("type", "answer");
            answerMessage.put("sdp", answer.description);
            webSocket.send(answerMessage.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void handleAnswer(JSONObject message) throws JSONException {
        SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, message.getString("sdp"));
        peerConnection.setRemoteDescription(new SdpObserverAdapter(), answer);
    }

    private void handleIceCandidate(JSONObject message) throws JSONException {
        if (message.has("candidate")) {
            IceCandidate candidate = new IceCandidate(
                    message.getString("sdpMid"),
                    message.getInt("sdpMLineIndex"),
                    message.getString("candidate")
            );
            peerConnection.addIceCandidate(candidate);
        }
    }

    private class SdpObserverAdapter implements SdpObserver {
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            // Default implementation does nothing
        }

        @Override
        public void onSetSuccess() {
            // Default implementation does nothing
        }

        @Override
        public void onCreateFailure(String s) {
            // Default implementation logs the error
            Log.e("SdpObserverAdapter", "onCreateFailure: " + s);
        }

        @Override
        public void onSetFailure(String s) {
            // Default implementation logs the error
            Log.e("SdpObserverAdapter", "onSetFailure: " + s);
        }
    }

}