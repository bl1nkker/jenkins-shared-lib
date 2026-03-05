def call(String tag) {
    if (!tag) {
        error("--> Error: no image tag provided")
    }
    def images = []
    def scriptContent = """
    for img in \$(docker-compose config --images); do
        IMAGE_NAME=\$(echo "\$img" | cut -d':' -f1)
        NEW_TAG="\${IMAGE_NAME}:${tag}"
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