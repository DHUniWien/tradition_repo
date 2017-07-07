#!/bin/sh

CONFIG_FILE=$1
SSH_KEYS_DIR=ssh-keys

. ${CONFIG_FILE}

/bin/chmod 600 ${SSH_KEYS_DIR}/storage/ci-id_rsa

SSH="/usr/bin/ssh \
    -o StrictHostKeyChecking=no \
    -i ${SSH_KEYS_DIR}/storage/ci-id_rsa \
    ci@${DEPLOY_HOST}"

INSTANCE_RUNNING=`$SSH sudo /usr/bin/docker ps --format "{{.Names}}" | grep -e "stemmarest-${INSTANCE}"`
if [[ "${INSTANCE_RUNNING}" ]] ; then
    echo "stop running instance <${NAME}> ..."
    $SSH sudo /usr/bin/docker stop stemmarest-${INSTANCE}
fi

INSTANCE_EXISTS=`$SSH sudo /usr/bin/docker ps -a --format "{{.Names}}" | grep -e "stemmarest-${INSTANCE}"`
if [[ "${INSTANCE_EXISTS}" ]] ; then
    echo "remove instance <${NAME}> ..."
    $SSH sudo /usr/bin/docker rm stemmarest-${INSTANCE}
fi

VOLUME_EXISTS=`$SSH sudo /usr/bin/docker volume ls | awk "{print $2}" | grep -e "${VOLUME}"`
if [[ ! "${VOLUME_EXISTS}" ]] ; then
    echo "create missing volume <${VOLUME}> ..."
    $SSH sudo /usr/bin/docker volume create --name ${VOLUME}
fi

echo "create new container ..."
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
