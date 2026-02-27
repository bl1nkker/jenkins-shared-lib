String call(String tag) {
    // returning Docker image tag basing on Git repository name
    // Repository name format: <sub>-<name>
    // Result TAG format: ${DOCKER_REGISTRY_HOST}/<sub>/<name>:${BRANCH}

    // This routine may be called from post-build actions where throwing an exception is a crime
    // Catch any exception, log it and return null if so.
    def imageTag = null
    try {
        echo "--> Calculating docker image tag"

        //// A.Knyazev, SII-13334: 
        /// these lines are commented out but leaved here for possible future debug
        //echo "--> Registry URL: ${env.DOCKER_REGISTRY_URL}"
        //echo "--> Repository URL: ${env.GIT_REPOSITORY_URL}"
        //echo "--> Branch: ${env.GIT_REPOSITORY_BRANCH}"

        String gitRepoSub = ''
        String gitRepoRest = ''

        // Check if current build has image prefix set for special cases
        if (env.getEnvironment().getOrDefault('RND_IMAGE_PREFIX', null)) {

            // IMPORTANT: Repository LOCATION is always "cdt-clients-clientcode123/repository-name.git"
            //            by convention

            (gitRepoSub, gitRepoRest) = env.GIT_REPOSITORY_URL
                                           .replaceFirst('^((git|ssh|https?|file)://)?[^:/]+[:/]+','')
                                           .replaceFirst('\\.git$', '')
                                           .split('/')[-2..-1]

            // Add prefix to image, tag construct is subject to change
            // in the future
            gitRepoSub = env.RND_IMAGE_PREFIX.trim() + gitRepoSub
        } else {
            (gitRepoSub, gitRepoRest) = env.GIT_REPOSITORY_URL
                                           .split('/')[-1]
                                           .replaceFirst('\\.git$', '')
                                           .split('-', 2)
        }

        // Assemble image tag
        imageTag = [[env.DOCKER_REGISTRY_HOST, gitRepoSub, gitRepoRest].join('/'), tag].join(':')
        echo "--> Calculated image tag: ${imageTag}"
    } catch (Exception e) {
        echo "===> ERROR: unable to compose Docker image tag: ${e}"
    }

    return imageTag
}
