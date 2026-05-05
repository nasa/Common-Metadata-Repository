import json
import os
import sys
import logging
import multiprocessing

import boto3
import oracledb
from datetime import datetime, timedelta, timezone
import requests

import find_s3
from find_logger import setup_logging
from find_db import connect_to_db

GRAN_ELASTIC_PORT = os.getenv("GRAN_ELASTIC_PORT", "9200")
GRAN_ELASTIC_HOST = os.getenv("GRAN_ELASTIC_HOST")
GRAN_ELASTIC_URL = f"http://{GRAN_ELASTIC_HOST}:{GRAN_ELASTIC_PORT}"
EFS_PATH = os.getenv("EFS_PATH")

logger = logging.getLogger("find_granule_counts")

def get_providers(db_connection, s3_client):
    """
    Get a list of all providers that have granules using the CMR database.
    Query the database for all _granule tables. Then queries each granule
    table to see if any exist. Keep only the provider names for tables
    that have granules in them.

    Args:
        db_connection - the passed in database connection
        s3_client - S3 client to save off the list of providers
    
    Returns:
        A list of providers that have granules

    Exceptions:
        Logs error and exits on issue connecting to or querying the DB
    """
        
    search_query = f"""
    select table_name from all_tables where table_name like '%_GRANULES'
    """
    granule_table_list = []
    result = []

    # Parse out the granule table names from the result
    with db_connection.cursor() as curr:
        try:
            curr.execute(search_query)
            granule_table_list = [item[0] for item in curr.fetchall()]
        except oracledb.Error as e:
            raise Exception(f"Failed to execute query: {search_query}\nerror: {e}")

    # Get a list of providers where granules exist.
    with db_connection.cursor() as curr:
        for table in granule_table_list:
            logger.info(f"Checking for granules in METADATA_DB.{table}")
            try:
                search_query = f"""
                SELECT CASE 
                    WHEN EXISTS (SELECT 1 FROM METADATA_DB.{table} WHERE ROWNUM <= 1) 
                    THEN 1 
                    ELSE 0 
                END AS has_rows 
                FROM dual
                """
                curr.execute(search_query)
                has_rows = curr.fetchone()[0]
                if has_rows:
                    result.append(table.replace('_GRANULES', ''))
            except oracledb.Error as e:
                raise Exception(f"Failed to execute query: {search_query}\nerror: {e}")

    logger.debug(f"result {result}")
    find_s3.save_providers_to_s3(s3_client, result)
    return result

def get_collections_per_provider(db_connection, s3_client, providers):
    """
    Queries the database for all collections per provider. Saves to S3
    for each provider a map of the provider and their collections.

    Args:
        db_connection - the passed in database connection
        s3_client - S3 client to save off the provider colletion map per provider
        providers - list of providers to work.
    
    Returns:
        Nothing; the data is saved to S3 for processing by another program

    Exceptions:
       logs error and raises oracle exception
    """

    with db_connection.cursor() as curr:
        for provider in providers:
            table_name = f"{provider}_COLLECTIONS"
            # Query for collections that have not been deleted.
            query = f"""
                SELECT concept_id, max_rev as revision_id
                FROM (SELECT concept_id, MAX(revision_id) AS max_rev, MAX(deleted) 
                      KEEP (DENSE_RANK LAST ORDER BY revision_id) AS is_deleted
                      FROM METADATA_DB.{table_name}
                      GROUP BY concept_id)
                WHERE is_deleted = 0
                ORDER BY concept_id, max_rev DESC
            """
            try:
                curr.execute(query)
                concept_ids = [row[0] for row in curr.fetchall()]

                # Write results to s3
                find_s3.save_to_s3(s3_client, {provider: concept_ids}, provider)

            except oracledb.Error as e:
                logger.exception(f"Error querying {table_name}")
                raise

