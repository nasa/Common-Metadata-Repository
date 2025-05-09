# cmr-es-spatial-plugin

A Elastic Search plugin that enables spatial search entirely within elastic.

To package es dependencies:

`lein with-profile es-deps,provided uberjar`

or

`lein install-es-deps`

This will package dependencies in es-deps/. These deps must be included in the
elasticsearch classpath.

To create the spatial script uberjar for use in elasticsearch:

`lein with-profile es-plugin,provided uberjar`

or

`lein install-es-plugin`

To package the spatial script for use in elastic:

`lein package-es-plugin`

This will create a zip in target/ ready for installation in elasticsearch.


Elastic Search 8.x - tested on 8.0.0, 8.5.0, 8.10.4, 8.15.5 - These instructions were generated May 2025.
1) compile CMR

2) After CMR has been compiled change the following:
   es-spatial-plugin/project.clj:
     change the provided profile:
       set the elastic version to "8.15.5" 
       in the exclusions section for both cmr-common-lib and cmr-spatial-lib comment out the following:
         ;[com.fasterxml.jackson.core/jackson-core]
         ;[com.fasterxml.jackson.dataformat/jackson-dataformat-cbor]
         ;[com.fasterxml.jackson.dataformat/jackson-dataformat-smile]

   es-spatial-plugin/resources/plugin/plugin-descriptor.properties so that elasticsearch.version=8.15.5

  To get the spatial plug in to work on Elastic Search 8.x I had to only use the cmr-es-spatial-plugin-0.1.0-SNAPSHOT.jar

  then create the jar file

  lein with-profile es-plugin,provided jar 


3) Create a directory to work in.

4) create a zip directory in your working directory.  I called mine zip8.15 and copied in
es-spatial-plugin/target/cmr-es-spatial-plugin-0.1.0-SNAPSHOT.jar
es-spatial-plugin/resources/plugin/plugin-descriptor.properties 

In the directory above the zip file (the working directory) copy 
es-spatial-plugin/resources/plugin/plugin-security.policy

The cmr-es-spatial-plugin-0.1.0-SNAPSHOT.jar contains the compiled .class bytecode of the plugin classes.

5) in the es-spatial-plugin project run
lein deps :tree
This command gives a heirarchical listing of dependency libraries. Most of these libraries we do not
need because they are either for testing or for parts of cmr-common, that the spatial library does not use.

Then I decided on a set of jar files that I knew I needed and went into my personal maven repository and copied the
libraries into my zip8.15 directory. My maven repository is located in my home directory under a directory called .m2

The set of jar files that I ended up with are:
clojure-utils-0.6.1.jar			core.matrix-0.54.0.jar			jackson-dataformat-cbor-2.13.3.jar
math.combinatorics-0.1.4.jar		randomz-0.3.0.jar			assertions-0.2.0.jar
cmr-common-lib-0.1.1-SNAPSHOT.jar	edn-java-0.4.4.jar			jackson-dataformat-smile-2.13.3.jar
mathz-0.3.0.jar				spec.alpha-0.3.218.jar			cheshire-5.12.0.jar
cmr-es-spatial-plugin-0.1.0-SNAPSHOT.jar jafama-2.3.1.jar			plugin-descriptor.properties
vectorz-0.44.0.jar			clojure-1.11.2.jar			cmr-spatial-lib-0.1.0-SNAPSHOT.jar
jackson-core-2.13.3.jar			joda-time-2.8.1.jar			primitive-math-0.1.4.jar
vectorz-clj-0.28.0.jar
 
The way that I figured out the above jars is the following:

Bring up Elastic Search 8.15.8:
6) start up docker - I use docker desktop
7) Use the directory that was created in step 3. You will run elastic search from here.
8) Create a file in this directory called docker-compose.yml. Put in the following into the file and save it:
    Note: it is best not to use a gui to create the file - upon another developer testing these instructions extra spaces
          were inserted and we had to fix the copy and paste errors.

services:
  elasticsearch:
    image: elasticsearch:8.15.5
    ports:
      - 9210:9200
      - 9300:9300
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - ES_JAVA_OPTS=-Djava.security.policy=/usr/share/elasticsearch/config/plugin-security.policy
    volumes:
      - ./esdata:/usr/share/elasticsearch/data
    networks:
      elasticsearch-network:
        aliases:
          - elasticsearch
