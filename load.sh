#!/bin/bash

set -e

DIR=$1

if [[ ! -d ${DIR} ]]
then
  echo "Directory '${DIR}' does not exist"
  exit 1
fi

if [[ "$(docker images -q ${IMAGE})" == "" ]]
then
  docker build -t kg-tdb -f load.dockerfile .
fi

mkdir -p tdb/
docker run --rm -v ${PWD}/${DIR}:/rdf -v ${PWD}/tdb:/tdb kg-tdb