def get_providers_with_collections():
    """
    Save a set of maps per provider to S3 that contains a provider name that has granules
    with a list of all collections.

    Args:
        None
    
    Returns:
        Nothing; Saves a set of maps per provider that includes the provider name and a
        list of provider collections.

    Exceptions:
        logs error and exits on issue connecting to DB
    """

    logger.info("Starting to get all collections per providers that have granules to audit.")
    
    # Connect to the database
    db_connection = connect_to_db()

    # Initialize the S3 client
    s3_client = boto3.client('s3')
    
    # Get a list of providers through the CMR database by checking to see if the _GRANULES tables contain granules
    providers = get_providers(db_connection, s3_client)

    # Then get all of the collection concept ids per each provider that has granules
    collections = get_collections_per_provider(db_connection, s3_client, providers)

    db_connection.close()

    logger.info("Finished getting all collections per providers that have granules for auditing.")

def get_db_granule_count(db_connection, latest_working_time, provider, collection_concept_id):
    """
    Get the db granule counts for a collection for any granules that has a 
    revision_date before the latest_working_time

    Args:
        db_connection: the connect to the database
        latest_working_time: The latest revision_date for each record to include with the record
                              counts. Any records after this time is not included. This allows for
                              the time differences of when the records get into the database vs ElasticSearch.
        provider: The provider id to query
        collection_concept_id: The collection concept id of the granules to count.
    
    Returns:
        The number of granules that exist in the database before or at the revision_date
        matching the latest_working_time for the collection in the provider.

    Exceptions:
        logs error returns 0 if an error occurs querying the database.
    """
    with db_connection.cursor() as curr:
        table_name = f"{provider}_GRANULES"

        logger.debug(f"Getting granule counts for {collection_concept_id} before {latest_working_time}")

        query = f"""
            SELECT COUNT(*)
            FROM (SELECT concept_id
                  FROM METADATA_DB.{table_name}
                  WHERE PARENT_COLLECTION_ID = :concept_id
                  AND REVISION_DATE <= :latest_working_time
                  GROUP BY concept_id
                  HAVING MAX(deleted) KEEP (DENSE_RANK LAST ORDER BY revision_id) = 0)
        """

        try:
            curr.execute(query,
                         concept_id=collection_concept_id,
                         latest_working_time=latest_working_time)
            count = curr.fetchone()[0]
            return count
        except oracledb.Error as e:
            logger.error(f"Error querying {table_name} for concept_id {collection_concept_id}: {e}")
            return 0

def get_es_indices():
    """
    Get the list of granule indexes in elastic search.

    Args:
        None
    
    Returns:
        The list of granule indexes.

    Exceptions:
        None
    """
    es_indices_response = requests.get(f"{GRAN_ELASTIC_URL}/_cat/aliases?h=alias&format=json")
    es_indices_list = [item['alias'] for item in es_indices_response.json()] 
    return es_indices_list

def find_index(indices, concept_id):
    """
    Find the ElasticSearch index for the specific passed in collection concept id.

    Args:
        indicies: A list of ElasticSearch index names.
        concept_id: The collection concept id to find the correct index name.
    
    Returns:
        The ElasticSearch index of where the granules exist for the collection.

    Exceptions:
        None
    """
    # Convert concept_id to lowercase
    index_name = concept_id.lower().replace("-", "_")

    # Check if any item in data contains the concept_id
    matching_items = [item for item in indices if index_name in item]
        
    if matching_items:
        index = matching_items[0]
    else:
        index = "1_small_collections_alias"
    return index

def get_es_granule_count(index, collection_concept_id, time):
    """
    Get the ElasticSearch granule counts for a collection for any granules that has a 
    revision_date before the passed in time.

    Args:
        index: the index name to where the granules exist for the collection.
        time: The latest revision_date for each record to include with the record
              counts. Any records after this time is not included. This allows for
              the time differences of when the records get into the database vs ElasticSearch.
        collection_concept_id: The collection concept id of the granules to count.
    
    Returns:
        The number of granules that exist in ElasticSearch before or at the revision_date
        matching the time parameter for the collection.

    Exceptions:
        None
    """
    headers = {"Content-Type": "application/json"}
    elastic_base_query = {"query": {"bool": {"must": [{"range": {"revision-date": {"lte": time}}},
                                                      {"match": {"collection-concept-id": collection_concept_id}}]}},
                        "size":0,
                        "track_total_hits": True}

    elastic_response = requests.get(f"{GRAN_ELASTIC_URL}/{index}/_search",
                                                data=json.dumps(elastic_base_query),
                                                headers=headers)
    if elastic_response.status_code >= 300:
        logger.error(f"ERROR: request to elastic failed with error: {elastic_response.text}")
        sys.exit(1)

    granule_hits = elastic_response.json()["hits"]["total"]["value"]
    logger.debug("granule hits\n", granule_hits)
    return granule_hits

