def call(String useDockerfile = ''){
    String dockerfileBuildImageId
    String dockerfileBuildContainerId
    String baseVersion
    String releaseVersion
    Boolean skipBuildCompletely = false
    pipeline{
      agent { label "agent-1" }
      options {
          buildDiscarder(logRotator(daysToKeepStr: '365', numToKeepStr: '64'))
      }
    stages{
      stage("Git checkout"){
          steps{
            // Clean workspace before we use it
            cleanWs()
            checkout(
                [
                    $class: 'GitSCM',
                    branches: [[name: "refs/heads/${env.GIT_REPOSITORY_BRANCH}"]],
                    doGenerateSubmoduleConfigurations: false,
                    extensions: [
                      [
                        $class: 'SubmoduleOption',
                        disableSubmodules: false,
                        recursiveSubmodules: true,
                        parentCredentials: true,
                        shallow: true,
                        trackingSubmodules: false
                      ]
                    ],
                    userRemoteConfigs: [[
                        credentialsId: 'jenkins-credentials-ssh-github',
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
      stage("Resolve Release Version") {
        // Preparation of environment variables and version resolution
        // - env.TAG - contains the tag that will be needed during build or pull images (depending on branch name)
        // - env.PUBLISH_TAGS - contains tags (there may be several) that must be published
        steps {
          script {
            // The current git tag is selected as the current version (ex. "1.0.1")
            baseVersion = sh(script: "git describe --tags --abbrev=0", returnStdout: true).trim()
            echo "Latest git tag: ${baseVersion}"
            env.TAG = resolveBaseTag(baseVersion)
            echo "Resolved base Docker tag: ${env.TAG}"
            if (env.GIT_REPOSITORY_BRANCH == "master"){
              // If the current branch is master, we prepare an increased version for release in advance (for a new git tag and docker tag).
              releaseVersion = incrementPatchVersion(baseVersion)
              echo "Version bump: from ${baseVersion} to ${releaseVersion}"
              env.PUBLISH_TAGS = resolvePublishTags(releaseVersion).join(" ")
            } else {
              // If the branch is feature or staging, the version is not increased. Build tags are simply collected
              env.PUBLISH_TAGS = resolvePublishTags(baseVersion).join(" ")
            }
            echo "Tags to be published: ${env.PUBLISH_TAGS}"
          }
        }
      }
      stage('Build Docker Images for non-master branch'){
        // This stage should always run IF build skipping is NOT enabled (default)
        // Build skipping is useful when there is a need to "build" all jobs
        // successfully without running actual build process (e.g. when creating
        // hundreds of job items simultaneously via DSL)
        // Also we do not collect images on the master
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
            echo "Strategy: ${useDockerfile ? 'Dockerfile' : 'Docker Compose'}"
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
        // A pull image with the tag "<version>-staging" must occur on the master branch. This way, existing images can be promoted
        when {
          environment name: 'GIT_REPOSITORY_BRANCH', value: 'master'
        }

        steps {
          script {
            echo "Strategy: ${useDockerfile ? 'Dockerfile' : 'Docker Compose'}"
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
                // Images with the tag “env.TAG” that are equal to ‘<version>-staging’ (from the resolveBaseTag()) will be pulled
                def status = sh(script: "docker-compose pull", returnStatus: true)
                if (status != 0) {
                    error("Failed to pull Docker Compose images")
                }
                def images = sh(script: "docker-compose config | awk '/image:/ {print \$2}'", returnStdout: true).trim().split("\n")
                for (img in images) {
                    def exists = sh(
                        script: "docker image inspect ${img} > /dev/null 2>&1",
                        returnStatus: true
                    )
                    if (exists != 0) {
                        error("Image ${img} is missing after pull")
                    } else {
                        echo "Verified image: ${img}"
                    }
                }
            }
          }
        }
      }

      stage("Tag & Publish Images") {
        // Regardless of the GOT_REPOSITORY_BRANCH all builded/pulled images 
        // will be retaged and pushed to the registry (if the parameter “DOCKER_RUN_PUSH”=true)
        steps {
          script {
            List images = []
            def tags = env.PUBLISH_TAGS.tokenize(" ")
            echo "Tags to process: ${tags}"
            echo "Strategy: ${useDockerfile ? 'Dockerfile' : 'Docker Compose'}"
            if (useDockerfile){
              for (tag in tags) {
                // infraImageTagFromGitRepo(tag) will automatically substitute DOCKER_REGISTRY_HOST, the image name and the image tag
                def image = tagDockerfileImage(dockerfileBuildImageId, infraImageTagFromGitRepo(tag))
                images.add(image)
              }
            } else{
              for (tag in tags) {
                images = images + tagDockerComposeImages(tag)
              }
            }
            echo "Images prepared: ${images}"
            if (params.DOCKER_RUN_PUSH){
              echo "DOCKER_RUN_PUSH is set to true, publishing images"
              for (img in images){
                echo "Pushing image: ${img}"
                publishDockerImage(img)
              }
            } else{
              echo "DOCKER_RUN_PUSH is set to false, skipping service"
            }
          }
        }
      }
    }
    post { 
      success {
        script {
          if (env.GIT_REPOSITORY_BRANCH == 'master' && params.DOCKER_RUN_PUSH){
            echo "About to bump git tag from ${baseVersion} to ${releaseVersion} and push to repository"

            def lastNonMergeCommit = sh(
              script: "git rev-list --no-merges -n 1 HEAD",
              returnStdout: true
            ).trim()
            echo "Last non-merge commit: ${lastNonMergeCommit}"
            sshagent(['jenkins-credentials-ssh-github']) {
                withCredentials([
                  usernamePassword(
                    credentialsId: 'GIT',
                    usernameVariable: 'GIT_USER',
                    passwordVariable: 'GIT_PASS'
                  )
                ]) {
                  sh """
                      git config user.name "${GIT_USER}"
                      git config user.email "${GIT_USER}@gmail.com"
                      git tag -a ${releaseVersion} -m "Release ${releaseVersion}" ${lastNonMergeCommit}
                      git push origin ${releaseVersion}
                """
              }
            }
            addBadge (text: releaseVersion, cssClass: 'badge-text--background badge-text--bordered')
          }
        }
      }
      cleanup {
        //// A.Knyazev, SII-13314
        // all container/image destruction procedures moved here
        // from other actions since post-build order is unpredictable
        // but 'cleanup' surely run at last
        script {
            if (useDockerfile) {
              if (dockerfileBuildImageId) {
                // sh("docker rm --force --volumes '${tempContainerId}' || true")
                sh("docker image rm '${dockerfileBuildImageId}' --force")
              }
            } else {
              sh("docker-compose down --volumes || true")
              postCleanupDockerCompose()
            }
        }
      }
    }
  }
}

def incrementPatchVersion(version) {
  // increments the "patch" part of the current version. To increase "major" and "minor" parts, we must do this manually
  def parts = version.tokenize(".")

  if (parts.size() != 3) {
    error("Version format must be major.minor.patch")
  }
  def major = parts[0].toInteger()
  def minor = parts[1].toInteger()
  def patch = parts[2].toInteger()
  patch++
  return "${major}.${minor}.${patch}"
}

def resolvePublishTags(version){
  // Resolves which tags should be published
  if (env.GIT_REPOSITORY_BRANCH == "master"){
    // if branch = master, the pipeline must publish an image with the tag "<version>"
    return [env.GIT_REPOSITORY_BRANCH, version]
  }
  else if (env.GIT_REPOSITORY_BRANCH == "staging"){
    // if branch = staging, the pipeline must publish images with the tags "<version>-staging" and "<version>-staging.<build_number>"
    // - "<version>-staging" will be used for promotion later on
    // - "<version>-staging.<build_number>" will be used for deploying a specific staging state
    return [env.GIT_REPOSITORY_BRANCH, "${version}-staging", "${version}-staging.${env.BUILD_NUMBER}"]
  } else {
    // if it is a feature branch, then the pipeline must publish images with a tag that corresponds to “git describe --tags” (ex: 1.0.1-1-g22c39cc)
    return [env.GIT_REPOSITORY_BRANCH, sh(script: "git describe --tags", returnStdout: true).trim()]
  }
}

def resolveBaseTag(version){
  // Resolves the tag that will be used to build/pull the image (depending on the branch)
  if (env.GIT_REPOSITORY_BRANCH == "staging" || env.GIT_REPOSITORY_BRANCH == "master"){
    // if branch = staging or master, then the base tag must be the “<version>-staging”
    return "${version}-staging"
  } else {
    // if it is a feature branch, then the pipeline must BUILD images with a tag that corresponds to “git describe --tags” (ex: 1.0.1-1-g22c39cc)
    return sh(script: "git describe --tags", returnStdout: true).trim()
  }
}
