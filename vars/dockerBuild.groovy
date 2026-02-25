def call(){
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
                    // docker login
                    echo "==> Using credentials ID: DOCKER"
                    withCredentials([usernamePassword(
                                        credentialsId:    'DOCKER',
                                        usernameVariable: 'DOCKER_REGISTRY_USER',
                                        passwordVariable: 'DOCKER_REGISTRY_PASSWORD')]){
                        sh ('echo ${DOCKER_REGISTRY_PASSWORD} | docker login -u ${DOCKER_REGISTRY_USER} --password-stdin ')
                    }
                }
            }
            stage("Docker build"){
                steps{
                    echo "docker build here..."
                    // Retrieve BASE_TAG
                    // if branch == "master": BASE_TAG = master
                    // if branch == "staging": BASE_TAG = staging
                    // if branch == "feature": BASE_TAG = sh("git describe --tags") # 1.0.1-4-gfa58fb7
                    
                    // Docker build (compose or dockerfile) with BASE_TAG
                    // must retrieve docker images as output

                    // Retag builded images:
                    // if branch == "master":
                    //      new_tag = sh("git tag") + 1 # 1.0.2
                    //      git tag new_tag
                    //      git push origin --tags
                    //      NEW_TAG = new_tag
                    //      docker tag image:BASE_TAG image:NEW_TAG
                    //      docker push
                    // if branch == 'staging':
                    //      NEW_TAG = sh("git tag") + "_staging"
                    //      NEW_TAG2 = sh("git tag") + "_staging" + BUILD_NUMBER
                    //      docker tag image:BASE_TAG image:NEW_TAG
                    //      docker tag image:BASE_TAG image:NEW_TAG2
                    //      docker push

                }
            }
        }
    }
}