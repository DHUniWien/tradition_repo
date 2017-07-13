#!/bin/sh

function run {

    cd ${SOURCES}
    [ -d context ] || mkdir context

    echo "building stemmarest ..."
    # should we clean up ?
    if [[ $RUN_TESTS ]]; then
        mvn --quiet package
    else
        mvn --quiet -DskipTests package
    fi

    echo "building docker image ..."
    /bin/cp target/stemmarest.war context/
    /bin/cp build/server.xml context/
    /bin/cp build/tomcat-users.xml context/
    /bin/sed -i s/CHANGE_THIS/${PASS}/ context/tomcat-users.xml
    /bin/cp ../ci/resources/Dockerfile-stemmarest-final context/Dockerfile

    cd context

    echo "starting docker container ..."
    docker build --quiet --label "devel=${LABEL}" .
    IMAGE_ID=`docker images --filter "label=devel=${LABEL}" --format '{{.ID}}'`

    docker run --rm --name stemmarest-devel --publish 127.0.0.1:8888:8080 ${IMAGE_ID}

    if [[ -z $KEEP_IMAGE ]]; then
        echo "removing image"
        docker rmi ${IMAGE_ID}
    fi
}

function usage {
    echo "Usage: `basename $0`";
    echo "  -h ..... this usage message"
    echo "  -s ..... stemmarest sources"
    echo "  -l ..... label for temporary container"
    echo "  -t ..... run tests"
    echo "  -p ..... tomcat pass"
    echo "  -k ..... keep docker image (when keeping images the user is expected to clean them up)"
}

while getopts "hs:ltp:k" OPT; do
    case $OPT in
        h)
            usage
            exit 0
        ;;
        s)
            SOURCES=$OPTARG
        ;;
        l)
            LABEL=$OPTARG
        ;;
        t)
            RUN_TESTS=1
        ;;
        p)
            PASS=$OPTARG
        ;;
        k)
            KEEP_IMAGE=1
        ;;
        \?)
            echo "invalid option"
            exit 1
        ;;
    esac
done

# parameter expansion strikes again
SOURCES=${SOURCES:-'.'}
LABEL=${LABEL:-'stemmarest-devel-wurschtl'}
PASS=${PASS:-'stemmarest-devel-wurschtl'}

run
