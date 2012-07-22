package com.meetup.scalerd;

import java.io.IOException;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.FutureCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ImageScaleTask implements FutureCallback<byte[]> {
    private static Log log = LogFactory.getLog(FutureCallback.class);

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
    public void onSuccess(byte[] bytes) {
        try {
            Channel ch = getChannel();
            ch.basicPublish("", replyTo, null, operations.toUpperCase().getBytes(Charsets.UTF_8));
        } catch (IOException ex) {
            log.error("bah", ex);
        }
    }

    @Override
    public void onFailure(Throwable throwable) {
    }
}
