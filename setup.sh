#!/bin/bash

# Create directories
mkdir -p frontend/src
mkdir -p backend/src/main/java/com/imageprocessing
mkdir -p rabbitmq
mkdir -p java-mdb/src/main/java/com/imageprocessing/mdb
mkdir -p openmpi
mkdir -p nodejs
mkdir -p shared/uploads
mkdir -p mysql-data
mkdir -p mongo-data

# Make scripts executable
chmod +x rabbitmq/setup.sh
chmod +x openmpi/Makefile

# Build OpenMPI program
cd openmpi
make
cd ..

# Start Docker Compose
docker-compose up -d

echo "Setup completed. The system is now starting..."
echo "Frontend will be available at: http://localhost:3000"
echo "Backend API will be available at: http://localhost:7000"
echo "RabbitMQ Management UI will be available at: http://localhost:15672"
echo "Node.js API will be available at: http://localhost:8080"
