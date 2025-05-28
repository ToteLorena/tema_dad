# RabbitMQ broker based on critoma base image
FROM critoma/amd64_u24_noble_ism_security

RUN apt-get update && apt-get install -y rabbitmq-server &&     rm -rf /var/lib/apt/lists/*

EXPOSE 5672 15672
CMD ["rabbitmq-server"]
