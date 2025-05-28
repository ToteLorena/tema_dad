package com.imageprocessing.mdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class Main {
    private static final String QUEUE_NAME = "image_processing_queue";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        try {
            // Connect to RabbitMQ
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("rabbitmq");  // Container name in Docker Compose
            factory.setPort(5672);
            factory.setUsername("admin");
            factory.setPassword("admin123");
            
            // Retry connection until RabbitMQ is available
            int maxRetries = 10;
            int retryCount = 0;
            Connection connection = null;
            
            while (connection == null && retryCount < maxRetries) {
                try {
                    connection = factory.newConnection();
                } catch (Exception e) {
                    retryCount++;
                    System.out.println("Failed to connect to RabbitMQ. Retrying in 5 seconds... (" + retryCount + "/" + maxRetries + ")");
                    Thread.sleep(5000);
                }
            }
            
            if (connection == null) {
                throw new IOException("Failed to connect to RabbitMQ after " + maxRetries + " attempts");
            }
            
            Channel channel = connection.createChannel();
            
            // Declare queue (to ensure it exists)
            channel.queueDeclare(QUEUE_NAME, true, false, false, null);
            
            // Set prefetch count to 1 to ensure fair dispatch
            channel.basicQos(1);
            
            System.out.println("Waiting for messages...");
            
            // Create consumer
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("Received message: " + message);
                
                try {
                    // Parse message
                    Map<String, String> jobData = objectMapper.readValue(message, Map.class);
                    
                    // Process the image using OpenMPI
                    processImageWithOpenMPI(jobData);
                    
                    // Acknowledge message
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } catch (Exception e) {
                    System.err.println("Error processing message: " + e.getMessage());
                    // Reject message and requeue
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                }
            };
            
            // Start consuming messages
            channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> {});
            
            // Keep the application running
            Thread.currentThread().join();
        } catch (IOException | InterruptedException e) {
            System.err.println("Error in MDB: " + e.getMessage());
        }
    }

    private static void processImageWithOpenMPI(Map<String, String> jobData) {
        try {
            String jobId = jobData.get("jobId");
            String filePath = jobData.get("filePath");
            String key = jobData.get("key");
            String operation = jobData.get("operation");
            String mode = jobData.get("mode");
            
            System.out.println("Processing job " + jobId + " with OpenMPI...");
            
            // Build command to execute OpenMPI program
            String command = "mpirun -np 2 /app/openmpi/image_processor "
           + filePath + ' ' + key + ' ' + operation + ' ' + mode + ' ' + jobId;
            
            System.out.println("Executing command: " + command);
            
            // Execute command
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                throw new RuntimeException("OpenMPI process failed with exit code " + exitCode);
            }
            
            System.out.println("OpenMPI processing completed successfully");
            
            // Notify Node.js that processing is complete
            notifyProcessingComplete(jobId);
        } catch (Exception e) {
            System.err.println("Error processing image with OpenMPI: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static void notifyProcessingComplete(String jobId) {
        try {
            // Send HTTP request to Node.js to notify that processing is complete
            String url = "http://nodejs-db:8080/api/notify";
            String jsonPayload = String.format("{\"jobId\":\"%s\",\"status\":\"completed\"}", jobId);
            
            // Use Java's HttpClient to send the request
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
            
            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to notify Node.js: " + response.body());
            }
            
            System.out.println("Successfully notified Node.js of job completion");
        } catch (Exception e) {
            System.err.println("Error notifying Node.js: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
