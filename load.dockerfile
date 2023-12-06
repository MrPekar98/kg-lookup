FROM ubuntu:20.04

RUN apt update
RUN apt install openjdk-11-jdk wget zip -y

WORKDIR /jena
RUN wget https://dlcdn.apache.org/jena/source/jena-4.10.0-source-release.zip
RUN unzip jena-4.10.0-source-release.zip
RUN rm jena-4.10.0-source-release.zip
WORKDIR jena-4.10.0/

ENTRYPOINT ./apache-jena/bin/tdbloader --loc /tdb /rdf