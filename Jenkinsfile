pipeline {
    agent any

    environment {
        JAVA_HOME = '/opt/java/openjdk'
        MAVEN_HOME = tool name: 'maven-3.9', type: 'maven'
        PATH = "${MAVEN_HOME}/bin:${JAVA_HOME}/bin:${env.PATH}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                echo "Branch: ${env.GIT_BRANCH}, Commit: ${env.GIT_COMMIT?.take(8)}"
            }
        }

        stage('Compile & Package') {
            steps {
                sh '''
                    export JAVA_HOME=/opt/java/openjdk
                    mvn -DskipTests clean package -q
                    echo "=== 产物列表 ==="
                    find . -name "*-1.0-SNAPSHOT.jar" -exec ls -lh {} \;
                '''
            }
        }

        stage('Unit Test') {
            steps {
                sh '''
                    export JAVA_HOME=/opt/java/openjdk
                    mvn test -q 2>&1 | tail -20
                '''
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Deploy') {
            when {
                branch 'Microservices'
            }
            steps {
                sh '''
                    echo ">>> 停止旧服务..."
                    cd /opt/mycloud && docker-compose down

                    echo ">>> 复制新 jar..."
                    find ${WORKSPACE} -name "*-1.0-SNAPSHOT.jar" -exec cp {} /opt/mycloud/ \;
                    ls -lh /opt/mycloud/*.jar | wc -l

                    echo ">>> 重建镜像..."
                    cd /opt/mycloud && docker-compose build

                    echo ">>> 启动服务..."
                    cd /opt/mycloud && docker-compose up -d

                    echo ">>> 等待启动..."
                    sleep 60

                    echo ">>> 验证注册..."
                    curl -s -XPOST localhost:8090/api/user/login \
                        -H 'Content-Type: application/json' \
                        -d '{"username":"test","password":"Test1234"}' || true

                    echo ">>> 部署完成"
                '''
            }
        }
    }

    post {
        failure {
            echo '构建失败，检查日志'
        }
    }
}