#  kibana:
#    container_name: cmr-kibana
#    depends_on: 
#      - elasticsearch
#    image: docker.elastic.co/kibana/kibana:8.17.5
#    ports:
#      - 5601:5601
#    networks:
#      - elasticsearch-network

networks:
 elasticsearch-network: {}

9) Note that you don't need the kibana section - as it just creates more junk in the logs to have to look at, so it is commented out.
   I included here so that others may see how to bring up both if they need it sometime in the future.

10) Open up another termial window to the same directory.  In this new termainal and in the same directory where docker-compose.yml exists run the command: 
   docker compose up

   This will load up the elastic search 8.15.5 image from the web and store it in your docker image. Then will will start up elastic search 8.15.5.
   It will also create a new directory called esdata in your directory. This is where elastic search will store its data so that the data is retained
   after elastic search is stopped and restarted. This window will also print the elastic search logs. 

11) Once it has started, go back INTO your zip directory (mine is called zip8.15) and zip up the files using the following command:
zip -r cmr-es-spatial-plugin-8.15.5.zip \
clojure-1.11.2.jar \
spec.alpha-0.3.218.jar \
cmr-common-lib-0.1.1-SNAPSHOT.jar \
cmr-spatial-lib-0.1.0-SNAPSHOT.jar \
jackson-dataformat-cbor-2.13.3.jar \
jackson-core-2.13.3.jar \
jackson-dataformat-smile-2.13.3.jar \
joda-time-2.8.1.jar \
primitive-math-0.1.4.jar \
jafama-2.3.1.jar \
core.matrix-0.54.0.jar \
vectorz-clj-0.28.0.jar \
clojure-utils-0.6.1.jar \
vectorz-0.44.0.jar \
mathz-0.3.0.jar  \
randomz-0.3.0.jar \
edn-java-0.4.4.jar \
assertions-0.2.0.jar \
math.combinatorics-0.1.4.jar \
cheshire-5.12.0.jar \
cmr-es-spatial-plugin-0.1.0-SNAPSHOT.jar \
plugin-descriptor.properties

12) copy cmr-es-spatial-plugin-8.15.5.zip to your working directory

13) run the following to get the plugin into the docker container - my docker container name is es-spatial-plugin-elasticsearch-1.
   if yours is different then you will need to modify the command to use your container name. You will get an error when trying to copy if 
   your container name is wrong. You can find your container name in docker desktop.

docker cp cmr-es-spatial-plugin-8.15.5.zip es-spatial-plugin-elasticsearch-1:/tmp/

14) copy the plugin-security.policy that is in your working directory from step 4 to the config directory in the elastic container:
docker cp plugin-security.policy es-spatial-plugin-elasticsearch-1:/usr/share/elasticsearch/config/plugin-security.policy

15) run the following command to run a shell that executes inside the docker container
docker exec -it es-spatial-plugin-elasticsearch-1 /bin/bash

16) run the following command to install the spatial plugin
elasticsearch-plugin install file:///tmp/cmr-es-spatial-plugin-8.15.5.zip

The plugin should install

17) restart Elastic search: in the docker desktop click on the vertical ... on the right side of the elastic search container and select restart

18) Bring up your external CMR database

19) bring up CMR
    since I am using an external database and elastic, after the first time I ran the CMR, I needed to comment out (bootstrap/bootstrap started-system) 
from access-control-app/src/cmr/access_control/system.clj because the ACLs already exist. The CMR won't come up because access control fails because the ACLs
already exist.

20) ingest a collection record that contains a spatial extent - mine used a bounding box.

21) search for that record using a spatial extent search ( a spatial extent search will invoke the spatial plugin.)  You should get your search
response back without crashing elastic search. 

During the process of figuring out the libraries, elastic search would crash. In the elastic search logs, either from the container or the terminal
where I ran docker compose up, look for Cause and also ClassNotFoundException. Then I figured out which library contained that class. Then I copied
that jar file into my zip directory, zipped up my file. Then I had to delete my elastic search container, delete the esdata directory, bring down CMR.
Then I had to follow the above instructions to bring everything back up and try again.  I kept up this process, until I got search results back without
crashing elastic search.


## License

Copyright © 2021 NASA
