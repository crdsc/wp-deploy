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

//Custom MySQL Docker Image Building
def buildCustomMySQLImage(){
    withEnv(['IMAGE=' + IMAGE, 'RepoImageName=' + LIMAGE, 'VERSION=' + VERSION, 'DBImageNAME=' + DBImageNAME]){
       withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'mysqldbconnect', usernameVariable: 'DBUserName', passwordVariable: 'DBPassword']]){

          if(env.ClusterActivity.equals("Deploy")){

             sh returnStdout: true, script: "sed -i 's/dummydb/wordpress/g; s/dummyuser/${DBUserName}/g; s/dummypass/${DBPassword}/g' sql-scripts/dbcreate.sql"

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

// Custom WordPress Docker Image Build
def buildCustomWPImage(){
    withEnv(['IMAGE=' + IMAGE, 'RepoImageName=' + LIMAGE, 'VERSION=' + VERSION, "WPImageNAME=" + WPImageNAME, 'DBHostName=' + "DBHostname.resulta-db.svc.local.cluster"]){
       withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'mysqldbadmin', usernameVariable: 'DBUserName', passwordVariable: 'DBPassword']]){

          if(env.ClusterActivity.equals("Deploy")){

             sh script: 'sshpass -p ${Password} scp ${KubeConfigSafe}:~/wp-test-site.tar.gz .'
             sh returnStdout: true, script: "tar -xvf wp-test-site.tar.gz; sed -i 's/dummyhost/${DBHostName}/g; s/dummyuser/${DBUserName}/g; s/dummypass/${DBPassword}/g' var/www/html/wp-config.php"
             sh returnStdout: true, script: "docker pull $WPImageNAME; docker build -t $LWPIMAGE:$VERSION -f DockerfileWP . ; docker push $LWPIMAGE:$VERSION"

          } else {
            println("In DESTROY Stage you don't need to build a new Docker Image")
          }

       }
   }
}

// Deploy MySQL DB
def deployMySQLDB(){
    withEnv(['IMAGE=' + IMAGE, 'RepoImageName=' + LIMAGE, 'VERSION=' + VERSION, 'storageClassName=' + storageClassName]){
       withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'mysqldbadmin', usernameVariable: 'DBUserName', passwordVariable: 'DBPassword']]){

          ansiColor('vga') {
              echo '\033[42m\033[97mDEPLOYING MySQL Instance with StorageClass: $storageClassName\033[0m'
             
             if(NS_State.isEmpty()){
                  echo "Namespace  ${DB_Namespace} does not exist"
                  sh returnStdout: true, script: "kubectl create ns $DB_Namespace"
               } else {
                  echo "Namespace  ${DB_Namespace} Already EXISTs"
             }
             
             if("${SECRET_STATE}".isEmpty()){
                  echo "mysql-wp-pass is EMPTRY"
                  sh returnStdout: true, script: "kubectl -n $DB_Namespace create secret generic mysql-wp-pass --from-literal=username=$DBUserName --from-literal=password=$DBPassword"
               } else {
                  echo "mysql-wp-pass NOT EMPTY. No need to Deploy"
             }

             sh returnStdout: true, script: "sed -i 's/dummydbnamespace/${DB_Namespace}/g; s/dummysc/${storageClassName}/g' k8s-deployment/mysql/mysql-deploy.yaml"
             sh returnStdout: true, script: 'kubectl -n $DB_Namespace apply -f k8s-deployment/mysql/mysql-deploy.yaml || true'
             sh returnStdout: true, script: 'kubectl -n $DB_Namespace get pod -l app=mysql-wp|grep -v NAME | awk \'{ print $1 }\'| xargs -i kubectl -n $DB_Namespace delete pod {}'

             Pod_State = """${sh(
                 returnStdout: true,
                 script: 'kubectl -n $DB_Namespace get pod -l app=mysql-wp -o jsonpath={.items[*].status.phase}'
             )}"""

             println("MySQL Pod Status: " + Pod_State)
             sleep 30
             if( "${Pod_State}".trim().equals("Running") ){

                println("\033[32;1mPod_State is \033[0m " + Pod_State + " \033[32;1m and working\033[0m ")

                sh script: 'sshpass -p ${Password} scp ${KubeConfigSafe}:~/wpdatabase.sql .'
                sh returnStdout: true, script: '''
                   DB_POD_NAME=`kubectl -n $DB_Namespace get pod -l app=mysql-wp -o=jsonpath={.items..metadata.name}`
                   kubectl -n $DB_Namespace get pod $DB_POD_NAME
                   sleep 90
                   kubectl -n $DB_Namespace exec -ti $DB_POD_NAME -- mysql -h localhost -u$DBUserName -p$DBPassword < wpdatabase.sql
                '''

             } else {
                println("\033[31;1mPod_State is \033[0m " + Pod_State + " \033[31;1m and NOT working\033[0m ")
             }
             
          }
       }
   }
}

// Destroy MySQL DB
def destroyMySQLDB(){
    withEnv(['IMAGE=' + IMAGE, 'RepoImageName=' + LIMAGE, 'VERSION=' + VERSION]){
       withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'mysqldbconnect', usernameVariable: 'DBUserName', passwordVariable: 'DBPassword']]){

          echo '\033[42mDESTROYING MySQL DB Instance\033[0m'

          sh returnStdout: true, script: "sed -i 's/dummydbnamespace/${DB_Namespace}/g' k8s-deployment/mysql/mysql-deploy.yaml"
          sh returnStdout: true, script: 'kubectl -n $DB_Namespace delete -f k8s-deployment/mysql/mysql-deploy.yaml'

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

// Build Custom MySQL Image Stage
stage("Build MyQSL Image"){
    node("${env.NodeName}"){
    wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'xterm']){

       echo '[Pipeline][INFO] Build MySQL Custom Image...'
       setCredentials()
       validateInputs()
       checkout scm
              
       buildCustomMySQLImage()

       echo '\033[34mThis stage has built MariaDB image and pushed it to the DockerHub\033[0m'
       }
    }
}

//Stage to build Custom WordPress Image
stage("Build WordPress Image"){
    node("${env.NodeName}"){
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'Jenkins_KubeMaster', usernameVariable: 'UserName', passwordVariable: 'Password']]){
       wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'xterm']){

          echo '[Pipeline][INFO] Build WordPress Custom Image...'
          setCredentials()
          validateInputs()
          checkout scm

          buildCustomWPImage()

          echo '\033[34mThis stage has built WordPressB image and pushed it to the DockerHub\033[0m'
          }
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

stage("MySQL DB Activity"){
    node("${env.NodeName}"){
    wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'xterm']){
       withEnv(['KubeConfigSafe=' + KubeConfigSafe, 'RepoImageName=' + LIMAGE, 'VERSION=' + VERSION]){
          withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'Jenkins_KubeMaster', usernameVariable: 'UserName', passwordVariable: 'Password']]){

             setCredentials()
             validateInputs()

             echo '\033[34m[Pipeline][INFO] Deploy/Destroy MySQL(MariaDB) to the k8s Cluster...\033[0m'
             sh 'mkdir -p ~/.kube/'
             sh script: 'sshpass -p ${Password} scp ${KubeConfigSafe}:~/.kube/config ~/.kube/'

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

             setCredentials()
             validateInputs()

             echo '\033[34m[Pipeline][INFO] Deploy/Destroy WordPress App on the k8s Cluster...\033[0m'

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
          }
       }
   }
   }
}

