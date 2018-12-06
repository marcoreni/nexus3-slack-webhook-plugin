FROM maven:3.5.2 as builder
MAINTAINER reni.marco@gmail.com
COPY . /build
WORKDIR /build
RUN mvn versions:set -DnewVersion=docker; mvn clean package

FROM sonatype/nexus3:3.13.0
USER root
RUN mkdir -p /opt/sonatype/nexus/system/it/marcoreni/nexus3-slack-webhook-plugin/docker/
COPY --from=builder /build/target/nexus3-slack-webhook-plugin-docker.jar /opt/sonatype/nexus/system/it/marcoreni/nexus3-slack-webhook-plugin/docker/
COPY --from=builder /build/target/feature/feature.xml /opt/sonatype/nexus/system/it/marcoreni/nexus3-slack-webhook-plugin/docker/nexus3-slack-webhook-plugin-docker-features.xml
COPY --from=builder /build/pom.xml /opt/sonatype/nexus/system/it/marcoreni/nexus3-slack-webhook-plugin/docker/nexus3-slack-webhook-plugin-docker.pom
RUN echo '<?xml version="1.0" encoding="UTF-8"?><metadata><groupId>it.marcoreni</groupId><artifactId>nexus3-slack-webhook-plugin</artifactId><versioning><release>docker</release><versions><version>docker</version></versions><lastUpdated>20170630132608</lastUpdated></versioning></metadata>' > /opt/sonatype/nexus/system/it/marcoreni/nexus3-slack-webhook-plugin/maven-metadata-local.xml
RUN echo "mvn\:it.marcoreni/nexus3-slack-webhook-plugin/docker = 200" >> /opt/sonatype/nexus/etc/karaf/startup.properties
USER nexus
