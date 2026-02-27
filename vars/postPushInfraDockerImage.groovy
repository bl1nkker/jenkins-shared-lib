def call(imageId, imageTag) {
    /// A.Knyazev, SII-13314: do nothing if arguments are bad
    // throwing an exception is a bad idea in post-buil actions
    // that is why the verification is performed
    if (!imageId) {
        echo "-->ERROR: no Image ID"
        return
    }

    if (!imageTag) {
        echo "-->ERROR: no Image TAG"
        return
    }

    echo "==> Push image parameters: ID=${imageId}, TAG=${imageTag}"
    cmd = [ "docker tag '${imageId}' '${imageTag}'", 
      "docker push '${imageTag}'" ].join(' && ')

    echo "--> ${cmd}"
    def status = sh(script: cmd, returnStatus: true)

    if (status){
        echo "-->ERROR: bad status: ${status}"
    }
}
