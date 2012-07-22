package com.meetup.scalerd;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import com.google.common.base.Splitter;
import com.google.common.util.concurrent.FutureCallback;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ImageScaleTask implements FutureCallback<BufferedImage> {
    private static Log log = LogFactory.getLog(FutureCallback.class);
    private static final Splitter PIPE_SPLITTER = Splitter.on('|').omitEmptyStrings();

    private final Connection connection;
    private final String replyTo;
    private final String operations;

    private static final ThreadLocal<Channel> channels = new ThreadLocal<Channel>();

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
            for (String operation : PIPE_SPLITTER.split(operations)) {
                log.info(operation);
                //image = apply(image, operation);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean png = image.getColorModel().hasAlpha();
            String mimeType = png ? "image/png" : "image/jpeg";
            ImageWriter imageWriter = ImageIO.getImageWritersByMIMEType(mimeType).next();
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

    @Override
    public void onFailure(Throwable throwable) {
    }
}
