FROM maven:3.3.9-jdk-8

LABEL maintainer=ops@dh.univie.ac.at \
      vendor=DHUniWien

RUN apt-get update \
 && apt-get install --yes \
    graphviz \
    pwgen

COPY stemmarest /root/stemmarest
RUN cd /root/stemmarest && mvn package

