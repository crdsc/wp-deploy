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


              withEnv(['KOPS_STATE_STORE=s3://k8s-crdsc-org']) {

                 sh """
                    echo $KOPS_STATE_STORE
                    // kops get k8s.crdsmartcity.org --state=$KOPS_STATE_STORE
                    CLUSTER_STATE=`kops get k8s.crdsmartcity.org --state=s3://k8s-crdsc-org`
                 """
              }

              withEnv(["AWS_ACCESS_KEY_ID=${env.AWS_ACCESS_KEY_ID}",
                 "AWS_SECRET_ACCESS_KEY=${env.AWS_SECRET_ACCESS_KEY}",
                 "AWS_DEFAULT_REGION=${env.AWS_DEFAULT_REGION}"]) {
                 
                 sh """
                    echo $KOPS_STATE_STORE
                    kops create cluster --name k8s.crdsmartcity.org --zones ca-central-1a --state $KOPS_STATE_STORE --yes
                 """
              }

              withEnv(["AWS_ACCESS_KEY_ID=${env.AWS_ACCESS_KEY_ID}",
                 "AWS_SECRET_ACCESS_KEY=${env.AWS_SECRET_ACCESS_KEY}",
                 "AWS_DEFAULT_REGION=${env.AWS_DEFAULT_REGION}"]) {
                 
                 sh """
                    echo $KOPS_STATE_STORE
                    kops get k8s.crdsmartcity.org --state=$KOPS_STATE_STORE
                 """
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
