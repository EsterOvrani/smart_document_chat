#!/bin/bash

# ==================== התקנת מערכת ====================
echo "🔄 Updating system..."
apt-get update
apt-get upgrade -y

# ==================== התקנת Docker ====================
echo "🐳 Installing Docker..."
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# הוספת משתמש ubuntu לקבוצת docker
usermod -aG docker ubuntu

# ==================== התקנת Docker Compose ====================
echo "🔧 Installing Docker Compose..."
apt-get install -y docker-compose

# ==================== התקנת כלים נוספים ====================
echo "📦 Installing additional tools..."
apt-get install -y git curl wget nano htop

# ==================== יצירת תקיית הפרויקט ====================
echo "📁 Creating project directory..."
PROJECT_DIR="/home/ubuntu/smart-doc-chat"
mkdir -p $PROJECT_DIR
cd $PROJECT_DIR

# ==================== יצירת תקיית nginx ====================
echo "📂 Creating nginx directory..."
mkdir -p nginx

# ==================== יצירת nginx/Dockerfile ====================
echo "📄 Creating nginx/Dockerfile..."
cat > nginx/Dockerfile << 'EOF'
FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
EOF

# ==================== יצירת nginx/nginx.conf ====================
echo "📄 Creating nginx/nginx.conf..."
cat > nginx/nginx.conf << 'EOF'
server {
    listen 80;
    server_name localhost;

    # הגדלת timeout
    proxy_read_timeout 300s;
    proxy_connect_timeout 300s;
    proxy_send_timeout 300s;
    client_max_body_size 50M;

    # ==================== Auth - הסר /api מהנתיב! ====================
    location ~ ^/api/auth/(.*)$ {
        # ✅ הסר את /api ושלח רק /auth/...
        proxy_pass http://backend:8080/auth/$1$is_args$args;
        proxy_http_version 1.1;
        
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # CORS
        add_header 'Access-Control-Allow-Origin' '*' always;
        add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS, PATCH' always;
        add_header 'Access-Control-Allow-Headers' 'Origin, Content-Type, Accept, Authorization' always;
        
        if ($request_method = 'OPTIONS') {
            add_header 'Access-Control-Allow-Origin' '*';
            add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS, PATCH';
            add_header 'Access-Control-Allow-Headers' 'Origin, Content-Type, Accept, Authorization';
            add_header 'Content-Length' 0;
            return 204;
        }
    }

    # ==================== API - שמור /api בנתיב ====================
    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_http_version 1.1;
        
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # CORS
        add_header 'Access-Control-Allow-Origin' '*' always;
        add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS, PATCH' always;
        add_header 'Access-Control-Allow-Headers' 'Origin, Content-Type, Accept, Authorization' always;
        
        if ($request_method = 'OPTIONS') {
            add_header 'Access-Control-Allow-Origin' '*';
            add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS, PATCH';
            add_header 'Access-Control-Allow-Headers' 'Origin, Content-Type, Accept, Authorization';
            add_header 'Content-Length' 0;
            return 204;
        }
    }

    # ==================== Frontend ====================
    location / {
        proxy_pass http://frontend:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }
}
EOF

