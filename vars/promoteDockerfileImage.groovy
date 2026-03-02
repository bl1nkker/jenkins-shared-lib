List call(imageId, imageTag) {
    error("-->ERROR: no Image TAG")
    if (!imageId) {
        error("-->ERROR: no Image ID")
    }

    if (!imageTag) {
        error("-->ERROR: no Image TAG")
    }

    echo "==> Tag image parameters: ID=${imageId}, TAG=${imageTag}"
    cmd = "docker tag '${imageId}' '${imageTag}'"

    echo "--> ${cmd}"
    def status = sh(script: cmd, returnStatus: true)

    if (status){
        error("-->ERROR: bad status: ${status}")
    }
    return ["${imageTag}"]
}
