package com.meetup.scalerd;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.concurrent.Callable;
import javax.imageio.ImageIO;

public class ImageReadTask implements Callable<BufferedImage> {
    private final String uri;

    public ImageReadTask(String uri) {
        this.uri = uri;
    }

    @Override
    public BufferedImage call() throws Exception {
        return ImageIO.read(new URL(uri));
    }
}
