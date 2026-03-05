List call(String imageId, String tag) {
    if (!imageId) {
        error("--> Error: no image ID provided")
    }

    if (!tag) {
        error("--> Error: no image tag provided")
    }

    echo "Tag image parameters: ID=${imageId}, TAG=${tag}"
    cmd = "docker tag '${imageId}' '${tag}'"

    def status = sh(script: cmd, returnStatus: true)

    if (status){
        error("-->ERROR: bad status: ${status}")
    }
    return "${tag}"
}
