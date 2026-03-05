#!/bin/bash
set -e

source /etc/profile >/dev/null 2>&1

docker-compose pull --ignore-pull-failures -q --include-deps
for p in $(docker-compose config --services); do
    echo ">>> Building $p"
    docker-compose build ${CACHE_ARGS} "$p"
done

exit 0