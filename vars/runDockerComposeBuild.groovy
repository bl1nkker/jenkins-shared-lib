Boolean call() {
    echo "Docker Compose configuration has been specified for this pipeline, using docker-compose instead of a plain Docker build"
    String dockerBuildCacheArg = params.DOCKER_USE_CACHE ? '' : '--no-cache'
    Integer shellExitCode = null

    echo "CacheArgs: ${dockerBuildCacheArg}"
    withEnv ([
        "CACHE_ARGS=${dockerBuildCacheArg}",
    ]){
        shellExitCode = sh(script: libraryResource("runDockerComposeBuild.sh"), returnStatus: true)
    }
    return [isSuccessful:(shellExitCode == 0)]
}
