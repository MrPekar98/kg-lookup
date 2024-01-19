#!/bin/bash

set -e

LUCENE_DIR=$1
GRAPH_NAME=$2
MEM=$3

if [[ ! -d ${LUCENE_DIR} ]]
then
  echo "Relative Lucene directory '${LUCENE_DIR}' does not exist"
  exit 1
fi

docker build -t kg-lookup .
docker run -it --network kg-lookup-network \
        -v ${PWD}/${LUCENE_DIR}:/lucene \
        -p 7000:7000 \
        -e MEM=${MEM} \
        -e GRAPH=${GRAPH_NAME} \
        -e VIRTUOSO=$(docker exec vos bash -c "hostname -I") \
        --name kg-lookup-service kg-lookup