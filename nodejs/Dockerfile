FROM critoma/amd64_u24_noble_ism_security

RUN apt-get update && \
    curl -fsSL https://deb.nodesource.com/setup_18.x | bash - && \
    apt-get install -y nodejs

WORKDIR /app
COPY . .
RUN npm install

CMD ["node", "server.js"]
