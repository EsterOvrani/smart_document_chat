pipeline {
    agent any
    
    environment {
        // Docker Registry
        DOCKER_REGISTRY = 'esterovrani'
        IMAGE_TAG = "${env.BUILD_NUMBER}"
        
        // Temporary build directory
        BUILD_DIR = "${WORKSPACE}/build"
    }
    
    stages {
        stage('🧹 Cleanup') {
            steps {
                script {
                    echo '🧹 Cleaning up old containers and images...'
                    sh '''
                        # שמור את ID של קונטיינר Jenkins
                        JENKINS_CONTAINER_ID=$(hostname)
                        
                        # עצור רק containers של הפרויקט (לא Jenkins!)
                        docker-compose down -v || true
                        
                        # עצור containers חוץ מJenkins
                        docker ps -aq | grep -v ${JENKINS_CONTAINER_ID} | xargs -r docker stop 2>/dev/null || true
                        docker ps -aq | grep -v ${JENKINS_CONTAINER_ID} | xargs -r docker rm -f 2>/dev/null || true
                        
                        # נקה images ישנים (לא containers רצים)
                        docker image prune -a -f || true
                        docker volume prune -f || true
                        
                        echo "✅ Cleanup completed (Jenkins container preserved)"
                    '''
                }
            }
        }
        
        stage('📥 Checkout') {
            steps {
                echo '📥 Checking out code from Git...'
                checkout scm
            }
        }
        
        stage('🔐 Create TEST .env (with TEST_MODE)') {
            steps {
                script {
                    echo '🔐 Creating TEST .env file with TEST_MODE enabled...'
                    
                    withCredentials([
                        string(credentialsId: 'OPENAI_API_KEY', variable: 'OPENAI_API_KEY'),
                        string(credentialsId: 'AWS_ACCESS_KEY_ID', variable: 'AWS_ACCESS_KEY_ID'),
                        string(credentialsId: 'AWS_SECRET_ACCESS_KEY', variable: 'AWS_SECRET_ACCESS_KEY'),
                        string(credentialsId: 'AWS_S3_BUCKET', variable: 'AWS_S3_BUCKET'),
                        string(credentialsId: 'SUPPORT_EMAIL', variable: 'SUPPORT_EMAIL'),
                        string(credentialsId: 'APP_PASSWORD', variable: 'APP_PASSWORD'),
                        string(credentialsId: 'JWT_SECRET_KEY', variable: 'JWT_SECRET_KEY')
                    ]) {
                        sh '''
                            cat > backend/.env << 'EOF'
# ==================== Database ====================
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/smartdocumentchat
SPRING_DATASOURCE_USERNAME=smartdoc_user
SPRING_DATASOURCE_PASSWORD=smartdoc_password

# ==================== JWT ====================
JWT_SECRET_KEY=${JWT_SECRET_KEY}

# ==================== Email ====================
SUPPORT_EMAIL=${SUPPORT_EMAIL}
APP_PASSWORD=${APP_PASSWORD}

# ==================== Frontend ====================
FRONTEND_URL=http://localhost

# ==================== OpenAI ====================
OPENAI_API_KEY=${OPENAI_API_KEY}

# ==================== Qdrant ====================
QDRANT_HOST=qdrant
QDRANT_PORT=6334

# ==================== AWS S3 ====================
AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}
AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}
AWS_REGION=eu-north-1
AWS_S3_BUCKET=${AWS_S3_BUCKET}

# ==================== Redis ====================
REDIS_HOST=redis
REDIS_PORT=6379

# ==================== Test Mode (FOR TESTING ONLY!) ====================
TEST_MODE_ENABLED=true
BYPASS_EMAIL_VERIFICATION=true
EOF
                            echo "✅ TEST .env created with TEST_MODE=true"
                        '''
                    }
                }
            }
        }
        
        stage('🏗️ Build TEST Images') {
            steps {
                echo '🏗️ Building TEST images (with TEST_MODE)...'
                sh '''
                    docker-compose build --no-cache
                    
                    # מצא את השמות האמיתיים
                    BACKEND_IMAGE=$(docker images --format "{{.Repository}}:{{.Tag}}" | grep backend | head -1)
                    FRONTEND_IMAGE=$(docker images --format "{{.Repository}}:{{.Tag}}" | grep frontend | head -1)
                    
                    echo "Found images:"
                    echo "  Backend: $BACKEND_IMAGE"
                    echo "  Frontend: $FRONTEND_IMAGE"
                    
                    # Tag test images
                    docker tag $BACKEND_IMAGE backend:test
                    docker tag $FRONTEND_IMAGE frontend:test
                    
                    # Tag latest
                    docker tag $BACKEND_IMAGE backend:latest
                    docker tag $FRONTEND_IMAGE frontend:latest
                    
                    echo "✅ TEST images built and tagged"
                '''
            }
        }
        
        stage('🚀 Start Test Environment') {
            steps {
                script {
                    echo '🚀 Starting test environment...'
                    sh 'docker-compose up -d'
                    
                    echo '⏳ Waiting for services to be healthy...'
                    sh '''
                        max_attempts=60
                        attempt=0
                        
                        while [ $attempt -lt $max_attempts ]; do
                            if curl -s http://localhost:8080/auth/status > /dev/null 2>&1; then
                                echo "✅ Backend is ready!"
                                break
                            fi
                            
                            attempt=$((attempt + 1))
                            echo "⏳ Attempt $attempt/$max_attempts - waiting..."
                            sleep 5
                        done
                        
                        if [ $attempt -eq $max_attempts ]; then
                            echo "❌ Backend failed to start"
                            docker-compose logs backend
                            exit 1
                        fi
                        
                        # Verify TEST_MODE is active
                        echo "🔍 Verifying TEST_MODE is enabled..."
                        docker-compose exec -T backend env | grep TEST_MODE || echo "⚠️ TEST_MODE not found!"
                    '''
                }
            }
        }
        
        stage('🧪 Run Newman Tests') {
            steps {
                script {
                    cd tests;
                    echo '🧪 Running Newman API tests with TEST_MODE...'
                    sh '''
                        docker run --add-host=host.docker.internal:host-gateway \
                          --network host \
                          -v $(pwd)/tests/newman:/etc/newman \
                          -t postman/newman:alpine \
                          run collections/smart-doc-chat.postman_collection.json \
                          -e environments/test.postman_environment.json \
                          --timeout-request 30000 \
                          --reporters cli,json \
                          --reporter-json-export /etc/newman/newman-report.json \
                          --bail
                          
                        echo "✅ All tests passed with TEST_MODE!"
                    '''
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'tests/newman/newman-report.json', allowEmptyArchive: true
                }
            }
        }
        
        stage('🗑️ Cleanup Test Environment') {
            steps {
                script {
                    echo '🗑️ Stopping and removing test containers...'
                    sh '''
                        docker-compose down -v
                        
                        # Remove test images
                        docker rmi backend:test || true
                        docker rmi frontend:test || true
                        
                        # Remove old backend/frontend images
                        docker rmi backend:latest || true
                        docker rmi frontend:latest || true
                        
                        echo "✅ Test environment cleaned up"
                    '''
                }
            }
        }
        
        stage('🔐 Create PRODUCTION .env (WITHOUT TEST_MODE)') {
            steps {
                script {
                    echo '🔐 Creating PRODUCTION .env file WITHOUT TEST_MODE...'
                    
                    withCredentials([
                        string(credentialsId: 'OPENAI_API_KEY', variable: 'OPENAI_API_KEY'),
                        string(credentialsId: 'AWS_ACCESS_KEY_ID', variable: 'AWS_ACCESS_KEY_ID'),
                        string(credentialsId: 'AWS_SECRET_ACCESS_KEY', variable: 'AWS_SECRET_ACCESS_KEY'),
                        string(credentialsId: 'AWS_S3_BUCKET', variable: 'AWS_S3_BUCKET'),
                        string(credentialsId: 'SUPPORT_EMAIL', variable: 'SUPPORT_EMAIL'),
                        string(credentialsId: 'APP_PASSWORD', variable: 'APP_PASSWORD'),
                        string(credentialsId: 'JWT_SECRET_KEY', variable: 'JWT_SECRET_KEY')
                    ]) {
                        sh '''
                            # 🚨 IMPORTANT: Remove old .env completely!
                            rm -f backend/.env
                            
                            # Create PRODUCTION .env WITHOUT TEST_MODE
                            cat > backend/.env << 'EOF'
# ==================== Database ====================
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/smartdocumentchat
SPRING_DATASOURCE_USERNAME=smartdoc_user
SPRING_DATASOURCE_PASSWORD=smartdoc_password

# ==================== JWT ====================
JWT_SECRET_KEY=${JWT_SECRET_KEY}

# ==================== Email ====================
SUPPORT_EMAIL=${SUPPORT_EMAIL}
APP_PASSWORD=${APP_PASSWORD}

# ==================== Frontend ====================
FRONTEND_URL=http://localhost

# ==================== OpenAI ====================
OPENAI_API_KEY=${OPENAI_API_KEY}

# ==================== Qdrant ====================
QDRANT_HOST=qdrant
QDRANT_PORT=6334

# ==================== AWS S3 ====================
AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}
AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}
AWS_REGION=eu-north-1
AWS_S3_BUCKET=${AWS_S3_BUCKET}

# ==================== Redis ====================
REDIS_HOST=redis
REDIS_PORT=6379

# ⚠️ NO TEST_MODE - This is PRODUCTION!
EOF
                            
                            echo "✅ PRODUCTION .env created WITHOUT TEST_MODE"
                            
                            # Verify TEST_MODE is NOT present
                            echo "🔍 Verifying TEST_MODE is NOT in .env..."
                            if grep -q "TEST_MODE" backend/.env; then
                                echo "❌ ERROR: TEST_MODE found in production .env!"
                                exit 1
                            else
                                echo "✅ Confirmed: No TEST_MODE in production .env"
                            fi
                        '''
                    }
                }
            }
        }
        
        stage('🏗️ Build PRODUCTION Images') {
            steps {
                echo '🏗️ Building PRODUCTION images (WITHOUT TEST_MODE)...'
                sh '''
                    # Force rebuild without cache
                    docker-compose build --no-cache
                    
                    echo "✅ PRODUCTION images built successfully"
                    
                    # Verify the new images exist
                    docker images | grep -E "backend|frontend"
                '''
            }
        }
        
        stage('🔍 Verify Production Images') {
            steps {
                script {
                    echo '🔍 Verifying production images do NOT contain TEST_MODE...'
                    sh '''
                        # Start container temporarily to check
                        docker run --rm backend:latest env > /tmp/backend-env.txt || true
                        
                        if grep -q "TEST_MODE=true" /tmp/backend-env.txt; then
                            echo "❌ CRITICAL ERROR: TEST_MODE found in production image!"
                            exit 1
                        else
                            echo "✅ Confirmed: Production image is clean (no TEST_MODE)"
                        fi
                        
                        rm -f /tmp/backend-env.txt
                    '''
                }
            }
        }
        
        stage('📦 Tag Production Images') {
            steps {
                script {
                    echo '📦 Tagging production images...'
                    sh '''
                        # Tag backend
                        docker tag backend:latest ${DOCKER_REGISTRY}/smart-doc-backend:${IMAGE_TAG}
                        docker tag backend:latest ${DOCKER_REGISTRY}/smart-doc-backend:latest
                        
                        # Tag frontend
                        docker tag frontend:latest ${DOCKER_REGISTRY}/smart-doc-frontend:${IMAGE_TAG}
                        docker tag frontend:latest ${DOCKER_REGISTRY}/smart-doc-frontend:latest
                        
                        echo "✅ Images tagged for production deployment"
                    '''
                }
            }
        }
        
        stage('🚢 Deploy to Registry') {
            when {
                branch 'main'
            }
            steps {
                script {
                    echo '🚢 Pushing PRODUCTION images to registry...'
                    withCredentials([usernamePassword(
                        credentialsId: 'docker-registry-credentials',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )]) {
                        sh '''
                            echo "${DOCKER_PASS}" | docker login ${DOCKER_REGISTRY} -u "${DOCKER_USER}" --password-stdin
                            
                            # Push backend
                            docker push ${DOCKER_REGISTRY}/smart-doc-backend:${IMAGE_TAG}
                            docker push ${DOCKER_REGISTRY}/smart-doc-backend:latest
                            
                            # Push frontend
                            docker push ${DOCKER_REGISTRY}/smart-doc-frontend:${IMAGE_TAG}
                            docker push ${DOCKER_REGISTRY}/smart-doc-frontend:latest
                            
                            docker logout ${DOCKER_REGISTRY}
                            
                            echo "✅ Production images deployed successfully!"
                        '''
                    }
                }
            }
        }
    }
    
    post {
        always {
            script {
                echo '📊 Collecting logs...'
                sh 'docker-compose logs > docker-logs.txt 2>&1 || true'
                archiveArtifacts artifacts: 'docker-logs.txt', allowEmptyArchive: true
            }
        }
        
        success {
            echo '✅ Pipeline completed successfully!'
            echo '📦 Production images are ready for deployment'
        }
        
        failure {
            echo '❌ Pipeline failed!'
            sh 'docker-compose logs --tail=100 || true'
        }
        
        cleanup {
            echo '🧹 Final cleanup...'
            sh '''
                docker-compose down -v || true
                rm -f backend/.env || true
                docker system prune -f || true
            '''
        }
    }
}