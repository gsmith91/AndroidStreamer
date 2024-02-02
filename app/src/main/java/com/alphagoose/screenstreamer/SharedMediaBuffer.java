package com.alphagoose.screenstreamer;

import java.nio.ByteBuffer;

public class SharedMediaBuffer {
    private static final SharedMediaBuffer instance = new SharedMediaBuffer();
    private ByteBuffer videoBuffer;
    private ByteBuffer audioBuffer;

    public static SharedMediaBuffer getInstance() {
        return instance;
    }

    private SharedMediaBuffer() {
    }

    public synchronized void setVideoData(ByteBuffer data) {
        this.videoBuffer = data;
    }

    public synchronized ByteBuffer getVideoData() {
        return videoBuffer;
    }

    public synchronized void setAudioData(ByteBuffer data) {
        this.videoBuffer = data;
    }

    public synchronized ByteBuffer getAudioData() {
        return videoBuffer;
    }
}