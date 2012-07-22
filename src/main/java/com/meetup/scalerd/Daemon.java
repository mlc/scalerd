package com.meetup.scalerd;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.rabbitmq.client.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;

public class Daemon implements Runnable {
    private static Log log = LogFactory.getLog(Daemon.class);

    private final ConnectionFactory connectionFactory;
    private final String exchangeName, queueName;
    private final JsonFactory jsonFactory;
    private final ListeningExecutorService readService, scaleService;

    private boolean shouldQuit = false;
    private Connection connection;

    public Daemon(ConnectionFactory connectionFactory, String exchangeName, String queueName) {
        this.connectionFactory = connectionFactory;
        this.exchangeName = exchangeName;
        this.queueName = queueName;

        ObjectMapper mapper = new ObjectMapper();
        jsonFactory = new JsonFactory(mapper);
        readService = makeExecutor(4);
        scaleService = makeExecutor(2);
    }

    public static ListeningExecutorService makeExecutor(int threads) {
        ExecutorService base = Executors.newFixedThreadPool(threads);
        return MoreExecutors.listeningDecorator(base);
    }

    public synchronized void run() {
        try {
            connection = connectionFactory.newConnection();
            log.info("connected to server");
            Channel ch = connection.createChannel();
            ch.exchangeDeclare(exchangeName, "direct", true, false, null);
            ch.queueDeclare(queueName, true, false, false, null);
            ch.queueBind(queueName, exchangeName, queueName); // topic name == queue name
            log.info("queue declared and bound");
            ch.basicConsume(queueName, false, new RpcConsumer(ch));
            while(!shouldQuit)
                wait();
        } catch (IOException ex) {
            log.fatal("ioexception in Daemon", ex);
        } catch (InterruptedException ignore) {
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (Exception ignore) {
            }
        }
    }

    void signalQuit() {
        shouldQuit = true;
    }

    private class RpcConsumer extends DefaultConsumer {
        private RpcConsumer(Channel channel) {
            super(channel);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            final long deliveryTag = envelope.getDeliveryTag();
            String contentType = properties.getContentType();
            boolean acked = false;
            try {
                if (!("application/json".equals(contentType))) {
                    throw new IllegalArgumentException("wrong content type " + contentType);
                }
                JsonParser jp = jsonFactory.createJsonParser(body);
                RpcInput args = jp.readValueAs(RpcInput.class);
                log.debug(args);
                getChannel().basicAck(deliveryTag, false);
                acked = true;
                ListenableFuture<byte[]> read = readService.submit(new ImageReadTask(args.getUri()));
                Futures.addCallback(read, new ImageScaleTask(connection, properties.getReplyTo(), args.getOperations()), scaleService);
            } catch (Exception ex) {
                log.warn("exception in handleDelivery", ex);
            } finally {
                if (!acked)
                    getChannel().basicNack(deliveryTag, false, false);
            }
        }

        @Override
        public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
            signalQuit();
        }
    }
}
