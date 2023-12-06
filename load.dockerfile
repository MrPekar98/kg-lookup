FROM ubuntu:20.04

RUN apt update
RUN apt install openjdk-11-jdk wget zip -y

WORKDIR /jena
RUN wget https://archive.apache.org/dist/jena/binaries/apache-jena-4.2.0.zip
RUN unzip apache-jena-4.2.0.zip
RUN rm apache-jena-4.2.0.zip
WORKDIR apache-jena-4.2.0/

ENTRYPOINT ./bin/tdbloader --loc /tdb /rdf