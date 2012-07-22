package com.meetup.scalerd;

import java.util.concurrent.Callable;

public class ImageReadTask implements Callable<byte[]> {
    private final String uri;

    public ImageReadTask(String uri) {
        this.uri = uri;
    }

    @Override
    public byte[] call() throws Exception {
        return new byte[0];
    }
}
