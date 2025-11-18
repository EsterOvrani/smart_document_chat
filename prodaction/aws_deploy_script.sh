#!/bin/bash

# ==================== הגדרות ====================
DOMAIN="smart-document-chat.com"
EMAIL="ester.ovrani@gmail.com"
PROJECT_DIR="/home/ubuntu/smart-doc-chat"

# ==================== התקנת מערכת ====================
echo "🔄 Updating system..."
apt-get update -y
apt-get upgrade -y

# ==================== התקנת Docker ====================
echo "🐳 Installing Docker..."
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh
rm get-docker.sh

# הוספת משתמש ubuntu לקבוצת docker
usermod -aG docker ubuntu

# ==================== התקנת Docker Compose ====================
echo "🔧 Installing Docker Compose..."
apt-get install -y docker-compose

# ==================== התקנת כלים נוספים ====================
echo "📦 Installing additional tools..."
apt-get install -y git curl wget nano htop

# ==================== התקנת Certbot ====================
echo "🔒 Installing Certbot..."
apt-get install -y certbot python3-certbot-nginx

# ==================== יצירת תקיית הפרויקט ====================
echo "📁 Creating project directory..."
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
EXPOSE 80 443
CMD ["nginx", "-g", "daemon off;"]
EOF

# ==================== יצירת nginx/nginx.conf ====================
echo "📄 Creating nginx/nginx.conf..."
cat > nginx/nginx.conf << 'NGINXCONF'
# Redirect HTTP to HTTPS
server {
    listen 80;
    server_name smart-document-chat.com;
    return 301 https://$server_name$request_uri;
}

# HTTPS Server
server {
    listen 443 ssl;
    server_name smart-document-chat.com;

    # SSL Certificates
    ssl_certificate /etc/letsencrypt/live/smart-document-chat.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/smart-document-chat.com/privkey.pem;
    
    # SSL Settings
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    # Timeout
    proxy_read_timeout 300s;
    proxy_connect_timeout 300s;
    proxy_send_timeout 300s;
    client_max_body_size 50M;

    # Auth
    location ~ ^/api/auth/(.*)$ {
        proxy_pass http://backend:8080/auth/$1$is_args$args;
        proxy_http_version 1.1;
        
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        add_header 'Access-Control-Allow-Origin' '*' always;
        add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS, PATCH' always;
        add_header 'Access-Control-Allow-Headers' 'Origin, Content-Type, Accept, Authorization' always;
        
        if ($request_method = 'OPTIONS') {
            return 204;
        }
    }

    # API
    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_http_version 1.1;
        
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        add_header 'Access-Control-Allow-Origin' '*' always;
        add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS, PATCH' always;
        add_header 'Access-Control-Allow-Headers' 'Origin, Content-Type, Accept, Authorization' always;
        
        if ($request_method = 'OPTIONS') {
            return 204;
        }
    }

    # Frontend
    location / {
        proxy_pass http://frontend:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }
}
NGINXCONF

# ==================== יצירת docker-compose.yml ====================
echo "📄 Creating docker-compose.yml..."
cat > docker-compose.yml << 'DOCKERCOMPOSE'
version: '3.8'

services:
  # ==================== PostgreSQL ====================
  postgres:
    image: postgres:15-alpine
    container_name: postgres-smart-doc-chat
    ports:
      - "${POSTGRES_PORT:-5432}:5432"
    env_file:
      - .env
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
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

  # ==================== Redis ====================
  redis:
    image: redis:7-alpine
    container_name: redis-smart-doc-chat
    ports:
      - "${REDIS_PORT:-6379}:6379"
    env_file:
      - .env
    command: >
      redis-server
      --requirepass ${REDIS_PASSWORD}
      --appendonly yes
      --maxmemory 256mb
      --maxmemory-policy allkeys-lru
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "--raw", "incr", "ping"]
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
      - "${QDRANT_REST_PORT:-6333}:6333"
      - "${QDRANT_GRPC_PORT:-6334}:6334"
    env_file:
      - .env
    volumes:
      - qdrant_storage:/qdrant/storage
    environment:
      - QDRANT__SERVICE__HTTP_PORT=6333
      - QDRANT__SERVICE__GRPC_PORT=6334
    networks:
      - app-network
    restart: unless-stopped

  # ==================== Backend ====================
  backend:
    image: esterovrani/smart-doc-chat-backend:latest
    container_name: spring-backend
    ports:
      - "${SERVER_PORT:-8080}:8080"
    env_file:            
      - .env 
    environment:
      POSTGRES_HOST: postgres
      REDIS_HOST: redis
      QDRANT_HOST: qdrant
      FRONTEND_URL: https://smart-document-chat.com
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

  # ==================== Frontend ====================
  frontend:
    image: esterovrani/smart-doc-chat-frontend:latest
    container_name: react-frontend
    ports:
      - "3000:3000"
    env_file:
      - .env
    depends_on:
      - backend
    networks:
      - app-network
    restart: unless-stopped
     
  # ==================== Nginx ====================
  nginx:
    build:
      context: ./nginx
      dockerfile: Dockerfile
    container_name: nginx-proxy
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - /etc/letsencrypt:/etc/letsencrypt:ro
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
DOCKERCOMPOSE

