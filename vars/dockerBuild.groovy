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
            stage("Determine Version") {
                steps {
                    script {
                        def baseVersion
                        if (env.GIT_REPOSITORY_BRANCH == "master") {
                            def currentTag = sh(script: "git tag --sort=-v:refname | head -n 1", returnStdout: true).trim()
                            baseVersion = bump_version(currentTag)
                            echo "About to bump git tag to ${env.NEW_TAG} and push to repository"
                            sh """
                                git tag -a ${baseVersion} -m "Release ${baseVersion}" HEAD
                                git push origin ${baseVersion}
                            """
                        } else {
                            baseVersion = sh(script: "git tag --sort=-v:refname | head -n 1", returnStdout: true).trim()
                        }
                        env.PROMO_TAGS = get_promotion_tags(baseVersion).join(" ")
                        env.TAG = get_base_tag()
                        echo "Determined BASE_TAG: ${env.TAG}"
                        echo "Determined PROMO_TAGS: ${env.PROMO_TAGS}"
                    }
                }
            }
            stage("Docker Build") {
                steps {
                    script {
                        Boolean result
                        if (useDockerfile){
                            Map res = runDockerfileBuild(useDockerfile)
                            (dockerfileBuildImageId, dockerfileBuildContainerId) =
                                res.isSuccessful ? [res.layerId, ''] : ['', res.layerId]
                            echo "Got isSuccessful=${res.isSuccessful}, dockerfileBuildImageId=${dockerfileBuildImageId}, dockerfileBuildContainerId=${dockerfileBuildContainerId}"
                        } else {
                            result = runDockerComposeBuild()
                            if (!result){
                                error('Docker build failure')
                            }
                        }
                    }
                }
            }
            stage("Retag & Push") {
                steps {
                    script {
                        if (params.DOCKER_RUN_PUSH){
                            if (useDockerfile){
                                // postPushInfraDockerImage(dockerfileBuildImageId, infraImageTagFromGitRepo())
                                infraImageTagFromGitRepo()
                            } else{
                                Boolean result = runDockerComposePush()
                                if (!result){
                                    error('Docker push failure')
                                }
                                Boolean result2 = runDockerComposeRetagPush()
                                if (!result2){
                                    error('Docker retag and build failure')
                                }
                            }
                        } else{
                            echo "DOCKER_RUN_PUSH is set to false, skipping service "
                        }
                    }
                }
            }
            stage("Cleanup") {
                steps {
                    script {
                        // todo: this also must delete promoted tags
                        sh """
                            for p in \$(docker-compose config | grep 'image:' | awk -F \\: '{ print \$2 }'); do
                                i=\$(docker images -q \$p)
                                [[ -n \$i ]] && docker rmi \$i --force
                            done
                        """
                    }
                }
            }
        }
    }
}

def bump_version(version) {
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

def get_promotion_tags(version){
    if (env.GIT_REPOSITORY_BRANCH == "master"){
        return [version]
    }
    else if (env.GIT_REPOSITORY_BRANCH == "staging"){
        return ["${version}-staging", "${version}-staging.${env.BUILD_NUMBER}"]
    } else {
        return []
    }

}

def get_base_tag(){
    if (env.GIT_REPOSITORY_BRANCH == "master"){
        return "master"
    }
    else if (env.GIT_REPOSITORY_BRANCH == "staging"){
        return "staging"
    } else {
        return sh(script: "git describe --tags", returnStdout: true).trim()
    }
}
