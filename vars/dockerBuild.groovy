def call(String useDockerfile = ''){
    String dockerfileBuildImageId
    String dockerfileBuildContainerId
    pipeline{
        agent { label "agent-1" }
        options {
            buildDiscarder(logRotator(daysToKeepStr: '365', numToKeepStr: '64'))
        }
        stages{
            stage("Git checkout"){
                steps{
                    checkout(
                        [
                            $class: 'GitSCM',
                            branches: [[name: "refs/heads/${env.GIT_REPOSITORY_BRANCH}"]],
                            doGenerateSubmoduleConfigurations: false,
                            extensions: [[
                                $class: 'SubmoduleOption',
                                disableSubmodules: false,
                                recursiveSubmodules: true,
                                parentCredentials: true,
                                shallow: true,
                                trackingSubmodules: false
                            ]],
                            userRemoteConfigs: [[
                                credentialsId: 'GITHUB',
                                url: "${env.GIT_REPOSITORY_URL}"
                            ]]
                        ]
                    )
                }
            }
            stage("Docker registry login"){
                steps{
                    echo "==> Using credentials ID: DOCKER"
                    withCredentials([
                        usernamePassword(
                            credentialsId: "DOCKER",
                            usernameVariable: "DOCKER_REGISTRY_USER",
                            passwordVariable: "DOCKER_REGISTRY_PASSWORD"
                        )
                    ]){
                        sh ("echo ${DOCKER_REGISTRY_PASSWORD} | docker login -u ${DOCKER_REGISTRY_USER} --password-stdin")
                    }
                }
            }
            stage("Resolve Release Version for non-master branch") {
              when {
                not {
                  environment name: 'GIT_REPOSITORY_BRANCH', value: 'master'
                }
              }
              steps {
                script {
                  // The current git tag is selected as the current version (ex. "1.0.1")
                  def currentVersion = sh(script: "git tag --sort=-v:refname | head -n 1", returnStdout: true).trim()
                  env.TAG = resolveBaseTag()
                  echo "Got base tag: ${env.TAG}"
                  env.PROMO_TAGS = generatePromotionTags(currentVersion).join(" ")
                  echo "Got promotion tags: ${env.PROMO_TAGS}"
                }
              }
            }
            stage("Resolve Release Version for master branch") {
              when {
                environment name: 'GIT_REPOSITORY_BRANCH', value: 'master'
              }
              
              steps {
                script {
                  // If the build is started for the master branch, a new tag will be created and pushed to the git repository
                  def currentVersion = sh(script: "git tag --sort=-v:refname | head -n 1", returnStdout: true).trim()
                  def newVersion = incrementPatchVersion(currentVersion)
                  echo "About to bump git tag from ${currentVersion} to ${newVersion} and push to repository"
                  sh """
                      git tag -a ${newVersion} -m "Release ${newVersion}" HEAD
                      git push origin ${newVersion}
                  """
                  // TODO: add comment here why we use this for master branch
                  env.TAG = currentVersion + "-staging"
                  echo "Got base tag: ${env.TAG}"
                  env.PROMO_TAGS = generatePromotionTags(currentVersion).join(" ")
                  echo "Got promotion tags: ${env.PROMO_TAGS}"
                }
              }
            }
            stage('Build Docker Images for non-master branch'){
              // This stage should always run IF build skipping is NOT enabled (default)
              // Build skipping is useful when there is a need to "build" all jobs
              // successfully without running actual build process (e.g. when creating
              // hundreds of job items simultaneously via DSL)
              when {
                allOf {
                  expression { !skipBuildCompletely }
                  not {
                    environment name: 'GIT_REPOSITORY_BRANCH', value: 'master'
                  }
                }
              }

              // Set environment for this stage: removed 
              // since 'env.getEnviromnet()' will be without these values
              // this is incompatible with later steps

              steps {
                echo "Effective value of TAG environment variable is '${env.TAG}' (Groovy)"
                // Extra assertion to ensure that TAG variable is not set as literal "null"
                // Possible cause: https://issues.jenkins.io/browse/JENKINS-43632
                script {
                  if (env.GIT_REPOSITORY_BRANCH != 'null') {
                    assert sh(script: 'echo $TAG', returnStdout: true).toString().trim() != 'null'
                  }
                }

                script {
                  // Run Docker Build or Docker Compose Build depending on useDockerfile parameter value
                  Map result
                  if (useDockerfile){
                    result = runDockerfileBuild(useDockerfile)
                    (dockerfileBuildImageId, dockerfileBuildContainerId) = result.isSuccessful ? [result.layerId, ''] : ['', result.layerId]
                  } else {
                    result = runDockerComposeBuild()
                  }
                  if (!result.isSuccessful){
                    error('Docker build failure')
                  }
                }
              }
            }
            stage('Pull Images for master branch'){
              // Must pull images with "<version>-staging" tag
              when {
                environment name: 'GIT_REPOSITORY_BRANCH', value: 'master'
              }

              steps {
                if (useDockerfile){
                  def prevImageTag = infraImageTagFromGitRepo(env.TAG)
                  echo "Pulling Dockerfile image: ${prevImageTag}"
                  def status = sh(script: "docker pull ${prevImageTag}", returnStatus: true)
                  if (status != 0) {
                      error("Failed to pull Dockerfile image: ${prevImageTag}")
                  }
                  dockerfileBuildImageId = sh(script: "docker images -q ${prevImageTag}", returnStdout: true).trim()
                  if (!dockerfileBuildImageId) {
                      error("Dockerfile image ID not found for ${prevImageTag}")
                  }
                  echo "Dockerfile image ID found: ${dockerfileBuildImageId}"
                } else {
                    echo "Pulling Docker Compose images..."
                    def status = sh(script: "docker-compose pull", returnStatus: true)
                    if (status != 0) {
                        error("Failed to pull Docker Compose images")
                    }
                    def images = sh(script: "docker-compose config --images", returnStdout: true).trim().split("\n")
                    for (img in images) {
                        def exists = sh(script: "docker images -q ${img}", returnStdout: true).trim()
                        if (!exists) {
                            error("Image ${img} is missing after pull")
                        } else {
                            echo "Verified image: ${img}"
                        }
                    }
                }
              }
            }
            stage("Promote & Publish Images") {
              steps {
                script {
                  List images = []
                  def tags = [env.TAG] + PROMO_TAGS.tokenize(" ")
                  if (useDockerfile){
                    for (tag in tags) {
                      def image = promoteDockerfileImage(dockerfileBuildImageId, infraImageTagFromGitRepo(tag))
                      images.add(image)
                    }
                  } else{
                    for (tag in tags) {
                      images = images + promoteDockerComposeImages(tag)
                    }
                  }
                  echo "images: $images"
                  if (params.DOCKER_RUN_PUSH){
                    echo "DOCKER_RUN_PUSH is set to true, publishing images"
                    for (img in images){
                      publishDockerImage(img)
                    }
                  } else{
                    echo "DOCKER_RUN_PUSH is set to false, skipping service"
                  }
                }
              }
            }
        }
    }
}

def incrementPatchVersion(version) {
    def parts = version.tokenize(".")

    if (parts.size() != 3) {
        error("Version format must be major.minor.patch")
    }

    def major = parts[0].toInteger()
    def minor = parts[1].toInteger()
    def patch = parts[2].toInteger()
    patch++
    def newVersion = "${major}.${minor}.${patch}"
    return newVersion
}

def generatePromotionTags(version){
    if (env.GIT_REPOSITORY_BRANCH == "master"){
        return [version]
    }
    else if (env.GIT_REPOSITORY_BRANCH == "staging"){
        return ["${version}-staging", "${version}-staging.${env.BUILD_NUMBER}"]
    } else {
        return []
    }

}

def resolveBaseTag(){
    if (env.GIT_REPOSITORY_BRANCH == "master"){
        return "master"
    }
    else if (env.GIT_REPOSITORY_BRANCH == "staging"){
        return "staging"
    } else {
        return sh(script: "git describe --tags", returnStdout: true).trim()
    }
}
