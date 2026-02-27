Map call(String useDockerfile) {
  String image

  // IMPORTANT NOTICE: agent SSH session is not interactive so session will lack
  // locale variables (system profile), file sourcing prevents this at build time
  // Related internal issues: SII-13024 (comments), SII-13144
  //
  // Following is based on similar actions dpne in "dockerBuild" library, some
  // SVN and 'products' repo-specific actions were dropped to keep this step to
  // a minimal shell invocation.

  echo 'Plain Dockerfile has been specified for this pipeline, using Docker build instead of Docker Compose'
  String dockerBuildCacheArg = params.DOCKER_USE_CACHE ? '' : '--no-cache'
  String dockerBuildBuildArg = ""
  Integer shellExitCode = null

  // Providing additional arguments
  //// WARNING:
  ////  'TAG' and 'DOCKER_REGISTRY_HOST' are NOT the same as those specified at container startup.
  ////  The original values referenced at host 'docker-compose.yml' are NOT passed to Jenkins build environment.
  ////  The names 'TAG' 'DOCKER_REGISTRY_HOST' is leaved like this for compose-based builds compatibility
  ////  since it is generally used in build-related "docker-compose.yml" files.

  ['MVN_PREFIX', 'MVN_URL', 'ENV_TYPE', 'DOCKER_REGISTRY_HOST', 'TAG'].each { var->
    // filter the variables: provide them as 'build-arg' if present only
    if (env.getEnvironment().containsKey(var) && env.getEnvironment().get(var)) {
        dockerBuildBuildArg += " --build-arg ${var}=${env.getEnvironment().get(var)}"
    }
  }
  echo "BuildArgs: ${dockerBuildBuildArg}"
  echo "CacheArgs: ${dockerBuildCacheArg}"

  withEnv ([
    "CACHE_ARGS=${dockerBuildCacheArg}",
    "BUILD_ARGS=${dockerBuildBuildArg}",
    "DOCKERFILE=${useDockerfile}"
    ]){
    shellExitCode = sh(script: libraryResource("runDockerBuild.sh") , returnStatus: true)
  }

  assert fileExists('.iidfile') : 'Image ID file has not been created while running build command'

  String layerId = readFile('.iidfile')
  assert layerId : 'Resulting build layer ID read from ".iidfile" is empty'

  return [ isSuccessful: (shellExitCode == 0), layerId: layerId ]
}
