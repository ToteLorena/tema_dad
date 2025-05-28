# frontend/Dockerfile
FROM critoma/amd64_u24_noble_ism_security

# Install Node.js
RUN apt-get update &&     apt-get install -y curl &&     curl -fsSL https://deb.nodesource.com/setup_18.x | bash - &&     apt-get install -y nodejs

WORKDIR /app
COPY . .
RUN npm install
CMD ["npm", "run", "dev"]
