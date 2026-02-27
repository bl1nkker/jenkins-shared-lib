#!/bin/bash

# set 'PATH' correctly since Jenkins provides a very shortened variant
source /etc/profile >/dev/null 2>&1

# Prepare & run Docker build

DOCKER_BUILD_IIDFILE=".iidfile"
DOCKER_BUILD_LOGFILE="${WORKSPACE}/logs/docker_build.log"

test -d "${WORKSPACE}/logs"      || mkdir "${WORKSPACE}/logs"
test -f "${DOCKER_BUILD_LOGFILE}" && rm -vf "${DOCKER_BUILD_LOGFILE}"
test -f "${DOCKER_BUILD_IIDFILE}" && rm -vf "${DOCKER_BUILD_IIDFILE}"

# Depending on the DOCKER_USE_CACHE argument value, the build is performed either with or without Docker cache
docker build \
   -f ${DOCKERFILE} \
   ${CACHE_ARGS} \
   ${BUILD_ARGS} \
   --iidfile "${DOCKER_BUILD_IIDFILE}" \
   . &> >(tee "${DOCKER_BUILD_LOGFILE}")

# Docker build is successful - image ID file exists and is not empty

test -f "${DOCKER_BUILD_IIDFILE}" && DOCKER_BUILD_IMAGE_ID="$(cat "${DOCKER_BUILD_IIDFILE}")"
test -n "${DOCKER_BUILD_IMAGE_ID}" && exit 0

# Docker build failed - try to return container ID instead

DOCKER_BUILD_CONTAINER_ID=$(tac "${DOCKER_BUILD_LOGFILE}" \
    | grep -m1 -iP '^ *-{3}> (Running in )*[0-9a-z]{12}' \
    | grep -ioP '[0-9a-z]{12}')

test -z "${DOCKER_BUILD_CONTAINER_ID}" \
    && echo "ERROR: Unable to find out Container ID" \
    && exit 1

test -z "$(docker container ls -a --quiet | grep "${DOCKER_BUILD_CONTAINER_ID}")" \
    && echo "ERROR: Unable to verify found Container ID ${DOCKER_BUILD_CONTAINER_ID}, it may be wrong" \
    && exit 1

echo "Found out Conteiner ID: ${DOCKER_BUILD_CONTAINER_ID}"
echo -n "${DOCKER_BUILD_CONTAINER_ID}" > "${DOCKER_BUILD_IIDFILE}"
exit 1
