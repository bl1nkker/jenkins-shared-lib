#!/bin/bash
set -e

source /etc/profile >/dev/null 2>&1

for img in $(docker-compose config --images); do
    IMAGE_NAME=$(echo "$img" | cut -d':' -f1)
    for t in ${PROMO_TAGS}; do
        echo "Retagging $img to ${IMAGE_NAME}:$t"
        docker tag "$img" "${IMAGE_NAME}:$t"
        docker push "${IMAGE_NAME}:$t"
    done
done

exit 0