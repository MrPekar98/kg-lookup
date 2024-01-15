FROM ubuntu:20.04

RUN apt update
RUN DEBIAN_FRONTEND=noninteractive apt install openjdk-17-jre openjdk-17-jdk maven git -y
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

RUN git clone https://github.com/srdc/virt-jena.git
WORKDIR virt-jena
RUN mvn clean install
WORKDIR ..

ADD .mvn/ .mvn/
ADD src/ src/
ADD mvnw .
ADD mvnw.cmd .
ADD pom.xml .

RUN sed -e 's/\r$//' mvnw > mvnw_unix
RUN chmod +x mvnw_unix

entrypoint ./mvnw_unix spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx${MEM}"
