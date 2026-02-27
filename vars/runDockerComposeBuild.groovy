Boolean call() {
    // IMPORTANT NOTICE: agent SSH session is not interactive so session will lack
    // locale variables (system profile), file sourcing prevents this at build time
    // Related internal issues: SII-13024 (comments), SII-13144
    //
    // Following is based on similar actions dpne in "dockerBuild" library, some
    // SVN and 'products' repo-specific actions were dropped to keep this step to
    // a minimal shell invocation.

    echo "Building images"
    String dockerBuildCacheArg = params.DOCKER_USE_CACHE ? '' : '--no-cache'
    Integer shellExitCode = null

    echo "CacheArgs: ${dockerBuildCacheArg}"
    withEnv ([
        "CACHE_ARGS=${dockerBuildCacheArg}",
    ]){
        shellExitCode = sh(script: libraryResource("runDockerComposeBuild.sh"), returnStatus: true)
    }
    return shellExitCode == 0
}
