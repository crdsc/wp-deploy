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

          sh returnStdout: true, script: "sed -i 's/dummydb/wpdbtest/g; s/dummydbuser/wpadmin/g; s/dummydbpass/password/g' sql-scripts/dbcreate.sql"

          //sh 'ls -l'
          sh 'cat sql-scripts/dbcreated.sql'
          sh 'docker pull mariadb:latest'
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

stage("Stage 3"){
    node("${env.NodeName}"){
    ansiColor('xterm'){
        echo '[Pipeline][INFO] Stage Three ...'
        echo '\033[34mHello\033[0m \033[33mcolorful\033[0m \033[35mworld!\033[0m'
        echo '\033[42m\033[97mWhite letters, green background\033[0m'
        echo "\u001b[31m Please enter DB user name\u001b[0m"
       }
    }
}
