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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

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
    private DataChannel dataChannel;
    private Queue<JSONObject> offerQueue = new LinkedList<>();
    private Queue<JSONObject> answerQueue = new LinkedList<>();

    public class LocalBinder extends Binder {
        WebSocketService getService() {
            return WebSocketService.this;
        }
    }

    public void pingSignalServer() {
        webSocket.send("AndroidStudio: PING");
    }
    public void callVideoReceiver() {
        createOffer();
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
        Request request = new Request.Builder().url("https://fern-talented-drop.glitch.me/").build();
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
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {

            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {

            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

            }

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
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

            }

            @Override
            public void onAddStream(MediaStream mediaStream) {

            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {

            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {

            }

            @Override
            public void onRenegotiationNeeded() {

            }

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

            }

        });

    }


    private void waitForThreeSecondsThenCallMethod() {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Call your method here
                processQueuedMessages();
            }
        }, 3000);
    }

    private void createOffer() {
        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription offer) {
                // Set local description
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {

                    }

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
                    public void onCreateFailure(String error) {

                    }

                    @Override
                    public void onSetFailure(String error) {

                    }
                }, offer);

            }


            @Override
            public void onSetSuccess() {

            }

            @Override
            public void onCreateFailure(String error) {

            }

            @Override
            public void onSetFailure(String error) {

            }
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
            initDataChannel();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void handleAnswer(JSONObject message) throws JSONException {
        if (peerConnection == null || peerConnection.signalingState() != PeerConnection.SignalingState.STABLE) {
            // If peerConnection is not ready, enqueue the answer for later processing
            answerQueue.offer(message);

            waitForThreeSecondsThenCallMethod();

            return;
        }

        // Process the answer immediately
        SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, message.getString("sdp"));
        peerConnection.setRemoteDescription(new SdpObserverAdapter() {
            @Override
            public void onSetSuccess() {
                // Create the local description (answer)
                peerConnection.createAnswer(new SdpObserverAdapter() {
                    @Override
                    public void onCreateSuccess(SessionDescription localDescription) {
                        // Set the local description
                        peerConnection.setLocalDescription(new SdpObserverAdapter(), localDescription);
                    }

                    @Override
                    public void onCreateFailure(String error) {
                        Log.e("WebSocketService", "Failed to create answer: " + error);
                    }
                }, new MediaConstraints());
            }

            @Override
            public void onSetFailure(String error) {
                Log.e("WebSocketService", "Failed to set remote description: " + error);
            }
        }, answer);
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


    private void initDataChannel() {
        DataChannel.Init dataChannelInit = new DataChannel.Init();
        dataChannelInit.ordered = true; // Messages are delivered in order
        dataChannelInit.negotiated = false;
        dataChannelInit.id = 0; // The ID for the data channel. Automatically chosen if -1
        dataChannelInit.maxRetransmits = -1; // No retransmits
        dataChannel = peerConnection.createDataChannel("testChannel", dataChannelInit);

        dataChannel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long previousAmount) {
                // Called when the buffered amount changes
            }

            @Override
            public void onStateChange() {
                // Called when the state of the DataChannel changes
                if (dataChannel.state() == DataChannel.State.OPEN) {
                    Log.i("DataChannel", "DataChannel is open");
                }
            }

            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                // Called when a message is received. This is where you handle incoming messages.
                ByteBuffer data = buffer.data;
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                String message = new String(bytes);
                Log.i("DataChannel", "Received message: " + message);
                // Optionally, update UI or handle the message as needed
            }
        });
    }

    public void sendTestData(String data) {
        // Create a JSON object and put "data" with your message
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("message", data);
            String jsonString = jsonObject.toString();

            if (dataChannel != null && dataChannel.state() == DataChannel.State.OPEN) {
                ByteBuffer buffer = ByteBuffer.wrap(jsonString.getBytes());
                DataChannel.Buffer buf = new DataChannel.Buffer(buffer, false);
                dataChannel.send(buf);
                Log.i("DataChannel", "Sent data: " + jsonString);
            } else {
                Log.e("DataChannel", "DataChannel is not open or not initialized.");
            }
        } catch (JSONException e) {
            Log.e("DataChannel", "Failed to create JSON: " + e.getMessage());
        }
    }

    // Method to process queued offers and answers
    private void processQueuedMessages() {
        // Process queued offers
        while (!offerQueue.isEmpty()) {
            JSONObject offer = offerQueue.poll();
            try {
                handleOffer(offer);
            } catch (JSONException e) {
                Log.e("WebSocketService", "Error processing queued offer: " + e.getMessage());
            }
        }

        // Process queued answers
        while (!answerQueue.isEmpty()) {
            JSONObject answer = answerQueue.poll();
            try {
                handleAnswer(answer);
            } catch (JSONException e) {
                Log.e("WebSocketService", "Error processing queued answer: " + e.getMessage());
            }
        }
    }
}