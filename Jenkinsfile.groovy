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
 * ARTIFACTORY_NAMESPACE - Artifactory namespace (oss, cicd,...)
 * UPLOAD_TO_DOCKER_HUB    - True\False
 * REGISTRY_CREDENTIALS_ID - Docker hub credentials id
 *
 **/

@Field String SCRIPT_PATH = 'digital-path-automation'
@Field final String ansibleActions = 'ansibleChecks.groovy'
@Field final String properties = 'Config/Prechecks_properties'

def stCredentials(){
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'mysqldbconnect', usernameVariable: 'DBUserName', passwordVariable: 'DBPassword']]){
       mysqlCred = "${DBUserName}"
       mysqldbPass = "${DBPassword}"
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
  }
}

def buildCustomMySQLImage(){
    withEnv(['IMAGE=' + IMAGE, 'RepoImageName=' + LIMAGE, 'VERSION=' + VERSION, "DBImageNAME=" + DBImageNAME]){
       withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'mysqldbconnect', usernameVariable: 'DBUserName', passwordVariable: 'DBPassword']]){

          if(env.ClusterActivity.equals("Deploy")){

             sh returnStdout: true, script: "sed -i 's/dummydb/wpdbtest/g; s/dummyuser/${DBUserName}/g; s/dummypass/${DBPassword}/g' sql-scripts/dbcreate.sql"

             sh 'docker pull $DBImageNAME'
             sh returnStdout: true, script: "docker build --build-arg dummy_image='${DBImageNAME}' --build-arg dummy_pass=${DBPassword} -t $IMAGE:$VERSION ."
             sh 'docker tag $IMAGE:$VERSION $LIMAGE:$VERSION'
             sh 'docker push $LIMAGE:$VERSION'
         
          } else {
            println("In DESTROY Stage you don't need to build a new Docker Image")
          }

       }
   }
}

// Deploy MySQL DB
def deployMySQLDB(){
    withEnv(['IMAGE=' + IMAGE, 'RepoImageName=' + LIMAGE, 'VERSION=' + VERSION]){
       withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'mysqldbconnect', usernameVariable: 'DBUserName', passwordVariable: 'DBPassword']]){

          ansiColor('vga') {
             echo '\033[42m\033[97mDEPLOYING MySQL Instance\033[0m'
             
             if(NS_State.isEmpty()){
                  echo "Namespace  ${DB_Namespace} does not exist"
                  sh returnStdout: true, script: "kubectl create ns $DB_Namespace"
               } else {
                  echo "Namespace  ${DB_Namespace} Already EXISTs"
             }
             echo "Secret value: $SECRET_STATE"
             someVar = "${SECRET_STATE}"
             
             if(someVar.isEmpty()){
                  echo "mysql-wp-pass is EMPTRY"
                  sh returnStdout: true, script: "kubectl -n $DB_Namespace create secret generic mysql-wp-pass --from-literal=username=$DBUserName --from-literal=password=$DBPassword"
               } else {
                  echo "mysql-wp-pass NOT EMPTY. No need to Deploy"
             }

             sh returnStdout: true, script: "sed -i 's/dummydbnamespace/${DB_Namespace}/g' k8s-deployment/mysql/mysql-deploy.yaml"
             sh returnStdout: true, script: 'kubectl -n $DB_Namespace apply -f k8s-deployment/mysql/mysql-deploy.yaml'
             sh returnStdout: true, script: 'kubectl -n $DB_Namespace get pod -l app=mysql-wp|grep -v NAME | awk \'{ print $1 }\'| xargs -i kubectl -n $DB_Namespace delete pod {}'
             
          }
       }
   }
}

// Destroy MySQL DB
def destroyMySQLDB(){
    withEnv(['IMAGE=' + IMAGE, 'RepoImageName=' + LIMAGE, 'VERSION=' + VERSION]){
       withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'mysqldbconnect', usernameVariable: 'DBUserName', passwordVariable: 'DBPassword']]){

          echo '\033[42m\033[97mDESTROYING MySQL DB Instance\033[0m'

          sh returnStdout: true, script: "sed -i 's/dummydbnamespace/${DB_Namespace}/g' k8s-deployment/mysql/mysql-deploy.yaml"
          sh returnStdout: true, script: 'kubectl -n $DB_Namespace delete -f k8s-deployment/mysql/mysql-deploy.yaml'

       }
   }
}

