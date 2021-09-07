#!groovy

import groovy.transform.Field


/**
 * Docker image Build and Deploy pipeline
 * IMAGE_NAME - Image name
 * IMAGE_GIT_URL - Image git repo URL
 * IMAGE_BRANCH - Image repo branch
 * IMAGE_CREDENTIALS_ID - Image repo credentials id
 * IMAGE_TAGS - Image tags
 * DOCKERFILE_PATH - Relative path to docker file in image repo
 * REGISTRY_URL - Docker registry URL (can be empty)
 * UPLOAD_TO_DOCKER_HUB    - True\False
 * REGISTRY_CREDENTIALS_ID - Docker hub credentials id
 *
 **/

@Field String SCRIPT_PATH = 'digital-path-automation'
@Field final String ansibleActions = 'ansibleChecks.groovy'
@Field final String properties = 'Config/Prechecks_properties'

def setCredentials(){
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'mysqldbconnect', usernameVariable: 'DBUserName', passwordVariable: 'DBPassword']]){
       mysqlCred = "${DBUserName}"
       mysqldbPass = "${DBPassword}"

       if(k8sLocation.equals("AWS")){
         storageClassName = "gp2"
       } else {
         storageClassName = "px-repl1-sc"
       }
    }
}

def validateInputs(){
   ansiColor('xterm') {
    print("Pipeline Inputs validation...")
    if(mysqlCred.isEmpty()){
        error "\u001b[1;31m Please enter DB user name\u001b[0m"
        currentBuild.result = 'FAILURE'
    }
    if(mysqldbPass.isEmpty()){
        error "\u001b[1;31m DB user password cannot be empty!\u001b[0m"
        currentBuild.result = 'FAILURE'
    }
    if( ClusterActivity.isEmpty() ){
        error "\u001b[1;31m Both, Deploy or Destroy did not checked out. Please make your choice\u001b[0m"
        currentBuild.result = 'FAILURE'
    }

    if( k8sLocation.isEmpty() ){
        error "\u001b[1;31m Both, Deploy or Destroy did not checked out. Please make your choice\u001b[0m"
        currentBuild.result = 'FAILURE'
    }
  }
}


// DEPLOY WPRESS 
def deployWPressApp(){
    withEnv(['IMAGE=' + IMAGE, 'RepoImageName=' + LIMAGE, 'VERSION=' + VERSION, 'storageClassName=' + storageClassName]){
       withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'mysqldbconnect', usernameVariable: 'DBUserName', passwordVariable: 'DBPassword']]){

          println("Deploying WordPress with storageClassName: " + storageClassName )
          if(NS_State.isEmpty()){
               echo "Namespace  ${App_Namespace} does not exist"
               sh returnStdout: true, script: "kubectl create ns $App_Namespace"
            } else {
                  echo "Namespace  ${App_Namespace} Already EXISTs"
          }

          if(SECRET_STATE.isEmpty()){
               echo "mysql-wp-pass is EMPTRY"
               sh returnStdout: true, script: "kubectl -n $App_Namespace create secret generic mysql-wp-pass --from-literal=username=$DBUserName --from-literal=password=$DBPassword"
            } else {
               echo "mysql-wp-pass NOT EMPTY. No need to Deploy"
          }

          sh script: 'sshpass -p ${Password} scp ${KubeConfigSafe}:~/wp-config.php .'
          sh returnStdout: true, script: 'kubectl -n $App_Namespace create configmap wp-config --from-file=wp-config.php || true'
          sh returnStdout: true, script: "sed -i 's/dummyappnamespace/${App_Namespace}/g; s/dummydbnamespace/${DB_Namespace}/g; /storageClassName/ s/dummysc/${storageClassName}/g' k8s-deployment/wp-app/wordpress-deployment.yaml"
          sh returnStdout: true, script: 'kubectl -n $App_Namespace apply -f k8s-deployment/wp-app/wordpress-deployment.yaml || true'
          sh returnStdout: true, script: 'kubectl -n $App_Namespace get pod -l app=wordpress|grep -v NAME | awk \'{ print $1 }\'| xargs -i kubectl -n $App_Namespace delete pod {}'

          sleep 30

          Pod_State = """${sh(
              returnStdout: true,
              script: 'kubectl -n $App_Namespace get pod -l app=wordpress -o jsonpath={.items[*].status.phase}'
          )}"""

          println("WordPress Pod Status: " + Pod_State)

          if( "${Pod_State}".trim().equals("Running") ){

             println("\033[32;1mWordPress Pod_State is \033[0m " + Pod_State + " \033[32;1m and working\033[0m ")

             sh script: 'sshpass -p ${Password} scp ${KubeConfigSafe}:~/wp-test-site.tar.gz .'
             sh returnStdout: true, script: '''
                tar -xvf wp-test-site.tar.gz
                App_POD_NAME=`kubectl -n $App_Namespace get pod -l app=wordpress -o=jsonpath={.items..metadata.name}`
                kubectl -n $App_Namespace get pod $App_POD_NAME
                kubectl cp var/www/html $App_Namespace/$App_POD_NAME:/var/www/html
             '''

          } else {
             println("\033[31;1mWordPress Pod State is \033[0m " + Pod_State + " \033[31;1m and NOT working\033[0m ")
          }

       }
   }
}

