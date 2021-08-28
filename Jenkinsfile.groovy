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
    print("Pipeline Inputs validation...")
    if(mysqlCred.isEmpty()){
        error "\u001b[1;31m Please enter DB user name\u001b[0m"
        currentBuild.result = 'FAILURE'
    }
    if(mysqldbPass.isEmpty()){
        error "\u001b[1;31m DB user password cannot be empty!\u001b[0m"
        currentBuild.result = 'FAILURE'
    }
    
}

def buildCustomMySQLImage(){
    withEnv(['IMAGE=' + IMAGE, 'RepoImageName=' + LIMAGE, 'VERSION=' + VERSION]){
       withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'mysqldbconnect', usernameVariable: 'DBUserName', passwordVariable: 'DBPassword']]){

       sh returnStdout: true, script: "sed -i 's/dummydb/wpdbtest/g; s/dummyuser/${DBUserName}/g; s/dummypass/${DBPassword}/g' sql-scripts/dbcreate.sql"

       sh 'docker pull mariadb:10.6.4'
       sh returnStdout: true, script: "docker build --build-arg dummy_pass=${DBPassword} -t $IMAGE ."
       sh 'docker tag $IMAGE $LIMAGE:$VERSION'
       sh 'docker push $LIMAGE:$VERSION'


       }
   }
}

def deployMySQLDB(){
    withEnv(['IMAGE=' + IMAGE, 'RepoImageName=' + LIMAGE, 'VERSION=' + VERSION]){
       withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'mysqldbconnect', usernameVariable: 'DBUserName', passwordVariable: 'DBPassword']]){

          ansiColor('vga') {
             echo '\033[42m\033[97mkubectl deployed and configured\033[0m'
             //sh returnStdout: true, script: "kubectl -n $DB_Namespace create secret generic mysql-wp-pass --from-literal=username=$DBUserName --from-literal=password=$DBPassword"
             
             sh returnStdout: true, script: 'kubectl -n $DB_Namespace apply -f k8s-deployment/mysql/mysql-deploy.yaml'
             sh returnStdout: true, script: 'kubectl -n $DB_Namespace get pod -l app=mysql-wp|grep -v NAME | awk '{ print $1 }'| xargs -i kubectl -n $DB_Namespace delete pod {}'
             sh returnStdout: true, script: 'SECRET_STATE=`kubectl -n $DB_Namespace get secret mysql-wp-pass -o jsonpath={.data.password} 2>/dev/null`'

             sh returnStdout: true, script: 'echo $SECRET_STATE'
             sh returnStdout: true, script: 'if [ ! -z $SECRET_STATE ]; then echo "MySQL Secret mysql-wp-pass already exists"; else echo "Secret mysql-wp-pass is $SECRET_STATE and NOT  exists"; fi'

                  //kubectl -n $DBNAMESPACE create secret generic mysql-pass --from-literal=password=$dummy_pass
                  //kubectl -n $DB_Namespace create secret generic mysql-wp-pass --from-literal=username=$DBUserName --from-literal=password=$DBPassword"
             
          }
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

stage("Deploy MySQL DB"){
    node("${env.NodeName}"){
    wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'xterm']){
       withEnv(['KubeConfigSafe=' + KubeConfigSafe, 'RepoImageName=' + LIMAGE, 'VERSION=' + VERSION]){
          withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'Jenkins_KubeMaster', usernameVariable: 'UserName', passwordVariable: 'Password']]){

             echo '[Pipeline][INFO] Deploy MySQL(MariaDB) to the k8s Cluster...'

             sh 'mkdir -p ~/.kube/'
             sh script: 'sshpass -p ${Password} scp ${KubeConfigSafe}:~/.kube/config ~/.kube/'
             sh 'kubectl get nodes'



             deployMySQLDB()


          }
       }
   }
   }
}

