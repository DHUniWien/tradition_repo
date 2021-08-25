FROM tomcat:10-jdk11
LABEL vendor=DHUniWien

# Update packages, install Graphviz
RUN apt-get update \
    && apt-get -y upgrade \
    && apt-get install -y graphviz \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Make the data directories
RUN mkdir -p /var/lib/stemmarest/conf \
    && chmod -R g+w /var/lib/stemmarest \
    && chmod -R +2000 /var/lib/stemmarest

# Copy the software and config
COPY target/stemmarest.war /usr/local/tomcat/webapps/
COPY build/server.xml /usr/local/tomcat/conf/
COPY build/tomcat-users.xml /usr/local/tomcat/conf/

# Set the appropriate environment variable
ENV STEMMAREST_HOME /var/lib/stemmarest

# Run the server
EXPOSE 8080
CMD ["catalina.sh", "run"]
