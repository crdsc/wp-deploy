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
    mysqlCred = env.DBUserName
    mysqldbPass = env.DBPassword
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
       withEnv(['KubeConfigSafe=' + KubeConfigSafe, ]){
       echo "buildCustomMySQLImage function"

       
       }
   }
}

stage("Checkout Code from GitHub"){
    node('docker-rg'){
        wrap([$class: 'AnsiColorBuildWrapper']){
           echo '[Pipeline][INFO] Checkout Code from GitHub...'
           stCredentials()
           validateInputs()
           checkout scm
//           checkout changelog: false; pool: false; scm: [$class: 'GitSCM', branches: [[name: main]], url: 'https://github.com/poyaskov/wp-deploy.git', credentialsId: 'poyaskov_github_pt']
                  
        }
    }
}

stage("Stage 2"){
    node('docker-rg'){
        wrap([$class: 'AnsiColorBuildWrapper']){
           echo '[Pipeline][INFO] Stage Two ...'
           echo '\033 Hello \033 \033[33mcolorful\033[0m \033 world! \033'
           echo "\u001b Please enter DB user name\u001b"
           ansiColor('xterm') {
           echo '\033[42m\033[97mWhite letters, green background\033[0m'
           }
        }
    }
}

stage("Stage 3"){
    node('docker-rg'){
    ansiColor('xterm'){
        echo '[Pipeline][INFO] Stage Three ...'
        echo '\033[34mHello\033[0m \033[33mcolorful\033[0m \033[35mworld!\033[0m'
        echo '\033[42m\033[97mWhite letters, green background\033[0m'
        echo "\u001b[31m Please enter DB user name\u001b[0m"
       }
    }
}
