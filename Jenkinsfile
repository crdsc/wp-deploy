// Jenkinsfile for WP deploy

pipeline {

    agent { label 'crdsc-master' }

    options {
      timestamps()
    }

    environment {
      //Use Pipeline Utility Steps plugin to read information from pom.xml into env variables
        IMAGE = 'wp-mysql-db'
        LIMAGE = 'registry.crdsmart.city/wp-mysql-db'
        VERSION = "0.${BUILD_NUMBER}"
        TAG = "${BUILD_NUMBER}"
        NAMESPACE = 'wp-mysql-db'
        INC="0.1"
    }

    stages {


        stage('Build and push web-app image to the local registry') {
            steps {
                sh """
                    ls -l
                    sed -i "/Build/ s/latest/\${BUILD_NUMBER}/" index.html
                    docker pull ubuntu:18.04
                    docker build -t ${IMAGE} .
                    docker tag ${IMAGE} ${LIMAGE}:${VERSION}
                    docker push ${LIMAGE}:${VERSION}
                """
            }
        }

        stage('Deploy kubectl and apply kubectl-config to the agent') {
            steps {
                sh """
                    sudo apt-get update && sudo apt-get install -y kubectl
                    mkdir -p ~/.kube/
                    scp vadim@158.50.25.21:~/.kube/config ~/.kube/
                    kubectl get nodes
                """
            }
        }

        stage('Deploy new image to k8s cluster') {
            steps {
                sh """
                    sed -i "/image/ s/latest/\${VERSION}/" files/test-webapp-deploy.yaml
                    kubectl -n \${NAMESPACE} apply -f files/test-webapp-deploy.yaml
                    kubectl -n \${NAMESPACE} get pod |grep -v NAME | awk '{ print \$1 }'| xargs -i kubectl -n \${NAMESPACE} delete pod {}
                """
            }
        }

        stage('Test k8s web-app pod status') {
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
