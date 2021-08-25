// Jenkinsfile for WP deploy

pipeline {

    agent { label "${AgentName}" }

    options {
      timestamps()
    }

    environment {
      //Use Pipeline Utility Steps plugin to read information from pom.xml into env variables
        BUILD_NUMBER = '1'
        IMAGE = 'wp-mysql-db'
        LIMAGE = 'poyaskov/wp-mysql-db'
        VERSION = "0.${BUILD_NUMBER}"
        TAG = "${BUILD_NUMBER}"
        INC="0.1"
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

        stage('Deploy K8S Cluster to AWS'){
           steps {
              withAWS(credentials: "{AWS_Jenkins}", region: 'ca-central-1') {

                 s3Upload bucket: "my-bucket", path: "foo/text.txt"

              }
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
