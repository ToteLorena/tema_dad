#!/bin/bash

# Wait for RabbitMQ to start
echo "Waiting for RabbitMQ to start..."
until rabbitmqctl status; do
  sleep 2
done

# Enable management plugin
rabbitmq-plugins enable rabbitmq_management

# Create vhost
rabbitmqctl add_vhost /image_processing

# Set permissions
rabbitmqctl set_permissions -p /image_processing admin ".*" ".*" ".*"

# Create exchange
rabbitmqadmin declare exchange name=image_processing type=topic durable=true

# Create queue
rabbitmqadmin declare queue name=image_processing_queue durable=true

# Bind queue to exchange
rabbitmqadmin declare binding source=image_processing destination=image_processing_queue routing_key=image.process

echo "RabbitMQ setup completed"
