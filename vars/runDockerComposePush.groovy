Boolean call() {
    echo "Pushing images"
    shellExitCode = sh(script: libraryResource("runDockerComposePush.sh") , returnStatus: true)
    return shellExitCode == 0
}
