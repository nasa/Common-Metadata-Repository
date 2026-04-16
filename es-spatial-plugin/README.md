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

## ES Spatial Lib with Elastic 8.19.14 changes and Java 17

Due to changes in 8.x requirements for plugins, we updated the es-spatial-lib plugin in the following ways to be able to work with 8.x

- Updated plugin-security.policy to grant reflection permissions
- Updated scripts to bundle all required dependencies into the final standalone uber jar
- Updated code to include mandatory API changes from ES 8.x

This plugin is meant to be generated separately and installed to your external elastic cluster as a plugin. In order to do that, you must first build a standalone jar.
Here are the instructions on how to create the zip and install on your remote ES:

- Step 1: Generate the ZIP File
  - Check your java version, we are using Java 17 to build this plugin. 
    - The java version you build the plugin with should match java.version in the es-spatial-plugin/resources/plugin/plugin-descriptor.properties file
  - Open your terminal, navigate to the spatial plugin directory, and run the packaging alias:
    - `cd es-spatial-plugin`
    - `lein package-es-plugin`

- Step 2: Locate the Artifact
  - Once the command finishes, your ZIP file will be located here:
  `es-spatial-plugin/target/cmr-es-spatial-plugin-0.1.0-SNAPSHOT.zip`

- Step 3: Create External ES cluster
  - If you don't have an external ES cluster running in a docker container already, following these steps:
    - Create two working dir folders (one for each cluster type) (ex. zip-gran-elastic-8.19 and zip-elastic-8.19)
    - Create a docker-compose.yml file in each dir (example at bottom of the readme. Each cluster will get a slightly diff docker compose yml file content)
    - Copy the `es-spatial-plugin/resources/plugin/plugin-security.policy` file into each directory
  - Run the docker
    - Make sure your docker desktop is running
    - Open a terminal for each dir and run `docker compose up`
      - NOTE: do NOT run these clusters at the same time in your local as they could overwhelm your memory
    - You should see your clusters start in the docker desktop. Take note of their names. In this example, their names are zip-elastic-819-elasticsearch-1 and zip-gran-elastic-819-elasticsearch-1

- Step 4: Install in External Elasticsearch
  - To install this into your external clusters (e.g., your zip-elastic-819-elasticsearch-1 container), run:
    - Go into your working dir from terminal
      - `cd zip-elastic-8.19`
    - Copy the built plugin ZIP from the plugin project output
      - `cp <path-to-CMR-repo>/es-spatial-plugin/target/cmr-es-spatial-plugin-0.1.0-SNAPSHOT.zip .`
    - Copy the zip to each container
      - `docker cp cmr-es-spatial-plugin-0.1.0-SNAPSHOT.zip zip-elastic-819-elasticsearch-1:/tmp/`
    - Copy the security policy to each container
      - `docker cp plugin-security.policy zip-elastic-819-elasticsearch-1:/usr/share/elasticsearch/config/plugin-security.policy`
    - Run the elasticsearch-plugin install command in each container
      - `docker exec -it zip-elastic-819-elasticsearch-1 bin/elasticsearch-plugin install file:///tmp/cmr-es-spatial-plugin-0.1.0-SNAPSHOT.zip`
    - Restart both containers to load the plugin
      - `docker restart zip-elastic-819-elasticsearch-1`

- Step 5: Test with CMR
  - Bring up CMR with (reset :elastic :external)
    - the first time I ran the CMR, I needed to comment out (bootstrap/bootstrap started-system)
      from access-control-app/src/cmr/access_control/system.clj because the ACLs already exist. The CMR won't come up because access control fails because the ACLs
      already exist.
  - Ingest a collection record that contains a spatial extent - mine used a bounding box.
  - Search for that record using a spatial extent search ( a spatial extent search will invoke the spatial plugin.)  You should get your search
    response back without crashing elastic search.

### docker-compose.yml for ES 8.19.14 for GRAN CLUSTER for local runs

```
services:
  elasticsearch:
    image: elasticsearch:8.19.14
    ports:
      - 9210:9200
      - 9300:9300
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - indices.id_field_data.enabled=true
      - ES_JAVA_OPTS=-Xms2g -Xmx2g -Djava.security.policy=/usr/share/elasticsearch/config/plugin-security.policy
    volumes:
      - ./esdata:/usr/share/elasticsearch/data
    networks:
      elasticsearch-network:
        aliases:
          - elasticsearch
  kibana:
    container_name: cmr-gran-kibana
    depends_on: 
      - elasticsearch
    image: docker.elastic.co/kibana/kibana:8.19.14
    ports:
      - 5601:5601
    environment:
      - XPACK_SECURITY_ENABLED=false
      - XPACK_ENCRYPTEDSAVEDOBJECTS_ENCRYPTIONKEY=something_at_least_32_characters_long_123
      - XPACK_REPORTING_ENCRYPTIONKEY=something_at_least_32_characters_long_456
      - XPACK_SECURITY_ENCRYPTIONKEY=something_at_least_32_characters_long_789
    networks:
      - elasticsearch-network

networks:
 elasticsearch-network: {}
```

### docker-compose.yml for ES 8.19.14 for NON-GRAN CLUSTER for local runs

```
services:
  elasticsearch:
    image: elasticsearch:8.19.14
    ports:
      - 9211:9200
      - 9301:9300
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - indices.id_field_data.enabled=true
      - ES_JAVA_OPTS=-Xms2g -Xmx2g -Djava.security.policy=/usr/share/elasticsearch/config/plugin-security.policy
    volumes:
      - ./esdata:/usr/share/elasticsearch/data
    networks:
      elasticsearch-network:
        aliases:
          - elasticsearch
  kibana:
    container_name: cmr-kibana
    depends_on: 
      - elasticsearch
    image: docker.elastic.co/kibana/kibana:8.19.14
    ports:
      - 5602:5601
    environment:
      - XPACK_SECURITY_ENABLED=false
      - XPACK_ENCRYPTEDSAVEDOBJECTS_ENCRYPTIONKEY=something_at_least_32_characters_long_123
      - XPACK_REPORTING_ENCRYPTIONKEY=something_at_least_32_characters_long_456
      - XPACK_SECURITY_ENCRYPTIONKEY=something_at_least_32_characters_long_789
    networks:
      - elasticsearch-network

networks:
 elasticsearch-network: {}
```
## License

Copyright © 2021-2025 NASA