# ==================== יצירת קובץ .env.example ====================
echo "📄 Creating .env.example file..."
cat > .env.example << 'ENVEXAMPLE'
# ==================== Server ====================
SERVER_PORT=8080

# ==================== Security - JWT ====================
JWT_SECRET_KEY=your-super-secret-jwt-key-change-this
JWT_EXPIRATION_MS=3600000

# ==================== Email Configuration ====================
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password

# ==================== OpenAI ====================
OPENAI_API_KEY=sk-your-openai-api-key

# ==================== AWS S3 ====================
AWS_ACCESS_KEY_ID=your-aws-access-key
AWS_SECRET_ACCESS_KEY=your-aws-secret-key
AWS_REGION=eu-west-1
AWS_S3_BUCKET=your-bucket-name

# ==================== Google OAuth2 ====================
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret

# ==================== Frontend URL ====================
FRONTEND_URL=https://smart-document-chat.com

# ==================== Database ====================
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=smartdocumentchat
POSTGRES_USER=smartdoc_user
POSTGRES_PASSWORD=change-this-password

# ==================== Redis ====================
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=change-this-password

# ==================== Qdrant ====================
QDRANT_HOST=localhost
QDRANT_REST_PORT=6333
QDRANT_GRPC_PORT=6334
QDRANT_DIMENSION=3072
QDRANT_DISTANCE=Cosine
QDRANT_DEFAULT_MAX_RESULTS=5
QDRANT_DEFAULT_MIN_SCORE=0.75
QDRANT_HNSW_M=16
QDRANT_HNSW_EF_CONSTRUCT=200
QDRANT_HNSW_EF=128

# ==================== Frontend ====================
NGINX_PORT=80
REACT_APP_GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
ENVEXAMPLE

# ==================== הודעה למשתמש ====================
echo ""
echo "⚠️  IMPORTANT: Please create .env file with your credentials"
echo "You can copy .env.example and fill in your actual values:"
echo "cp .env.example .env"
echo "nano .env"
echo ""
read -p "Press Enter after you've created and filled the .env file..."

# ==================== בדיקה אם קובץ .env קיים ====================
if [ ! -f ".env" ]; then
    echo "❌ ERROR: .env file not found!"
    echo "Please create .env file before continuing."
    exit 1
fi

# ==================== שינוי הרשאות ====================
echo "🔐 Setting permissions..."
chown -R ubuntu:ubuntu $PROJECT_DIR
chmod -R 755 $PROJECT_DIR

# ==================== הורדת התמונות מראש ====================
echo "📥 Pulling Docker images..."
docker pull postgres:15-alpine
docker pull redis:7-alpine
docker pull qdrant/qdrant:latest
docker pull esterovrani/smart-doc-chat-backend:latest
docker pull esterovrani/smart-doc-chat-frontend:latest
docker pull nginx:alpine

# ==================== קבלת תעודת SSL ====================
echo "🔒 Obtaining SSL certificate..."
certbot certonly --standalone --non-interactive --agree-tos --email $EMAIL -d $DOMAIN

# ✅ בדיקה אם התעודה נוצרה בהצלחה
if [ ! -f "/etc/letsencrypt/live/$DOMAIN/fullchain.pem" ]; then
    echo "❌ ERROR: Failed to obtain SSL certificate!"
    exit 1
fi

echo "✅ SSL certificate obtained successfully!"

# ==================== הרצת Docker Compose ====================
echo "🚀 Starting Docker Compose..."
cd $PROJECT_DIR
docker-compose up -d --build

# ==================== המתנה לשירותים ====================
echo "⏳ Waiting for services to start..."
sleep 30

# ==================== סטטוס ====================
echo "📊 Checking services status..."
docker-compose ps

# ==================== הגדרת חידוש אוטומטי של SSL ====================
echo "🔄 Setting up automatic SSL renewal..."
(crontab -l 2>/dev/null; echo "0 3 * * * certbot renew --quiet && docker-compose -f $PROJECT_DIR/docker-compose.yml restart nginx") | crontab -

# ==================== יצירת קובץ סיום ====================
echo "✅ Setup complete! All services are running." > /home/ubuntu/setup-complete.txt
echo "📊 Project location: $PROJECT_DIR" >> /home/ubuntu/setup-complete.txt
echo "🌐 Access your app at: https://$DOMAIN" >> /home/ubuntu/setup-complete.txt
echo "🔒 SSL certificate will auto-renew every 90 days" >> /home/ubuntu/setup-complete.txt
chown ubuntu:ubuntu /home/ubuntu/setup-complete.txt

# ==================== הצגת סיכום ====================
echo ""
echo "🎉 ==============================================="
echo "🎉 DEPLOYMENT COMPLETE!"
echo "🎉 ==============================================="
echo ""
echo "✅ Docker containers are running"
echo "✅ SSL certificate installed and configured"
echo "✅ Auto-renewal scheduled"
echo ""
echo "🌐 Your application is available at:"
echo "   https://$DOMAIN"
echo ""
echo "📝 Next steps:"
echo "   1. Update Google OAuth Console with:"
echo "      - Authorized JavaScript origins: https://$DOMAIN"
echo "      - Authorized redirect URIs: https://$DOMAIN/callback"
echo "   2. Test your application"
echo "   3. Monitor logs: docker-compose logs -f"
echo ""