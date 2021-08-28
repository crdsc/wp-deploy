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
    wrap([$class: 'AnsiColorBuildWrapper']){
       withEnv(['IMAGE=' + IMAGE, 'RepoImageName=' + LIMAGE, 'VERSION=' + VERSION]){
          withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'mysqldbconnect', usernameVariable: 'DBUserName', passwordVariable: 'DBPassword']]){

          echo "buildCustomMySQLImage function"

          sh returnStdout: true, script: "sed -i 's/dummydb/wpdbtest/g; s/dummyuser/${DBUserName}/g; s/dummypass/${DBPassword}/g' sql-scripts/dbcreate.sql"

          //sh 'ls -l'
          sh 'docker pull mariadb:10.6.4'
          sh returnStdout: true, script: "docker build --build-arg dummy_pass=${DBPassword} -t $IMAGE ."
          sh 'docker tag $IMAGE $LIMAGE:$VERSION'
          sh 'docker push $LIMAGE:$VERSION'


          }       
       }
   }
}

stage("Build MyQSL Image"){
    node("${env.NodeName}"){
        wrap([$class: 'AnsiColorBuildWrapper']){

              echo '[Pipeline][INFO] Checkout Code from GitHub...'
              stCredentials()
              validateInputs()
              checkout scm
              
              echo '[Pipeline][INFO] Building Docker Image ...'

              buildCustomMySQLImage()

              ansiColor('xterm') {
              echo '\033[42m\033[97mThis stage building MariaDB image uand pushing it to the DockerHub\033[0m'
             }
        }
    }
}

stage("Kubectl config"){
    node("${env.NodeName}"){
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'Jenkins_KubeMaster', usernameVariable: 'UserName', passwordVariable: 'Password']]){

       echo '[Pipeline][INFO] Deploy kubectl and apply kubectl-config to the agent...'

       sh 'echo ${Password} | sudo -S apt-get update -y && sudo apt-get install -y kubectl'
       sh 'mkdir -p ~/.kube/'
       sh script: "scp "${KubeConfigSafe}":~/.kube/config ~/.kube/"
       sh 'kubectl get nodes'

       ansiColor('xterm'){
           echo '\033[42m\033[97mkubectl deplyed abd configured\033[0m'
          }
       }
    }
}
