FROM ubuntu:20.04

ARG MEM
ENV MEM=${ARG}

RUN apt update
RUN apt install openjdk-17-jre openjdk-17-jdk maven -y
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

ADD .mvn/ .mvn/
ADD src/ src/
ADD mvnw .
ADD mvnw.cmd .
ADD pom.xml .

RUN sed -e 's/\r$//' mvnw > mvnw_unix
RUN chmod +x mvnw_unix

entrypoint ./mvnw_unix spring-boot:run -Dspring-boot.run.jvmArguments="-Xms${MEM}"
