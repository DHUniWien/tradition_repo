FROM tomcat:8.0-jre8
MAINTAINER Tara L Andrews <tla@mit.edu>
RUN apt-get update && apt-get install -y tomcat8 graphviz
RUN mkdir /var/lib/stemmarest && chown -R tomcat8 /var/lib/stemmarest && chmod -R g+w /var/lib/stemmarest && chmod -R +2000 /var/lib/stemmarest
ADD target/stemmarest.war /usr/local/tomcat/webapps/

