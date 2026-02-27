#!/bin/bash
set -e

source /etc/profile >/dev/null 2>&1

for p in $(docker-compose config --services); do
    echo ">>> Pushing $p, as DOCKER_RUN_PUSH=true"
    docker-compose push "$p"
done

exit 0