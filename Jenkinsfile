pipeline {

    agent { label "${AgentName}" }

    options {
      timestamps()
    }

    stages {


        stage('Build MySQL DB image and push to the registry') {
            steps {
                sh """
                    ls -l
                    docker pull mariadb:latest
                    docker build --build-arg dummy_pass=$dummy_pass -t ${IMAGE} .
                    docker tag ${IMAGE} ${LIMAGE}:${VERSION}
                    docker push ${LIMAGE}:${VERSION}
                """
            }
        }

        stage('Deploy kubectl and apply kubectl-config to the agent') {
            steps {
                sh """
                    hostname
                    sudo apt-get update && sudo apt-get install -y kubectl
                    mkdir -p ~/.kube/
                    scp "${KubeConfigSafe}":~/.kube/config ~/.kube/
                    kubectl get nodes
                """
            }
        }

        stage('Deploy MySQL image to k8s cluster') {
            steps {
                sh """
                    sed -i "/image/ s/latest/\${VERSION}/" k8s-deployment/mysql/mysql-deploy.yaml
                    kubectl -n \${NAMESPACE} apply -f k8s-deployment/mysql/mysql-deploy.yaml
                    kubectl -n \${NAMESPACE} get pod |grep -v NAME | awk '{ print \$1 }'| xargs -i kubectl -n \${NAMESPACE} delete pod {}
                """
            }
        }

        stage('Test WP MySQL pod status') {
            steps {
                sh """
                    kubectl -n ${NAMESPACE} get pod
                """
            }
        }

        stage('Test k8s web-app URI health') {
            steps {
                sh """
                    curl -s -I https://web-app.poyaskov.ca
                """
            }
        }

    }

    post {
        failure {
            mail to: 'vadim@poyaskov.ca',
                subject: "Failed Pipeline: ${currentBuild.fullDisplayName}",
                body: """
                    FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'
                    Check console output at '${env.BUILD_URL}'
                    """
        }
        success {
            mail to: 'vadim@poyaskov.ca',
                subject: "Deployment finisged Successfully . Pipeline: ${currentBuild.fullDisplayName}",
                body: """
                    SUCCESSFUL: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'
                    Check console output at '${env.BUILD_URL}'
                    """
        }
    }

}
