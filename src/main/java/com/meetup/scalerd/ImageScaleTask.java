package com.meetup.scalerd;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.imgscalr.Scalr;

public class ImageScaleTask implements FutureCallback<BufferedImage> {
    private static Log log = LogFactory.getLog(FutureCallback.class);
    private static final Splitter EXCLAIM_SPLITTER = Splitter.on('!').omitEmptyStrings();
    private static final Splitter PIPE_SPLITTER = Splitter.on('|').omitEmptyStrings();
    private static final Splitter X_SPLITTER = Splitter.on('x').omitEmptyStrings();
    private static final Splitter COMMA_SPLITTER = Splitter.on(',');
    private static final ThreadLocal<Channel> channels = new ThreadLocal<Channel>();

    private final Connection connection;
    private final String replyTo;
    private final String operations;
    private float quality = 0.75f;

    public ImageScaleTask(Connection connection, String replyTo, String operations) {
        this.connection = connection;
        this.replyTo = replyTo;
        this.operations = operations;
    }

    private Channel getChannel() throws IOException {
        Channel ch = channels.get();
        if (ch == null) {
            ch = connection.createChannel();
            channels.set(ch);
        }
        return ch;
    }

    @Override
    public void onSuccess(BufferedImage image) {
        try {
            for (String operation : EXCLAIM_SPLITTER.split(operations)) {
                log.info(operation);
                image = apply(image, operation);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean png = image.getColorModel().hasAlpha();
            String mimeType = png ? "image/png" : "image/jpeg";
            ImageWriter imageWriter = ImageIO.getImageWritersByMIMEType(mimeType).next();
            if (!png) {
                ImageWriteParam iwp = imageWriter.getDefaultWriteParam();
                iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                iwp.setCompressionQuality(quality);
            }
            ImageOutputStream stream = ImageIO.createImageOutputStream(out);
            imageWriter.setOutput(stream);
            try {
                imageWriter.write(image);
            } finally {
                imageWriter.dispose();
                stream.flush();
            }

            Channel ch = getChannel();
            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .contentType(mimeType)
                    .build();
            ch.basicPublish("", replyTo, props, out.toByteArray());
        } catch (IOException ex) {
            log.error("bah", ex);
        }
    }

    private BufferedImage apply(BufferedImage image, String operation) {
        Iterator<String> parts = PIPE_SPLITTER.split(operation).iterator();
        String op, args = null;
        if (!parts.hasNext()) {
            log.warn("empty operation");
            return image;
        }
        op = parts.next();
        if (parts.hasNext())
            args = parts.next();
        if ("rs".equals(op))
            return resize(image, args, false);
        else if ("rx".equals(op))
            return resize(image, args, true);
        else if ("cr".equals(op))
            return crop(image, args);
        else if ("rt".equals(op))
            return rotate(image, args);
        else if ("ql".equals(op)) {
            setQuality(args);
            return image;
        } else {
            log.warn("unknown operation " + op);
            return image;
        }
    }

    private static BufferedImage resize(BufferedImage image, String args, boolean max) {
        int[] params = parseIntArgs(args, true);
        if (params.length != 2) {
            log.warn("invalid args to resize " + args);
            return image;
        }
        int targetWidth = params[0], targetHeight = params[1];
        if (targetWidth <= 0 || targetHeight <= 0)
            return image;

        int initialWidth = image.getWidth(), initialHeight = image.getHeight();
        double widthFactor = ((double)targetWidth / initialWidth),
                heightFactor = ((double)targetHeight / initialHeight);
        boolean useWidth = max ? (widthFactor > heightFactor) : (widthFactor < heightFactor);
        if ((useWidth ? widthFactor : heightFactor) > 1.0)
            return image;

        BufferedImage ret = Scalr.resize(image, Scalr.Method.AUTOMATIC,
                useWidth ? Scalr.Mode.FIT_TO_WIDTH : Scalr.Mode.FIT_TO_HEIGHT,
                useWidth ? targetWidth : targetHeight);
        image.flush();
        return ret;
    }

    private static BufferedImage crop(BufferedImage image, String args) {
        int[] params = parseIntArgs(args, false);
        if (params.length == 2) {
            return centerCrop(image, params[0], params[1]);
        } else if (params.length == 4) {
            return crop(image, params[0], params[1], params[2], params[3]);
        } else {
            log.warn("invalid args to crop " + args);
            return image;
        }
    }

    private static BufferedImage centerCrop(BufferedImage image, int w, int h) {
        int initialWidth = image.getWidth(), initialHeight = image.getHeight();
        if (w <= 0 || h <= 0)
            return image;
        w = Math.min(w, initialWidth);
        h = Math.min(h, initialHeight);

        if (w == initialWidth && h == initialHeight)
            return image;
        int x = (initialWidth - w) / 2,
                y = (initialHeight - h) / 2;
        BufferedImage ret = Scalr.crop(image, x, y, w, h);
        image.flush();
        return ret;
    }

    private static BufferedImage crop(BufferedImage image, int w, int h, int x, int y) {
        int initialWidth = image.getWidth(), initialHeight = image.getHeight();
        if (w <= 0 || h <= 0 || x < 0 || y < 0)
            return image;
        if ((w+x) > initialWidth) {
            w = initialWidth - x;
        }
        if ((h+y) > initialHeight) {
            h = initialHeight - y;
        }
        BufferedImage ret = Scalr.crop(image, x, y, w, h);
        image.flush();
        return ret;
    }

    private static BufferedImage rotate(BufferedImage image, String args) {
        int angle;
        try {
            angle = Integer.parseInt(args, 10);
        } catch (Exception ex) {
            log.warn("invalid rotate args " + args);
            return image;
        }
        Scalr.Rotation rotation;
        switch(angle) {
        case 0:
            return image;
        case 90:
            rotation = Scalr.Rotation.CW_90;
            break;
        case 180:
            rotation = Scalr.Rotation.CW_180;
            break;
        case 270:
            rotation = Scalr.Rotation.CW_270;
            break;
        default:
            log.warn("invalid rotate angle " + angle);
            return image;
        }
        BufferedImage ret = Scalr.rotate(image, rotation);
        image.flush();
        return ret;
    }

    private void setQuality(String args) {
        try {
            float f = Float.parseFloat(args);
            if (f < 0.0f || f > 100.0f)
                throw new IllegalArgumentException();
            quality = f / 100.0f;
        } catch (Exception ex) {
            log.warn("invalid quality " + args);
        }
    }

    private static int[] parseIntArgs(String args, boolean x) {
        try {
            List<Integer> tmp = Lists.newArrayListWithExpectedSize(4);
            for (String s : (x ? X_SPLITTER : COMMA_SPLITTER).split(args)) {
                tmp.add(Integer.parseInt(s, 10));
            }
            int len = tmp.size();
            int[] ret = new int[len];
            for (int i = 0; i < len; ++i) {
                ret[i] = tmp.get(i);
            }
            return ret;
        } catch (Exception ex) {
            return new int[0];
        }
    }

    @Override
    public void onFailure(Throwable throwable) {
    }
}
