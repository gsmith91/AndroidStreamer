package com.alphagoose.screenstreamer;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.util.Log;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Surface;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.nio.ByteBuffer;

import okhttp3.WebSocket;
import okio.ByteString;

public class MediaProjectionService extends Service {

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaCodec mediaCodec;
    private Surface inputSurface;
    private Thread encodingThread;
    private boolean encoding = false;

    @Override
    public void onCreate() {
        super.onCreate();
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int resultCode = intent.getIntExtra("resultCode", -1);
        Intent data = intent.getParcelableExtra("data");

        if (resultCode != -1 && data != null) {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);

            // Initialize screen capturing process here
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int density = metrics.densityDpi;
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;

            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            virtualDisplay = mediaProjection.createVirtualDisplay("MediaProjectionService",
                    width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    inputSurface, null, null);


            initMediaCodec();
            startEncodingThread();
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaProjection != null) {

            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    private void initMediaCodec() {
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", 1280, 720);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 6000000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);

        try {
            mediaCodec = MediaCodec.createEncoderByType("video/avc");
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = mediaCodec.createInputSurface();
            mediaCodec.start();



        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startEncodingThread() {
        encoding = true;
        encodingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (encoding) {
                    handleEncodedData();
                }
            }
        });
        encodingThread.start();
    }

    private void stopEncodingThread() {
        encoding = false;
        try {
            if (encodingThread != null) {
                encodingThread.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void handleEncodedData() {
        log("HandleEncodedData");

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);

        while (outputBufferIndex >= 0) {
            ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
            if (outputBuffer != null) {

               // sendVideoData(outputBuffer);  // Send the data to the server
            }

            mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
        }
    }

    private  void log(String log)
    {
        Log.d("MyApp", log);
    }
}