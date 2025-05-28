#!/bin/bash

# Start frontend
docker exec -d c01-frontend-backend bash -c "cd /app/frontend && npm start"

# Start backend
docker exec -d c01-frontend-backend bash -c "cd /app/backend && mvn clean package && java -jar target/image-encryption-backend-1.0-SNAPSHOT.jar"

# Start RabbitMQ
docker exec -d c02-rabbitmq bash -c "/app/rabbitmq/setup.sh"

# Start Java MDB
docker exec -d c03-java-mdb bash -c "cd /app/java-mdb && mvn clean package && java -jar target/image-processing-mdb-1.0-SNAPSHOT.jar"

# Start Node.js
docker exec -d c05-nodejs-db bash -c "cd /app/nodejs && npm install && node server.js"

echo "All services started successfully!"
echo "You can now access the application at http://localhost:3000"