# ==================== יצירת docker-compose.yml ====================
echo "📄 Creating docker-compose.yml..."
cat > docker-compose.yml << 'EOF'
version: '3.8'
services:
  # ==================== PostgreSQL ====================
  postgres:
    image: postgres:15-alpine
    container_name: postgres-smart-doc-chat
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: smartdocumentchat
      POSTGRES_USER: smartdoc_user
      POSTGRES_PASSWORD: smartdoc_password
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U smartdoc_user -d smartdocumentchat"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - app-network
    restart: unless-stopped

  # ==================== Redis Cache ====================
  redis:
    image: redis:7-alpine
    container_name: redis-smart-doc-chat
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    command: redis-server --appendonly yes --maxmemory 256mb --maxmemory-policy allkeys-lru
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5
    networks:
      - app-network
    restart: unless-stopped

  # ==================== Qdrant ====================
  qdrant:
    image: qdrant/qdrant:latest
    container_name: qdrant-smart-doc-chat
    ports:
      - "6333:6333"
      - "6334:6334"
    volumes:
      - qdrant_storage:/qdrant/storage
    environment:
      - QDRANT__SERVICE__HTTP_PORT=6333
      - QDRANT__SERVICE__GRPC_PORT=6334
    networks:
      - app-network
    restart: unless-stopped

  # ==================== Backend (מ-Docker Hub) ====================
  backend:
    image: esterovrani/smart-doc-chat-backend:latest
    container_name: spring-backend
    env_file:
      - ./backend/.env
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/smartdocumentchat
      SPRING_DATASOURCE_USERNAME: smartdoc_user
      SPRING_DATASOURCE_PASSWORD: smartdoc_password
      QDRANT_HOST: qdrant
      QDRANT_PORT: 6334
      REDIS_HOST: redis
      REDIS_PORT: 6379
      FRONTEND_URL: http://localhost
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      qdrant:
        condition: service_started
    networks:
      - app-network
    restart: unless-stopped

  # ==================== Frontend (מ-Docker Hub) ====================
  frontend:
    image: esterovrani/smart-doc-chat-frontend:latest
    container_name: react-frontend
    ports:
      - "3000:3000"
    depends_on:
      - backend
    networks:
      - app-network
    restart: unless-stopped

  # ==================== Nginx (Reverse Proxy) ====================
  nginx:
    build:
      context: ./nginx
      dockerfile: Dockerfile
    container_name: nginx-proxy
    ports:
      - "80:80"
    depends_on:
      - backend
      - frontend
    networks:
      - app-network
    restart: unless-stopped

# ==================== Networks ====================
networks:
  app-network:
    driver: bridge

# ==================== Volumes ====================
volumes:
  postgres_data:
    driver: local
  redis_data:
    driver: local
  qdrant_storage:
    driver: local
EOF

# ==================== יצירת תקיית backend ל-.env ====================
echo "📂 Creating backend directory..."
mkdir -p backend

# ==================== יצירת קובץ .env לבקאנד ====================
echo "📄 Creating backend/.env file..."
cat > backend/.env << 'EOF'
# ==================== Database ====================
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/smartdocumentchat
SPRING_DATASOURCE_USERNAME=smartdoc_user
SPRING_DATASOURCE_PASSWORD=smartdoc_password

# ==================== JWT ====================
JWT_SECRET_KEY=ENTER YOUR JWT SECRET KEY HERE

# ==================== Email ====================
SUPPORT_EMAIL=ENTER YOUR SUPPORT EMAIL HERE
APP_PASSWORD=ENTER YOUR EMAIL APP PASSWORD HERE

# ==================== Frontend ====================
FRONTEND_URL=http://localhost:3000

# ==================== OpenAI ====================
OPENAI_API_KEY=ENTER YOUR OPENAI API KEY HERE

# ==================== Qdrant ====================
QDRANT_HOST=localhost
QDRANT_PORT=6334
# ==================== AWS S3 ====================
AWS_ACCESS_KEY_ID=ENTER YOUR AWS ACCESS KEY ID HERE
AWS_SECRET_ACCESS_KEY=ENTER YOUR AWS SECRET ACCESS KEY HERE
AWS_REGION=eu-north-1
AWS_S3_BUCKET=smart-document-chat
EOF

# ==================== שינוי הרשאות ====================
echo "🔐 Setting permissions..."
chown -R ubuntu:ubuntu $PROJECT_DIR
chmod -R 755 $PROJECT_DIR

# ==================== המתנה ל-Docker ====================
echo "⏳ Waiting for Docker to be ready..."
sleep 10

# ==================== הורדת התמונות מראש ====================
echo "📥 Pulling Docker images..."
docker pull postgres:15-alpine
docker pull redis:7-alpine
docker pull qdrant/qdrant:latest
docker pull esterovrani/smart-doc-chat-backend:latest
docker pull esterovrani/smart-doc-chat-frontend:latest
docker pull nginx:alpine

# ==================== הרצת Docker Compose ====================
echo "🚀 Starting Docker Compose..."
cd $PROJECT_DIR
docker-compose up -d

# ==================== סטטוס ====================
echo "✅ Waiting for services to start..."
sleep 30
docker-compose ps

# ==================== יצירת קובץ סיום ====================
echo "✅ Setup complete! All services are running." > /home/ubuntu/setup-complete.txt
echo "📊 Project location: $PROJECT_DIR" >> /home/ubuntu/setup-complete.txt
echo "🌐 Access your app at: http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4)" >> /home/ubuntu/setup-complete.txt
chown ubuntu:ubuntu /home/ubuntu/setup-complete.txt

echo "🎉 DEPLOYMENT COMPLETE!"