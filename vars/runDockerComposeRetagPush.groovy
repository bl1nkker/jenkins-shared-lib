Boolean call() {
    echo "Retagging and pushing images"
    shellExitCode = sh(script: libraryResource("runDockerComposeRetagPush.sh") , returnStatus: true)
    return shellExitCode == 0
}
