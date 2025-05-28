package com.imageprocessing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class RabbitMQPublisher {
    private static final String EXCHANGE_NAME = "image_processing";
    private static final String ROUTING_KEY = "image.process";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Connection connection;
    private Channel channel;

    public RabbitMQPublisher() {
        try {
            initializeRabbitMQ();
        } catch (Exception e) {
            System.err.println("Failed to initialize RabbitMQ: " + e.getMessage());
        }
    }

    private void initializeRabbitMQ() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("rabbitmq");  // Container name in Docker Compose
        factory.setPort(5672);
        factory.setUsername("admin");
        factory.setPassword("admin123");
        
        // Retry connection until RabbitMQ is available
        int maxRetries = 10;
        int retryCount = 0;
        boolean connected = false;
        
        while (!connected && retryCount < maxRetries) {
            try {
                connection = factory.newConnection();
                connected = true;
            } catch (Exception e) {
                retryCount++;
                System.out.println("Failed to connect to RabbitMQ. Retrying in 5 seconds... (" + retryCount + "/" + maxRetries + ")");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        if (!connected) {
            throw new IOException("Failed to connect to RabbitMQ after " + maxRetries + " attempts");
        }
        
        channel = connection.createChannel();
        
        // Declare exchange
        channel.exchangeDeclare(EXCHANGE_NAME, "topic", true);
        
        // Declare queue
        String queueName = "image_processing_queue";
        channel.queueDeclare(queueName, true, false, false, null);
        
        // Bind queue to exchange
        channel.queueBind(queueName, EXCHANGE_NAME, ROUTING_KEY);
        
        System.out.println("RabbitMQ publisher initialized successfully");
    }

    public void publishMessage(Map<String, String> message) throws IOException {
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            channel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, null, messageJson.getBytes());
            System.out.println("Published message: " + messageJson);
        } catch (IOException e) {
            System.err.println("Failed to publish message: " + e.getMessage());
            throw e;
        }
    }

    public void close() {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (IOException | TimeoutException e) {
            System.err.println("Error closing RabbitMQ connection: " + e.getMessage());
        }
    }
}