def compare_granule_counts(db_connection, latest_working_time, f, provider, collection_concept_ids):
    """
    Compare the database and ElasticSearch granule counts. If the counts differ the create a map for the specific
    collection information and save it so that another program can fix the issues.

    Args:
        db_connection: the connect to the database
        latest_working_time: The latest revision_date for each record to include with the record
                              counts. Any records after this time is not included. This allows for
                              the time differences of when the records get into the database vs ElasticSearch.
        f: A file pointer so that the specific collection information can be saved.
        provider: The provider id to query
        collection_concept_ids: A list of collection concept ids for a provider of the granules to count.
    
    Returns:
        Nothing, but it does save a file consisting of collection and granule count information so that another
        program can find and fix issues.

    Exceptions:
        None
    """
    # get the list of indices from ES.
    indices = get_es_indices()
    first = True

    for collection_concept_id in collection_concept_ids:
        # Query the
        db_granule_count = get_db_granule_count(db_connection, latest_working_time, provider, collection_concept_id)

        if db_granule_count > 0:
            # Now we need to query ES and compare the counts
            index = find_index(indices, collection_concept_id)
            #es_gran_count = get_es_granule_count(index, collection_concept_id, latest_working_time.strftime("%Y-%m-%dT%H:%M:%S"))
            es_gran_count = get_es_granule_count(index, collection_concept_id, latest_working_time.isoformat().replace("+00:00", "Z"))

            if db_granule_count == es_gran_count:
                logger.info(f"DB granule count and ES granule counts match for {collection_concept_id}")
            else:
                logger.info(f"DB granule count {db_granule_count} and ES granule count {es_gran_count} do not match for {collection_concept_id}")
                mismatch_info = {
                    "provider": provider,
                    "concept_id": collection_concept_id,
                    "index": index,
                    "db_count": db_granule_count,
                    "es_count": es_gran_count,
                    "timestamp": latest_working_time.isoformat(),
                    "difference": db_granule_count - es_gran_count
                }
                
                # Write the mismatch info to the file immediately
                if first:
                    json.dump(mismatch_info, f, indent=2)
                    first = False
                else:
                    f.write(', ')
                    json.dump(mismatch_info, f, indent=2)
                f.flush()

def missing_file_name(provider):
    """
    Returns the file name to store the collection granule count information on S3.
    """
    return f"{provider}/{provider}_granule_counts_mismatch.json"

def missing_efs_file_name(provider):
    """
    Returns the file name to store the collection granule count information on an EFS device temporarily.
    """
    return f"{EFS_PATH}{missing_file_name(provider)}"

def find_granule_counts_mismatch(provider):
    """
    Compare the database and ElasticSearch granule counts. If the counts differ the create a map for the specific
    collection information and save it so that another program can fix the issues.

    Args:
        provider: The provider id to work to get a list of collection that have granules and to find the granule counts.
    
    Returns:
        Nothing, but it does save a file to S3 consisting of collection and granule count information so that another
        program can find and fix issues.

    Exceptions:
        None
    """
    # Connect to the database
    db_connection = connect_to_db()

    # Initialize the S3 client
    s3_client = boto3.client('s3')

    os.makedirs(f"{EFS_PATH}{provider}", exist_ok=True)
    
    collections = find_s3.read_collections_from_provider(s3_client, provider)

    # The revision_date is stored as UTC.
    latest_working_time = (datetime.now(timezone.utc) - timedelta(hours=1)).replace(microsecond=0)
    logger.info(f"Latest Working Time: {latest_working_time}")

    for provider, collection_concept_ids in collections.items():
        with open(missing_efs_file_name(provider), 'w', encoding="UTF-8") as f:
            f.write('[')
            # Compare granule counts between the database and elastic search
            compare_granule_counts(db_connection, latest_working_time, f, provider, collection_concept_ids)
            f.write(']')

    find_s3.upload_file_to_s3(missing_efs_file_name(provider), missing_file_name(provider))

