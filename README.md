# Stemmarest
### a graph-based data storage solution for Stemmaweb

Stemmarest is a [Neo4j](http://neo4j.com/)-based repository for variant text traditions (i.e. texts that have been transmitted in multiple manuscripts). The repository is accessed via a REST API, documentation for which [can be found here](https://dhuniwien.github.io/tradition_repo/).

Development of Stemmarest was begun by a team of software engineering students at the [University of Bern](https://www.unibe.ch/), and since 2016 has been continued by the [Digital Humanities group at the University of Vienna](https://acdh.univie.ac.at/) with financial support from the [Swiss National Science Foundation](http://www.snf.ch/en/Pages/default.aspx).

## Downloading

You can get a version of Stemmarest via Docker Hub:

    docker pull dhuniwien/stemmarest:latest
	docker run -d --rm --name stemmarest -p 127.0.0.1:8080:80800 dhuniwien/stemmarest:latest

Alternatively, if you wish to build from source, you can clone this repository and build a Stemmarest WAR file yourself (see below).

## Building

Stemmarest needs to be built using [Maven](http://maven.apache.org/run-maven/index.html#Quick_Start). This can be done either in a suitable Java IDE, or at the command line after the Maven tools have been installed:

    mvn package  # note that this will also run the tests

Make sure, that the package graphviz is installed on your computer. If not, some tests will fail.     

A WAR file will be produced, in `target/stemmarest.war`, that can then be deployed to the Tomcat server of your choice.

## Running

The application has been tested on Tomcat version 9 with JDK 11; to deploy it, copy the WAR file into the `webapps` directory of your Tomcat server.

Stemmarest requires a location for its data storage; by default this is `/var/lib/stemmarest`, but can be changed by setting the environment variable `STEMMAREST_HOME`. The directory specified must have its permissions set so that the Tomcat user can write to it.

Note that if, at any time, you wish to inspect the database visually, you may shut down the Stemmarest server and start an instance of Neo4J at the database directory location. **Make sure that your version of Neo4J matches the version specified in `pom.xml`!**
