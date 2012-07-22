package com.meetup.scalerd;

import java.io.FileReader;
import java.util.Properties;
import com.google.common.io.Closeables;
import com.rabbitmq.client.ConnectionFactory;

public class Main {
    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        FileReader in = null;
        try {
            in = new FileReader("scalerd.properties");
            props.load(in);
        } finally {
            Closeables.closeQuietly(in);
        }
        ConnectionFactory cf = new ConnectionFactory();
        cf.setUri(props.getProperty("amqp.uri"));
        Daemon daemon = new Daemon(cf, props.getProperty("amqp.exchange"), props.getProperty("amqp.queue"));
        daemon.run();
    }
}
