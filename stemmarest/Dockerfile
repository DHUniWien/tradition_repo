FROM tomcat:8.0-jre8
MAINTAINER Tara L Andrews <tla@mit.edu>
RUN apt-get update && apt-get install -y tomcat8 graphviz vim less pwgen
RUN mkdir /var/lib/stemmarest && chown -R tomcat8. /var/lib/stemmarest && chmod -R g+w /var/lib/stemmarest && chmod -R +2000 /var/lib/stemmarest
ADD target/stemmarest.war /usr/local/tomcat/webapps/

ADD build/server.xml /usr/local/tomcat/conf/
# create inital tomcat groups/users and set random password
ADD build/tomcat-users.xml /usr/local/tomcat/conf/
RUN sed -i s/CHANGE_THIS/`pwgen 32`/ /usr/local/tomcat/conf/tomcat-users.xml
