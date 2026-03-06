def call(String tag) {
    if (!tag) {
        error("--> Error: no image tag provided")
    }
    def images = []

    def scriptContent = """
        for img in \$(docker-compose config | awk '/image:/ {print \$2}'); do
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


// docker-compose config | awk \'/image:/ {print $2}\'
// docker-compose config | grep 'image:' | awk -F \\: '{ print $2 }'