pipeline {

    agent { label "${AgentName}" }

    options {
      timestamps()
    }

    stages {


        stage('Build MySQL DB image and push to the registry') {
            steps {
                sh('ls -l')
                sh('docker pull mariadb:latest')
                sh('docker build --build-arg dummy_pass=$dummy_pass -t $IMAGE .')
                sh('docker tag $IMAGE $LIMAGE:$VERSION')
                sh('docker push $LIMAGE:$VERSION')
                //sh('curl -u $EXAMPLE_CREDS_USR:$EXAMPLE_CREDS_PSW https://example.com/')
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
                    kubectl -n \${DBNAMESPACE} apply -f k8s-deployment/mysql/mysql-deploy.yaml
                    kubectl -n \${DBNAMESPACE} get pod |grep -v NAME | awk '{ print \$1 }'| xargs -i kubectl -n \${DBNAMESPACE} delete pod {}
                    SECRET_STATE=`kubectl -n wp-test get secret mysql-passs o jsonpath={.data.password} 2>/dev/null`
                """
                sh '
                kubectl -n wp-test create secret generic mysql-pass --from-literal=password=$dummy_pass
                '
            }
        }

        stage('Deploy WP image to k8s cluster') {
            steps {
                sh """
                    sed -i "/image/ s/latest/\${VERSION}/" k8s-deployment/wp-app/wordpress-deployment.yaml
                    kubectl -n \${NAMESPACE} apply -f k8s-deployment/wp-app/wordpress-deployment.yaml
                    kubectl -n \${NAMESPACE} get pod |grep -v NAME | awk '{ print \$1 }'| xargs -i kubectl -n \${NAMESPACE} delete pod {}
                """
            }
        }

        stage('Check WP MySQL pod status') {
            steps {
                sh """
                    DB_STATE=""
                    DB_STATE=`kubectl -n ${DBNAMESPACE} get podi -l app=mysql -o jsonpath='{.items[*].status.containerStatuses[0].ready}'`
                    WP_STATE="`kubectl -n ${NAMESPACE} get pod -l app=wordpress -o jsonpath='{.items[*].status.containerStatuses[0].ready}'`"
                    #if ! "${DB_STATE}" 
                    #   then echo "DB MySQL deployed with errors"
                    #fi
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
