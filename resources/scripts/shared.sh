IMAGE_NEO4J="neo4j:3.3.3"
IMAGE_ES="docker.elastic.co/elasticsearch/elasticsearch:6.2.2"
IMAGE_KIBANA="docker.elastic.co/kibana/kibana:6.2.2"

HOST_NEO4J="cmr-graph-neo4j"
HOST_ES="cmr-graph-elastic"
HOST_KIBANA="cmr-graph-kibana"

PORT_ES=9211

CID_FILE_NEO4J=/tmp/${HOST_NEO4J}-container-id
CID_FILE_ES=/tmp/${HOST_ES}-container-id
CID_FILE_KIBANA=/tmp/${HOST_KIBANA}-container-id

DOCKER_NET="cmr-graph-net"
