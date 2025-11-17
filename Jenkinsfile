pipeline {
    agent any
    
    environment {
        // הוספה חדשה - תקן את בעיית ה-API version
        DOCKER_API_VERSION = '1.41'

        // Docker Registry
        DOCKER_REGISTRY = 'esterovrani'
        
        // Git commit message (מנוקה מתווים מיוחדים)
        GIT_COMMIT_MESSAGE = sh(
            script: "git log -1 --pretty=format:'%s' | sed 's/[^a-zA-Z0-9]/-/g' | tr '[:upper:]' '[:lower:]' | sed 's/--*/-/g' | sed 's/^-//' | sed 's/-\$//' | cut -c1-50",
            returnStdout: true
        ).trim()
        
        // Git commit hash קצר (לשילוב)
        GIT_COMMIT_SHORT = sh(
            script: "git rev-parse --short=7 HEAD",
            returnStdout: true
        ).trim()
        
        // Tag format: commit-message-hash (לייחודיות)
        IMAGE_TAG = "${GIT_COMMIT_MESSAGE}-${GIT_COMMIT_SHORT}"
        
        // Temporary build directory
        BUILD_DIR = "${WORKSPACE}/build"
    }
    
    stages {
        stage('📋 Display Build Info') {
            steps {
                script {
                    echo '📋 ====== BUILD INFORMATION ======'
                    sh '''
                        echo "Git Commit Message: $(git log -1 --pretty=format:'%s')"
                        echo "Git Commit Hash:    ${GIT_COMMIT_SHORT}"
                        echo "Sanitized Message:  ${GIT_COMMIT_MESSAGE}"
                        echo "Image Tag:          ${IMAGE_TAG}"
                        echo "Git Branch:         $(git rev-parse --abbrev-ref HEAD)"
                        echo "Git Author:         $(git log -1 --pretty=format:'%an')"
                        echo "Docker Registry:    ${DOCKER_REGISTRY}"
                        echo "=================================="
                    '''
                }
            }
        }
        
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
                        docker images --format "{{.Repository}}:{{.Tag}} {{.ID}}" | grep -v "jenkins-jenkins" | awk '{print $2}' | xargs -r docker rmi -f 2>/dev/null || true
                        
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
                    echo '🔐 Creating GLOBAL TEST .env file with TEST_MODE enabled...'
                    
                    withCredentials([
                        string(credentialsId: 'OPENAI_API_KEY', variable: 'OPENAI_API_KEY'),
                        string(credentialsId: 'AWS_ACCESS_KEY_ID', variable: 'AWS_ACCESS_KEY_ID'),
                        string(credentialsId: 'AWS_SECRET_ACCESS_KEY', variable: 'AWS_SECRET_ACCESS_KEY'),
                        string(credentialsId: 'AWS_S3_BUCKET', variable: 'AWS_S3_BUCKET'),
                        string(credentialsId: 'MAIL_USERNAME', variable: 'MAIL_USERNAME'),
                        string(credentialsId: 'MAIL_PASSWORD', variable: 'MAIL_PASSWORD'),
                        string(credentialsId: 'JWT_SECRET_KEY', variable: 'JWT_SECRET_KEY'),
                        string(credentialsId: 'GOOGLE_CLIENT_ID', variable: 'GOOGLE_CLIENT_ID'),
                        string(credentialsId: 'GOOGLE_CLIENT_SECRET', variable: 'GOOGLE_CLIENT_SECRET')
                    ]) {
                        sh '''
                            # יצירת .env גלובלי בתיקייה הראשית
                            cat > .env << EOF
# ==================== Shared Infrastructure ====================
# ==================== Database Configuration ====================
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
POSTGRES_DB=smartdocumentchat
POSTGRES_USER=smartdoc_user
POSTGRES_PASSWORD=smartdoc_postgres_password

# ==================== Redis Configuration ====================
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=

# ==================== Qdrant Configuration ====================
QDRANT_HOST=qdrant
QDRANT_REST_PORT=6333
QDRANT_GRPC_PORT=6334
QDRANT_API_KEY=

# ==================== Ports ====================
NGINX_PORT=80

# ==================== Backend-Specific Configuration ====================
# ==================== Server ====================
SERVER_PORT=8080

# ==================== Security - JWT ====================
JWT_SECRET_KEY=${JWT_SECRET_KEY}
JWT_EXPIRATION_MS=3600000

# ==================== Email Configuration ====================
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=${MAIL_USERNAME}
MAIL_PASSWORD=${MAIL_PASSWORD}

# ==================== OpenAI ====================
OPENAI_API_KEY=${OPENAI_API_KEY}

# ==================== AWS S3 ====================
AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}
AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}
AWS_REGION=eu-north-1
AWS_S3_BUCKET=${AWS_S3_BUCKET}

# ==================== Google OAuth2 ====================
GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}
GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET}

# ==================== Frontend URL ====================
FRONTEND_URL=http://localhost

# ==================== Test Mode (FOR TESTING ONLY!) ====================
TEST_MODE_ENABLED=true
BYPASS_EMAIL_VERIFICATION=true
FIXED_VERIFICATION_CODE=999999

# ==================== Qdrant Embeddings ====================
QDRANT_DIMENSION=3072
QDRANT_DISTANCE=Cosine
QDRANT_DEFAULT_MAX_RESULTS=5
QDRANT_DEFAULT_MIN_SCORE=0.75
QDRANT_HNSW_M=16
QDRANT_HNSW_EF_CONSTRUCT=200
QDRANT_HNSW_EF=128

# ==================== Frontend-Specific Configuration ====================
REACT_APP_GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}
EOF
                            echo "✅ GLOBAL TEST .env created in project root with TEST_MODE=true"
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
                        # עצור והסר את כל containers של הטסט כולל volumes
                        docker-compose -f docker-compose.test.yml down -v
                        
                        echo "✅ TEST environment cleaned up"
                    '''
                }
            }
        }
        
        stage('🔐 Create PRODUCTION .env') {
            steps {
                script {
                    echo '🔐 Creating GLOBAL PRODUCTION .env file WITHOUT TEST_MODE...'
                    
                    withCredentials([
                        string(credentialsId: 'OPENAI_API_KEY', variable: 'OPENAI_API_KEY'),
                        string(credentialsId: 'AWS_ACCESS_KEY_ID', variable: 'AWS_ACCESS_KEY_ID'),
                        string(credentialsId: 'AWS_SECRET_ACCESS_KEY', variable: 'AWS_SECRET_ACCESS_KEY'),
                        string(credentialsId: 'AWS_S3_BUCKET', variable: 'AWS_S3_BUCKET'),
                        string(credentialsId: 'MAIL_USERNAME', variable: 'MAIL_USERNAME'),
                        string(credentialsId: 'MAIL_PASSWORD', variable: 'MAIL_PASSWORD'),
                        string(credentialsId: 'JWT_SECRET_KEY', variable: 'JWT_SECRET_KEY'),
                        string(credentialsId: 'GOOGLE_CLIENT_ID', variable: 'GOOGLE_CLIENT_ID'),
                        string(credentialsId: 'GOOGLE_CLIENT_SECRET', variable: 'GOOGLE_CLIENT_SECRET')
                    ]) {
                        sh '''
                            # מחק את .env הישן
                            rm -f .env
                            
                            # צור PRODUCTION .env גלובלי ללא TEST_MODE
                            cat > .env << EOF
# ==================== Shared Infrastructure ====================
# ==================== Database Configuration ====================
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
POSTGRES_DB=smartdocumentchat
POSTGRES_USER=smartdoc_user
POSTGRES_PASSWORD=smartdoc_postgres_password

# ==================== Redis Configuration ====================
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=

# ==================== Qdrant Configuration ====================
QDRANT_HOST=qdrant
QDRANT_REST_PORT=6333
QDRANT_GRPC_PORT=6334
QDRANT_API_KEY=

# ==================== Ports ====================
NGINX_PORT=80

# ==================== Backend-Specific Configuration ====================
# ==================== Server ====================
SERVER_PORT=8080

# ==================== Security - JWT ====================
JWT_SECRET_KEY=${JWT_SECRET_KEY}
JWT_EXPIRATION_MS=3600000

# ==================== Email Configuration ====================
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=${MAIL_USERNAME}
MAIL_PASSWORD=${MAIL_PASSWORD}

# ==================== OpenAI ====================
OPENAI_API_KEY=${OPENAI_API_KEY}

# ==================== AWS S3 ====================
AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}
AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}
AWS_REGION=eu-north-1
AWS_S3_BUCKET=${AWS_S3_BUCKET}

# ==================== Google OAuth2 ====================
GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}
GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET}

# ==================== Frontend URL ====================
FRONTEND_URL=http://localhost

# ==================== Qdrant Embeddings ====================
QDRANT_DIMENSION=3072
QDRANT_DISTANCE=Cosine
QDRANT_DEFAULT_MAX_RESULTS=5
QDRANT_DEFAULT_MIN_SCORE=0.75
QDRANT_HNSW_M=16
QDRANT_HNSW_EF_CONSTRUCT=200
QDRANT_HNSW_EF=128

# ==================== Frontend-Specific Configuration ====================
REACT_APP_GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}
EOF
                            
                            echo "✅ GLOBAL PRODUCTION .env created in project root WITHOUT TEST_MODE"
                            
                            # וודא שTEST_MODE מוגדר כ-false
                            if grep -q "TEST_MODE_ENABLED=true" .env; then
                                echo "❌ ERROR: TEST_MODE_ENABLED=true found in production .env!"
                                exit 1
                            else
                                echo "✅ Confirmed: TEST_MODE_ENABLED=false in production .env"
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
                    echo '🔍 Verifying production images do NOT contain TEST_MODE=true...'
                    sh '''
                        # בדוק שbackend-prod image לא מכיל TEST_MODE=true
                        docker run --rm --entrypoint env backend-prod:latest > /tmp/backend-env.txt || true
                        
                        if grep -q "TEST_MODE_ENABLED=true" /tmp/backend-env.txt; then
                            echo "❌ CRITICAL ERROR: TEST_MODE_ENABLED=true found in production image!"
                            exit 1
                        else
                            echo "✅ Confirmed: Production image is clean (TEST_MODE_ENABLED=false)"
                        fi
                        
                        rm -f /tmp/backend-env.txt
                    '''
                }
            }
        }
        
        stage('📦 Tag Production Images') {
            steps {
                script {
                    echo '📦 Tagging production images with Git commit message...'
                    sh '''
                        echo "Original commit message: $(git log -1 --pretty=format:'%s')"
                        echo "Sanitized tag: ${IMAGE_TAG}"
                        
                        # Tag backend with commit message and latest
                        docker tag backend-prod:latest ${DOCKER_REGISTRY}/smart-doc-chat-backend:${IMAGE_TAG}
                        docker tag backend-prod:latest ${DOCKER_REGISTRY}/smart-doc-chat-backend:latest
                        
                        # Tag frontend with commit message and latest
                        docker tag frontend-prod:latest ${DOCKER_REGISTRY}/smart-doc-chat-frontend:${IMAGE_TAG}
                        docker tag frontend-prod:latest ${DOCKER_REGISTRY}/smart-doc-chat-frontend:latest
                        
                        echo "✅ Images tagged for production deployment"
                        echo "   Backend:  ${DOCKER_REGISTRY}/smart-doc-chat-backend:${IMAGE_TAG}"
                        echo "   Frontend: ${DOCKER_REGISTRY}/smart-doc-chat-frontend:${IMAGE_TAG}"
                        echo "   (Also tagged as 'latest')"
                    '''
                }
            }
        }
        
        stage('🚢 Deploy to Registry') {
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
                            
                            # Push backend with commit message tag and latest
                            echo "📤 Pushing backend with tag: ${IMAGE_TAG}..."
                            docker push ${DOCKER_REGISTRY}/smart-doc-chat-backend:${IMAGE_TAG}
                            docker push ${DOCKER_REGISTRY}/smart-doc-chat-backend:latest
                            
                            # Push frontend with commit message tag and latest
                            echo "📤 Pushing frontend with tag: ${IMAGE_TAG}..."
                            docker push ${DOCKER_REGISTRY}/smart-doc-chat-frontend:${IMAGE_TAG}
                            docker push ${DOCKER_REGISTRY}/smart-doc-chat-frontend:latest
                            
                            docker logout
                            
                            echo "✅ Production images deployed successfully!"
                            echo ""
                            echo "📦 DEPLOYED IMAGES:"
                            echo "   Backend:  ${DOCKER_REGISTRY}/smart-doc-chat-backend:${IMAGE_TAG}"
                            echo "   Backend:  ${DOCKER_REGISTRY}/smart-doc-chat-backend:latest"
                            echo "   Frontend: ${DOCKER_REGISTRY}/smart-doc-chat-frontend:${IMAGE_TAG}"
                            echo "   Frontend: ${DOCKER_REGISTRY}/smart-doc-chat-frontend:latest"
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
            script {
                echo '✅ ====== PIPELINE SUCCESS ======'
                sh '''
                    echo "📦 Production images deployed!"
                    echo ""
                    echo "📝 Git Commit Info:"
                    echo "   Message: $(git log -1 --pretty=format:'%s')"
                    echo "   Author:  $(git log -1 --pretty=format:'%an')"
                    echo "   Hash:    ${GIT_COMMIT_SHORT}"
                    echo ""
                    echo "🎯 Image Tag: ${IMAGE_TAG}"
                    echo ""
                    echo "🐳 Deployed Images:"
                    echo "   ${DOCKER_REGISTRY}/smart-doc-chat-backend:${IMAGE_TAG}"
                    echo "   ${DOCKER_REGISTRY}/smart-doc-chat-frontend:${IMAGE_TAG}"
                    echo ""
                    echo "✅ Pipeline completed successfully!"
                '''
            }
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
            echo '🧹 ====== FINAL DEEP CLEANUP ======'
            sh '''
                echo "🛑 Step 1: Stopping all Docker Compose services with volumes..."
                docker-compose -f docker-compose.test.yml down -v 2>/dev/null || true
                docker-compose down -v 2>/dev/null || true
                
                echo "🗑️ Step 2: Removing all project images (preserving jenkins-jenkins)..."
                # מחק את כל ה-images של הפרויקט (לא jenkins-jenkins!)
                docker images --format "{{.Repository}}:{{.Tag}}" | grep -v "jenkins-jenkins" | grep -E "backend|frontend|postgres|redis|qdrant|nginx|newman" | xargs -r docker rmi -f 2>/dev/null || true
                
                # מחק dangling images (לא jenkins-jenkins!)
                docker images -f "dangling=true" -q | xargs -r docker rmi -f 2>/dev/null || true
                
                echo "🧹 Step 3: Cleaning Docker builder cache..."
                docker builder prune -a -f
                
                echo "🗑️ Step 4: Removing unused volumes..."
                docker volume prune -f
                
                echo "🗑️ Step 5: Removing unused networks..."
                docker network prune -f
                
                echo "🧹 Step 6: Final system cleanup..."
                docker system prune -f
                
                echo "🗂️ Step 7: Removing .env file..."
                rm -f .env || true
                
                echo ""
                echo "📊 ====== CLEANUP SUMMARY ======"
                echo "Remaining containers:"
                docker ps -a
                echo ""
                echo "Remaining images:"
                docker images
                echo ""
                echo "Remaining volumes:"
                docker volume ls
                echo ""
                echo "✅ DEEP CLEANUP COMPLETED (jenkins-jenkins preserved)"
            '''
        }
    }
}