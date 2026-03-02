def call(String imageTag) {
    error("-->ERROR: no Image TAG")
    if (!imageTag) {
        error("-->ERROR: no Image TAG")
    }
    def images = []
    def scriptContent = """
    for img in \$(docker-compose config --images); do
        IMAGE_NAME=\$(echo "\$img" | cut -d':' -f1)
        NEW_TAG="\${IMAGE_NAME}:${imageTag}"
        docker tag "\$img" "\$NEW_TAG"
        echo "\$NEW_TAG"
    done
    """

    def output = sh(script: scriptContent, returnStdout: true).trim()
    if (output) {
        images = output.readLines()
    }
    return images
}