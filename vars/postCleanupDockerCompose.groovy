def call() {
    def scriptContent = """
        for img in \$(docker-compose config | awk '/image:/ {print \$2}'); do
            IMAGE_NAME=\$(echo "\$img" | cut -d':' -f1)
            IMAGE_ID=\$(docker images -q "\$IMAGE_NAME")
            echo "Cleaning up image=\$IMAGE_NAME with image_id=\$IMAGE_ID ..."
            docker image rm "\$IMAGE_ID" --force
        done
    """

    sh(script: scriptContent)
}