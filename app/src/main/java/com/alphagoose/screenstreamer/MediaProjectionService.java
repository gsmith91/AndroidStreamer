package com.alphagoose.screenstreamer;
import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.media.MediaCodec;
import android.media.MediaFormat;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaProjectionService extends Service {
    private static final String CHANNEL_ID = "MediaProjectionServiceChannel";
    private static final long TIMEOUT_US = 10000; // Timeout for dequeuing
    private final IBinder binder = new LocalBinder();
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private Surface inputSurface;
    private MediaCodec videoEncoder;

    public class LocalBinder extends Binder {
        MediaProjectionService getService() {
            return MediaProjectionService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize the MediaProjectionManager for later use.
        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        // Notification setup for foreground service.
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Media Projection Service")
                .setContentText("Running...")
                .setSmallIcon(R.drawable.ic_launcher_background) // Use your own icon
                .setContentIntent(pendingIntent)
                .build();

        // Start service in the foreground.
        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Process the intent data if available.
        if (intent != null && intent.hasExtra("resultCode") && intent.hasExtra("data")) {
            int resultCode = intent.getIntExtra("resultCode", RESULT_CANCELED);
            Intent projectionData = intent.getParcelableExtra("data");

            if (resultCode == RESULT_OK && projectionData != null) {
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, projectionData);
                if (mediaProjection != null) {
                    // The mediaProjection is ready, now you can create a virtual display or start capturing.
                    createVirtualDisplay();
                } else {
                    Log.e("MediaProjectionService", "Initialization failed: MediaProjection is null");
                }
            } else {
                Log.e("MediaProjectionService", "Failed to initialize MediaProjection.");
            }
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Media Projection Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private void createVirtualDisplay() {
        if (mediaProjection != null) {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int density = metrics.densityDpi;
            int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;

            initializeVideoEncoder(width, height);
            handleEncodedOutputData(videoEncoder);

            // Assume you have an initialized encoder with an input surface
            virtualDisplay = mediaProjection.createVirtualDisplay("MediaProjectionService",
                    width, height, density, flags, inputSurface, null, null);
        } else {
            Log.e("MediaProjectionService", "createVirtualDisplay called before mediaProjection initialized");
        }
    }

    private MediaCodecInfo selectCodec(String mimeType) {
        // Use REGULAR_CODECS to list only the codecs that are available for regular app usage.
        // Use ALL_CODECS to include codecs that are reserved for system use.
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaCodecInfo[] codecInfos = codecList.getCodecInfos();

        for (MediaCodecInfo codecInfo : codecInfos) {
            if (!codecInfo.isEncoder()) {
                continue; // Skip decoder-only codecs.
            }

            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType)) {
                    // This codec supports the specified MIME type, return it.
                    return codecInfo;
                }
            }
        }

        // If we couldn't find a matching codec, return null.
        return null;
    }

    private void initializeVideoEncoder(int width, int height) {
        try {
            // Use the selectCodec method to find a codec that supports AVC (H.264) video format.
            MediaCodecInfo codecInfo = selectCodec(MediaFormat.MIMETYPE_VIDEO_AVC);
            if (codecInfo == null) {
                Log.e("initializeVideoEncoder", "Compatible codec not found for " + MediaFormat.MIMETYPE_VIDEO_AVC);
                return;
            }

            MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC);
            int selectedColorFormat = selectColorFormat(capabilities);
            if (selectedColorFormat == 0) {
                Log.e("initializeVideoEncoder", "No suitable color format found.");
                return;
            }

            videoEncoder = MediaCodec.createByCodecName(codecInfo.getName());
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);

            // Configure your format here (bit rate, frame rate, I-frame interval, etc.)
            int bitRate = 5000000; // Example bit rate
            int frameRate = 30; // Example frame rate
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5); // Example I-frame interval
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, selectedColorFormat); // Set selected color format

            videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = videoEncoder.createInputSurface();
            videoEncoder.start();

            // Remember to handle the encoded frames in a separate thread.

        } catch (IOException e) {
            Log.e("initializeVideoEncoder", "IOException initializing video encoder: " + e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            Log.e("initializeVideoEncoder", "Exception configuring encoder: " + e.getMessage());
        }
    }

    private int selectColorFormat(MediaCodecInfo.CodecCapabilities capabilities) {
        for (int colorFormat : capabilities.colorFormats) {
            if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
                return colorFormat;
            }
        }
        return 0; // Return 0 if no suitable color format is found
    }

    private void handleEncodedOutputData(final MediaCodec encoder) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                boolean isEncoding = true; // Control encoding loop

                while (isEncoding) {
                    // Dequeue an available output buffer index
                    int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);

                    if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // No output available yet
                    } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // Output format has changed, handle it here if necessary
                    } else if (outputBufferIndex >= 0) {
                        // Get the output buffer directly
                        ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);

                        if (bufferInfo.size > 0) {
                            handleEncodedVideoFrames();
                        }

                        // Release the buffer back to the encoder
                        encoder.releaseOutputBuffer(outputBufferIndex, false);

                        // Check if this is the end of the stream
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isEncoding = false; // Stop encoding loop
                        }
                    }
                }

                // Clean up and release the encoder
                encoder.stop();
                encoder.release();
            }
        }).start();
    }


    private void handleEncodedVideoFrames() {
        // This is a simplified outline. Actual implementation will vary.
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = videoEncoder.dequeueOutputBuffer(bufferInfo, 3000);
        if (outputBufferIndex >= 0) {
            ByteBuffer outputBuffer = videoEncoder.getOutputBuffer(outputBufferIndex);

            // Process the frame for WebRTC transmission
            sendFrameToWebRTC(outputBuffer, bufferInfo);

            videoEncoder.releaseOutputBuffer(outputBufferIndex, false);
        }
    }

    private void sendFrameToWebRTC(ByteBuffer frame, MediaCodec.BufferInfo bufferInfo) {
        // Convert the frame to a format suitable for WebRTC and send it
        // This will involve the WebRTC native APIs to handle video frames
    }
}