// DEPLOY WPRESS 
def deployWPressApp(){
    withEnv(['IMAGE=' + IMAGE, 'RepoImageName=' + LIMAGE, 'VERSION=' + VERSION]){
       withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'mysqldbconnect', usernameVariable: 'DBUserName', passwordVariable: 'DBPassword']]){

          println("Deploying WordPress")
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

          sh returnStdout: true, script: "sed -i 's/dummyappnamespace/${App_Namespace}/g' k8s-deployment/wp-app/wordpress-deployment.yaml"
          sh returnStdout: true, script: 'kubectl -n $App_Namespace apply -f k8s-deployment/wp-app/wordpress-deployment.yaml'
          sh returnStdout: true, script: 'kubectl -n $App_Namespace get pod -l app=wordpress|grep -v NAME | awk \'{ print $1 }\'| xargs -i kubectl -n $App_Namespace delete pod {}'

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

stage("Build MyQSL Image"){
    node("${env.NodeName}"){
    wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'xterm']){

       echo '[Pipeline][INFO] Checkout Code from GitHub...'
       stCredentials()
       validateInputs()
       checkout scm
              
       buildCustomMySQLImage()

       echo '\033[34mThis stage has built MariaDB image and pushed it to the DockerHub\033[0m'
       }
    }
}

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

stage("MySQL DB Activity"){
    node("${env.NodeName}"){
    wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'xterm']){
       withEnv(['KubeConfigSafe=' + KubeConfigSafe, 'RepoImageName=' + LIMAGE, 'VERSION=' + VERSION]){
          withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'Jenkins_KubeMaster', usernameVariable: 'UserName', passwordVariable: 'Password']]){

             echo '\033[34m[Pipeline][INFO] Deploy/Destroy MySQL(MariaDB) to the k8s Cluster...\033[0m'
             sh 'mkdir -p ~/.kube/'
             sh script: 'sshpass -p ${Password} scp ${KubeConfigSafe}:~/.kube/config ~/.kube/'
             sh 'kubectl get nodes'

             NS_State = """${sh(
                                 returnStdout: true,
                                 script: 'kubectl get ns $DB_Namespace 2>/dev/null || true'  
             )}"""

             println("NameSpace status:" + NS_State )

             SECRET_STATE = """${sh(
                                 returnStdout: true,
                                 script: 'kubectl -n $DB_Namespace get secret mysql-wp-pass -o jsonpath={.data.password} 2>/dev/null || true'
             )}"""

             if(env.ClusterActivity.equals("Deploy")){
                deployMySQLDB()
                } else {
                destroyMySQLDB()
             }
             
             Pod_State = """${sh(
                                 returnStdout: true,
                                 script: 'kubectl -n $DB_Namespace get pod -l app=mysql-wp -o jsonpath={.items[*].status.phase}'
             )}"""
             
             println("MySQL Pod Status: " + Pod_State)

          }
       }
   }
   }
}

stage("WPress App Activity"){
    node("${env.NodeName}"){
    wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'xterm']){
       withEnv(['KubeConfigSafe=' + KubeConfigSafe, 'RepoImageName=' + LIMAGE, 'VERSION=' + VERSION]){
          withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'Jenkins_KubeMaster', usernameVariable: 'UserName', passwordVariable: 'Password']]){

             echo '\033[34m[Pipeline][INFO] Deploy/Destroy WordPress App to the k8s Cluster...\033[0m'

             NS_State = """${sh(
                                 returnStdout: true,
                                 script: 'kubectl get ns $App_Namespace 2>/dev/null || true'
             )}"""

             println("WP App NameSpace status:" + NS_State )

             SECRET_STATE = """${sh(
                                 returnStdout: true,
                                 script: 'kubectl -n $App_Namespace get secret mysql-wp-pass -o jsonpath={.data.password} 2>/dev/null || true'
             )}"""

             if(env.ClusterActivity.equals("Deploy")){
                deployWPressApp()
                } else {
                destroyWPressApp()
             }

             Pod_State = """${sh(
                                 returnStdout: true,
                                 script: 'kubectl -n $App_Namespace get pod -l app=wordpress -o jsonpath={.items[*].status.phase}'
             )}"""

             println("WordPrtess Pod Status: " + Pod_State)

             if( Pod_State.equals("Running") ){
              sh script: 'sshpass -p ${Password} scp ${KubeConfigSafe}:~/wp-test-site.tar.gz .'
              sh returnStdout: true,script: '''

                 tar -xvf wp-test-site.tar.gz
                 ls -l
              ''' 

             }
          }
       }
   }
   }
}

