#!/bin/bash

# set 'PATH' correctly since Jenkins provides a very shortened variant
source /etc/profile >/dev/null 2>&1
docker-compose pull --ignore-pull-failures -q --include-deps
# Depending on the DOCKER_USE_CACHE argument value, the build is performed either with or without Docker cache
docker-compose build ${CACHE_ARGS}

exit 0