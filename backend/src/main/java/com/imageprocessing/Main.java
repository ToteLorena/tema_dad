package com.imageprocessing;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import io.javalin.http.staticfiles.Location;
import java.util.Objects;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Main {
    private static final String UPLOAD_DIR = "/app/shared/uploads";
    private static final Map<String, JobStatus> jobStatuses = new HashMap<>();

    public static void main(String[] args) {
        // Create upload directory if it doesn't exist
        createDirectories();

        // Initialize RabbitMQ publisher
        RabbitMQPublisher publisher = new RabbitMQPublisher();

        // Start Javalin server
        
        Javalin app = Javalin.create(config -> {
            config.plugins.enableCors(cors -> cors.add(it -> {
                it.anyHost();
            }));
        }).start("0.0.0.0", 7000);

        // Define routes
        app.post("/api/process", ctx -> processImage(ctx, publisher));
        app.get("/api/status/{jobId}", ctx -> getJobStatus(ctx));
    }

    private static void createDirectories() {
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
            System.out.println("Upload directory created: " + UPLOAD_DIR);
        } catch (IOException e) {
            System.err.println("Failed to create upload directory: " + e.getMessage());
        }
    }

    private static void processImage(Context ctx, RabbitMQPublisher publisher) {
        try {
            UploadedFile uploadedFile = ctx.uploadedFile("image");
            if (uploadedFile == null) {
                ctx.status(400).json(Map.of("message", "No image file provided"));
                return;
            }

            String key = ctx.formParam("key");
            if (key == null || key.isEmpty()) {
                ctx.status(400).json(Map.of("message", "No encryption key provided"));
                return;
            }

            String operation = Objects.requireNonNullElse(ctx.formParam("operation"), "encrypt");
            String mode      = Objects.requireNonNullElse(ctx.formParam("mode"),      "ECB");
            

            // Generate a unique job ID
            String jobId = UUID.randomUUID().toString();
            
            // Save the uploaded file
            String originalFilename = uploadedFile.filename();
            String fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String savedFilename = jobId + fileExtension;
            String filePath = "/app/shared/uploads/" + savedFilename;

            
            saveUploadedFile(uploadedFile, filePath);
            
            // Create job message
            Map<String, String> jobMessage = new HashMap<>();
            jobMessage.put("jobId", jobId);
            jobMessage.put("filePath", filePath);
            jobMessage.put("key", key);
            jobMessage.put("operation", operation);
            jobMessage.put("mode", mode);
            
            // Publish message to RabbitMQ
            publisher.publishMessage(jobMessage);
            
            // Store job status
            jobStatuses.put(jobId, new JobStatus(jobId, "processing", null));
            
            // Return job ID to client
            ctx.json(Map.of("jobId", jobId, "message", "Image processing started"));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("message", "Error processing image: " + e.getMessage()));
        }
    }

    private static void saveUploadedFile(UploadedFile uploadedFile, String filePath) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(filePath)) {
            outputStream.write(uploadedFile.content().readAllBytes());
        }
    }

    private static void getJobStatus(Context ctx) {
        String jobId = ctx.pathParam("jobId");
        JobStatus status = jobStatuses.get(jobId);
        
        if (status == null) {
            ctx.status(404).json(Map.of("message", "Job not found"));
            return;
        }
        
        ctx.json(status);
    }

    // Method to update job status (called by a callback or webhook)
    public static void updateJobStatus(String jobId, String status, String imageId) {
        jobStatuses.put(jobId, new JobStatus(jobId, status, imageId));
    }

    static class JobStatus {
        private final String jobId;
        private final String status;
        private final String imageId;

        public JobStatus(String jobId, String status, String imageId) {
            this.jobId = jobId;
            this.status = status;
            this.imageId = imageId;
        }

        public String getJobId() {
            return jobId;
        }

        public String getStatus() {
            return status;
        }

        public String getImageId() {
            return imageId;
        }
    }
}
