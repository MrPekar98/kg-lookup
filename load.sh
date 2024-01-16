#!/bin/bash

set -e

DIR=$1
IMAGE="openlink/virtuoso-opensource-7:7"
NETWORK="kg-lookup-network"

docker network inspect ${NETWORK} >/dev/null 2>&1 || docker network create ${NETWORK}

if [[ ! -d ${DIR} ]]
then
  echo "Directory '${DIR}' does not exist"
  exit 1
elif [[ ! -d import/ ]]
then
  echo "Copying directory..."
  mkdir import/

  for F in ${DIR}/* ;\
  do
    cp ${F} import/
  done

  cp import.isql import/
  echo "Done"
fi

if [[ "$(docker images -q ${IMAGE})" == "" ]]
then
  docker pull ${IMAGE}
fi

docker run --rm --name vos -d \
           -v ${PWD}/database:/database \
           -v ${PWD}/import:/import \
           -t -p 1111:1111 -p 8890:8890 -i ${IMAGE}

sleep 1m
docker exec -it vos isql 1111 exec="SPARQL create GRAPH <http://localhost:8890/graph>"
docker exec -it vos isql 1111 exec="LOAD /import/import.isql"
