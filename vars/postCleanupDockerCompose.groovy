def call() {
    def scriptContent = """
        for img in \$(docker-compose config | awk '/image:/ {print \$2}'); do
            IMAGE_ID=\$(docker images -q "\$img")
            echo "Cleaning up image=\$img with image_id=\$IMAGE_ID ..."
            docker image rm "\$IMAGE_ID" --force
        done
    """

    sh(script: scriptContent)
}