def get_current_fargate_network_config():
    """
    Helper function to retrieve the fargate network configuration so that this program can 
    launch AWS ECS task to find and fix missing granules.
    
    Args:
        None
    
    Returns:
        The fargate network configuration.

    Exceptions:
        Raises exception when the program is not running inside a
        fargate environment.
        Raises exception when either the subnets or security groups
        are not found.
    """
    # Fetch the metadata URI provided by AWS inside Fargate
    metadata_url = os.environ.get('ECS_CONTAINER_METADATA_URI_V4')
    if not metadata_url:
        raise Exception("Not running inside a Fargate environment!")
        
    # Get the task metadata
    task_data = requests.get(f"{metadata_url}/task").json()
    task_arn = task_data.get('TaskARN')
    cluster_arn = task_data.get('Cluster')

    # Query the ECS control plane
    ecs_client = boto3.client('ecs')
    response = ecs_client.describe_tasks(
        cluster=cluster_arn,
        tasks=[task_arn]
    )
    
    # Extract the Elastic Network Interface (ENI) data
    # Fargate tasks always put the network layout inside the 'attachments' list
    subnets = []
    private_ips = []

    # Pull subnets and grab the private IP instead of security groups
    for task in response.get('tasks', []):
        for attachment in task.get('attachments', []):
            if attachment.get('type') == 'ElasticNetworkInterface':
                for detail in attachment.get('details', []):
                    if detail['name'] == 'subnetId':
                        subnets.append(detail['value'])
                    # We pull the private IPv4 instead of the missing SG ID
                    elif detail['name'] == 'privateIPv4Address':
                        private_ips.append(detail['value'])

    # Fallback to EC2 to look up the Security Groups using the IP
    ec2_client = boto3.client('ec2')
    
    # Locate the unique ENI active with your task's private IP
    eni_response = ec2_client.describe_network_interfaces(
        Filters=[
            {'Name': 'addresses.private-ip-address', 'Values': private_ips}
        ]
    )

    security_groups = []
    for eni in eni_response.get('NetworkInterfaces', []):
        for group in eni.get('Groups', []):
            if 'GroupId' in group:
                security_groups.append(group['GroupId'])

    # Deduplicate arrays
    subnets = list(set(subnets))
    security_groups = list(set(security_groups))

    # Safety check before passing to run_task
    if not subnets or not security_groups:
        raise Exception(f"Failed to find network bindings! Subnets: {subnets}, SGs: {security_groups}")

    return {
        'awsvpcConfiguration': {
            'subnets': subnets,
            'securityGroups': security_groups,
            'assignPublicIp': 'DISABLED' 
        }
    }

def get_current_cluster_name():
    """
    Helper function to retrieve the currnet fargate cluster so that this program can 
    launch AWS ECS task to find and fix missing granules.
    
    Args:
        None
    
    Returns:
        The fargate cluster name.

    Exceptions:
        Raises exception when the program is not running inside a
        fargate environment.
    """
    # Fetch the metadata URI provided by AWS
    metadata_url = os.environ.get('ECS_CONTAINER_METADATA_URI_V4')
    if not metadata_url:
        raise Exception("Not running inside a Fargate environment!")
        
    # Get the task metadata
    response = requests.get(f"{metadata_url}/task")
    task_data = response.json()
    
    # Pull the Cluster ARN or name
    cluster_ref = task_data.get('Cluster')
    
    # Get the short name.
    if '/' in cluster_ref:
        return cluster_ref.split('/')[-1]
        
    return cluster_ref

