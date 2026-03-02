def call(String image) {
    if (!image) {
        error("-->ERROR: no image specified")
    }

    echo "==> Push image parameters: image=${image}"
    cmd = "docker push '${image}'"
    def status = sh(script: cmd, returnStatus: true)
    if (status){
        error ("-->ERROR: bad status: ${status}")
    }
}
