FROM alpine:latest

RUN apk add bash git openjdk8

COPY . /aeron-prometheus-stats/

RUN mkdir -p /opt/aeron-prometheus-stats/lib && \
    cd /aeron-prometheus-stats && \
    ./gradlew fatJar && \
    cp ./build/libs/aeron-prometheus-stats-all-1.0-SNAPSHOT.jar /opt/aeron-prometheus-stats/lib
