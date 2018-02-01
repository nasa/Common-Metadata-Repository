### Global constants

CMR_TMP_DIR=/tmp
CMR_LOG_DIR=$CMR_DIR/logs
PID_FILE_PREFIX=$CMR_TMP_DIR/cmr-pid-
CMR_PROJ_VERSION=0.1.0-SNAPSHOT
ORACLE_VERSION=11.2.0.4
ORACLE_GROUP_ID=com.oracle
SQS_DOCKER_REPO=pafortin/goaws
SQS_CONTAINER_ID_FILE=$CMR_TMP_DIR/cmr-sqs-sns-docker-cid
DOCKER_CONTAINER_PREFIX=cmr-
DOCKER_CONTAINER_ID_FILE_PREFIX=$CMR_TMP_DIR/cmr-docker-conatiner-id.

### Utility Functions

# Bash 3 doesn't support associative arrays, so we'll use a function:
function get_cmr_port () {
	APP=$1
	case "$APP" in
		'access-control') echo 3011;;
		'bootstrap') echo 3006;;
		'cubby') echo 3007;;
		'index-set') echo 3005;;
		'indexer') echo 3004;;
		'ingest') echo 3002;;
		'metadata-db') echo 3001;;
		'search') echo 3003;;
		'virtual-product') echo 3009;;
	esac
}

function get_pid_file () {
	PROJ=$1
	echo "${PID_FILE_PREFIX}${PROJ}"
}

function get_pid () {
	PROJ=$1
	PID_FILE=`get_pid_file $PROJ`
    if [ -f "$PID_FILE" ]; then
		echo `cat $PID_FILE`
	fi
}

### Messages

function cmd_not_found () {
    echo "Command not defined; for help message use 'cmr help'"
}

function subcmd_not_found () {
    echo "Subcommand not defined; for help message use 'cmr $@ help'"
}

function required_file_not_found () {
	echo "The following required file was not found:"
	echo "    $1"
}