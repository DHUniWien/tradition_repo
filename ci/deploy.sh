#!/bin/sh

CONFIG_FILE=$1
SSH_KEYS_DIR=ssh-keys

source ${CONFIG_FILE}

/bin/chmod 600 ${SSH_KEYS}/storage/ci-id_rsa

SSH="/usr/bin/ssh \
    -o StrictHostKeyChecking=no \
    -i ${SSH_KEYS_DIR}/storage/ci-id_rsa \
    ci@${DEPLOY_HOST}"

$SSH sudo /usr/bin/docker stop stemmarest-${INSTANCE}
$SSH sudo /usr/bin/docker rm stemmarest-${INSTANCE}

$SSH sudo /usr/bin/docker create \
  --name    stemmarest-${INSTANCE} \
  --env     STEMMAREST_HOME=/var/lib/stemmarest/ \
  --user    tomcat8 \
  --publish ${HOST}:${HOST_PORT}:${CONTAINER_PORT} \
  --volume  ${VOLUME}:/var/lib/stemmarest/ \
  --memory  ${MEMORY} \
  --cpu-period=100000 \
  --cpu-quota=100000 \
  --restart always \
${REGISTRY}/${IMAGE}:${TAG}

$SSH sudo /usr/bin/docker start stemmarest-${INSTANCE}