// DESTROY WPRESS
def destroyWPressApp(){
    withEnv(['IMAGE=' + IMAGE, 'RepoImageName=' + LIMAGE, 'VERSION=' + VERSION]){
       withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'mysqldbconnect', usernameVariable: 'DBUserName', passwordVariable: 'DBPassword']]){

          println("Destroying WordPress")

          sh returnStdout: true, script: "sed -i 's/dummyappnamespace/${App_Namespace}/g' k8s-deployment/wp-app/wordpress-deployment.yaml"
          sh returnStdout: true, script: 'kubectl -n $App_Namespace delete -f k8s-deployment/wp-app/wordpress-deployment.yaml'

       }
   }
}

// Create Private Key
def CreatePrivateKey(){
    withEnv(['RepoImageName=' + "Test" ]){
       withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'jenkins', usernameVariable: 'UserName', passwordVariable: 'Password']]){

          println("Creation a Private Key method")

          
          sh '''
             mkdir -p tempo/$SA_Name
             cd tempo/$SA_Name
             openssl req -new -newkey rsa:4096 -nodes -keyout yourdomain.key -out yourdomain.csr -subj "/C=CA/ST=Ontario/L=Toronto/O=CRDSC Inc./OU=ITOps/CN=kubernetes.cluster"
             tar -cvf yourdomain.tar ../../tempo/$SA_Name
             ls -l

          '''
          sh 'echo ${Password} | sudo -S wget -O tempo/$SA_Name/id_rsa.pub --http-user ${UserName} --http-password=${Password} https://nexus.crdsmartcity.com/repository/crd-tex/keys/cbrk-master-01/jenkins/jenkins-cbrk-master.pub.key'
          sh 'echo ${Password} | sudo -S wget -O tempo/$SA_Name/id_rsa --http-user ${UserName} --http-password=${Password} https://nexus.crdsmartcity.com/repository/crd-tex/keys/cbrk-master-01/jenkins/jenkins-cbrk-master.key'
          sh 'echo ${Password} | sudo -S curl -v -u ${UserName} :${Password} --upload-file yourdomain.tar https://nexus.crdsmartcity.com/repository/crd-tex/keys/cbrk-master-01/jenkins/yourdomain.tar'



       }
    }
}


// Stage to install and config  kubectl locally on the agent
stage("Kubectl config"){
    node("${env.NodeName}"){
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'Jenkins_KubeMaster', usernameVariable: 'UserName', passwordVariable: 'Password']]){

       echo '[Pipeline][INFO] Deploy kubectl and apply kubectl-config to the agent...'
       sh 'echo ${Password} | sudo -S apt-get update -y && sudo apt-get install -y kubectl'
       sh 'mkdir -p ~/.kube/'
       sh script: 'sshpass -p ${Password} scp ${KubeConfigSafe}:~/.kube/config ~/.kube/'
       sh 'kubectl get nodes'

       }
    }
}

// Create private Key Stage
stage("Create a Key"){
    node("${env.NodeName}"){
    wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'xterm']){

       echo '[Pipeline][INFO] Create Private Key Locally ...'
       setCredentials()
       validateInputs()
       checkout scm

       CreatePrivateKey()

       echo '\033[34mThis stage has built MariaDB image and pushed it to the DockerHub\033[0m'
       }
    }
}