def process_granule_mismatch(provider):
    """
    Compare the database and ElasticSearch granule counts. If the counts differ the create a map for the specific
    collection information and save it so that another program can fix the issues.

    Args:
        provider: The provider id to work to get a list of collection that have granules and to find the granule counts.

    Returns:
        Nothing, but it does save a file to S3 consisting of collection and granule count information so that another
        program can find and fix issues.

    Exceptions:
        None
    """

    # Initialize the ECS fargate client
    ecs_client = boto3.client('ecs')

    # Get the current fargate network environment
    current_networking = get_current_fargate_network_config()

    # Fetch the cluster name and get the environment from it.
    cluster_name = get_current_cluster_name()
    environment = cluster_name.rsplit('-', 1)[1]

    logger.info(f"Starting to find missing granules for provider {provider}")

    response = ecs_client.run_task(
        cluster = cluster_name,
        launchType = 'FARGATE',
        taskDefinition = f"cmr-db-es-audit-find-granules-{environment}",
        count = 1,
        platformVersion = 'LATEST',
        networkConfiguration = current_networking,
        overrides={
            'containerOverrides': [
                {
                    'name': f"cmr-db-es-audit-find-granules-{environment}",
                    'environment': [
                        {'name': 'PROVIDER', 'value': provider},
                        {'name': 'AUDIT_S3_BUCKET_NAME', 'value': os.getenv("AUDIT_S3_BUCKET_NAME")},
                        {'name': 'DB_USERNAME', 'value': os.getenv("DB_USERNAME")},
                        {'name': 'DB_PASSWORD', 'value': os.getenv("DB_PASSWORD")},
                        {'name': 'DB_URL', 'value': os.getenv("DB_URL")},
                        {'name': 'GRAN_ELASTIC_HOST', 'value': os.getenv("GRAN_ELASTIC_HOST")},
                        {'name': 'ELASTIC_HOST', 'value': os.getenv("ELASTIC_HOST")},
                        {'name': 'EFS_PATH', 'value': os.getenv("EFS_PATH")},
                        {'name': 'SQS_QUEUE_URL', 'value': os.getenv("SQS_QUEUE_URL")},
                        {'name': 'BATCH_SIZE', 'value': os.getenv("BATCH_SIZE")}
                    ]
                }
            ]
        }
    )

    logger.info(f"Started Task ARN: {response['tasks'][0]['taskArn']} to find missing granules for {provider}")


if __name__ == "__main__":

    setup_logging()
    logger.info("Starting to find granule counts to see if there are any discrepencies between the database and elastic search")

    # Save to S3 a set of maps per provider that includes the provider name and a list of all
    # the providers collections if the provider has granules.
    get_providers_with_collections()

    logger.info("Starting to find provider collections with granule issues.")
    
    # Initialize the S3 client
    s3_client = boto3.client('s3')

    # Get a list of providers.
    providers = find_s3.read_providers_from_s3(s3_client)

    # work through the list of providers
    # 1. Read the list of collections that have granules - could generate the list here instead of having a different program create it.
    # 2. For each collection 
    # 2.a. Get the granule counts in the DB
    # 2.b. Get the granule counts in ES
    # 2.c. Compare them if count > 0 then write data to a file
    cpu_count = os.cpu_count()
    num_workers = cpu_count * 4

    with multiprocessing.Pool(processes=num_workers) as pool:
        pool.map(find_granule_counts_mismatch, providers, chunksize=1)

    logger.info("Finished finding provider collections with granule issues.")

    ###########
    # Start working on the granule issues. This section will spawn off workers per provider to
    # 1) go through each collection for a provider where the granule counts don't match between the database and elastic search
    # 2) when the database granule counts are bigger records are missing in ES. 
    # 2.a) Find those records and index them by creating an SQS message and putting it directly on the index queue.
    # 2.b) Right now we only go through the granules until the discrepancy number drops to zero. At the momment we are not
    #      checking every granule. Maybe we should add an option to check every granule - we only really need/should do this once.
    # 3) When the database granule counts are smaller than ES, then ES has records that didn't get deleted. Go through ES and find 
    #    the granules that no longer should exist and remove them.

    logger.info("Start working on granule issues.")

    # The next task is more CPU intensive to the database and therefore the number of workers is smaller.
    # num_workers = cpu_count
    num_workers = 2
    with multiprocessing.Pool(processes=num_workers) as pool:
        pool.map(process_granule_mismatch, providers, chunksize=1)
