package com.meetup.scalerd;

import java.io.IOException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Daemon implements Runnable {
    private final ConnectionFactory connectionFactory;
    private final String exchangeName, queueName;
    private static Log log = LogFactory.getLog(Daemon.class);

    public Daemon(ConnectionFactory connectionFactory, String exchangeName, String queueName) {
        this.connectionFactory = connectionFactory;
        this.exchangeName = exchangeName;
        this.queueName = queueName;
    }

    public void run() {
        Connection connection = null;
        try {
            connection = connectionFactory.newConnection();
            Channel ch = connection.createChannel();
            ch.exchangeDeclare(exchangeName, "direct", true, false, null);
            ch.queueDeclare(queueName, true, false, false, null);
            ch.queueBind(queueName, exchangeName, queueName);
        } catch (IOException ex) {
            log.fatal("ioexception in Daemon", ex);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (Exception ignore) {
            }
        }
    }
}
