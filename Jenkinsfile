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
        stage('🧹 Cleanup Old Containers') {
            steps {
                script {
                    echo '🧹 Cleaning up old containers and images (preserving Jenkins)...'
                    sh '''
                        # שמור את ID של קונטיינר Jenkins
                        JENKINS_CONTAINER_ID=$(hostname)
                        
                        echo "Jenkins Container ID: $JENKINS_CONTAINER_ID (will be preserved)"
                        
                        # עצור docker-compose containers (אם יש)
                        docker-compose -f docker-compose.test.yml down -v 2>/dev/null || true
                        docker-compose down -v 2>/dev/null || true
                        
                        # עצור כל הcontainers חוץ מJenkins
                        docker ps -aq | grep -v ${JENKINS_CONTAINER_ID} | xargs -r docker stop 2>/dev/null || true
                        docker ps -aq | grep -v ${JENKINS_CONTAINER_ID} | xargs -r docker rm -f 2>/dev/null || true
                        
                        # נקה images ישנים (לא של Jenkins!)
                        docker images --format "{{.Repository}}:{{.Tag}} {{.ID}}" | grep -v jenkins | awk '{print $2}' | xargs -r docker rmi -f 2>/dev/null || true
                        
                        # נקה volumes
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
        
        stage('🔐 Create TEST .env') {
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
                            cat > backend/.env << EOF
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
        
        stage('🏗️ Build TEST Environment') {
            steps {
                echo '🏗️ Building TEST docker-compose images...'
                sh '''
                    # בנה את כל הimages (כולל Newman)
                    docker-compose -f docker-compose.test.yml build --no-cache
                    
                    echo "✅ TEST environment images built"
                '''
            }
        }
        
        stage('🚀 Start TEST Environment & Run Tests') {
            steps {
                script {
                    echo '🚀 Starting TEST environment...'
                    sh '''
                        # הרץ את כל השירותים וחכה שיהיו healthy
                        echo "⏳ Starting services and waiting for health checks..."
                        docker-compose -f docker-compose.test.yml up -d postgres redis qdrant backend frontend nginx
                        
                        # חכה שהבקנד יהיה healthy (docker-compose עושה את זה בשבילנו!)
                        echo "⏳ Waiting for backend to be healthy..."
                        docker-compose -f docker-compose.test.yml up -d --wait backend
                        
                        if [ $? -eq 0 ]; then
                            echo "✅ Backend is healthy and ready!"
                        else
                            echo "❌ Backend health check failed!"
                            docker-compose -f docker-compose.test.yml logs backend
                            exit 1
                        fi
                        
                        echo "🧪 Running Newman tests..."
                        # הרץ את Newman service
                        docker-compose -f docker-compose.test.yml up newman
                        
                        # בדוק exit code של Newman
                        NEWMAN_EXIT_CODE=$(docker inspect newman-tests --format='{{.State.ExitCode}}')
                        
                        echo "Newman exit code: $NEWMAN_EXIT_CODE"
                        
                        if [ "$NEWMAN_EXIT_CODE" != "0" ]; then
                            echo "❌ Newman tests failed!"
                            docker-compose -f docker-compose.test.yml logs newman
                            exit 1
                        fi
                        
                        echo "✅ All Newman tests passed!"
                    '''
                }
            }
            post {
                always {
                    sh 'docker-compose -f docker-compose.test.yml logs newman > newman-output.log 2>&1 || true'
                    archiveArtifacts artifacts: 'newman-output.log', allowEmptyArchive: true
                }
            }
        }
        
        stage('🗑️ Cleanup TEST Environment') {
            steps {
                script {
                    echo '🗑️ Stopping and removing TEST containers...'
                    sh '''
                        # עצור והסר את כל containers של הטסט
                        docker-compose -f docker-compose.test.yml down -v
                        
                        echo "✅ TEST environment cleaned up"
                    '''
                }
            }
        }
        
        stage('🔐 Create PRODUCTION .env') {
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
                            # מחק את .env הישן
                            rm -f backend/.env
                            
                            # צור PRODUCTION .env ללא TEST_MODE
                            cat > backend/.env << EOF
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

EOF
                            
                            echo "✅ PRODUCTION .env created WITHOUT TEST_MODE"
                            
                            # וודא שTEST_MODE לא קיים
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
                    # בנה רק backend ו-frontend (לא nginx או newman)
                    docker-compose build --no-cache backend frontend
                    
                    echo "✅ PRODUCTION images built successfully"
                    
                    # רשימת images
                    docker images | grep -E "backend|frontend"
                '''
            }
        }
        
        stage('🔍 Verify Production Images') {
            steps {
                script {
                    echo '🔍 Verifying production images do NOT contain TEST_MODE...'
                    sh '''
                        # בדוק שbackend image לא מכיל TEST_MODE
                        docker run --rm --entrypoint env backend:latest > /tmp/backend-env.txt || true
                        
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
                            echo "${DOCKER_PASS}" | docker login -u "${DOCKER_USER}" --password-stdin
                            
                            # Push backend
                            docker push ${DOCKER_REGISTRY}/smart-doc-backend:${IMAGE_TAG}
                            docker push ${DOCKER_REGISTRY}/smart-doc-backend:latest
                            
                            # Push frontend
                            docker push ${DOCKER_REGISTRY}/smart-doc-frontend:${IMAGE_TAG}
                            docker push ${DOCKER_REGISTRY}/smart-doc-frontend:latest
                            
                            docker logout
                            
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
            sh '''
                echo "📋 Current containers:"
                docker ps -a
                
                echo "📋 Recent logs:"
                docker-compose -f docker-compose.test.yml logs --tail=100 || true
            '''
        }
        
        cleanup {
            echo '🧹 Final cleanup...'
            sh '''
                # וודא שכל הtest containers נעצרו
                docker-compose -f docker-compose.test.yml down -v 2>/dev/null || true
                docker-compose down -v 2>/dev/null || true
                
                # נקה .env
                rm -f backend/.env || true
                
                # נקה system
                docker system prune -f || true
                
                echo "✅ Final cleanup completed"
            '''
        }
    }
}