import json
import os
import re
import sys

import boto3
from botocore.exceptions import ClientError
import oracledb
from datetime import datetime, timezone
import requests
import logging

import find_s3
from parameters import CONFIG, load_ssm_params
from find_logger import setup_logging
from find_db import connect_to_db

PROVIDER = None
ENVIRONMENT = None
try:
    PROVIDER = os.environ['PROVIDER']
    ENVIRONMENT = os.environ['ENVIRONMENT']
except KeyError:
    print("Error: PROVIDER or ENVIRONMENT environment variable is not set.")
    sys.exit(1)

REQUEST_TIMEOUT_SECONDS = 30

#ELASTIC_PORT = os.getenv("ELASTIC_PORT", "9200")
#ELASTIC_HOST = os.getenv("ELASTIC_HOST")
#ELASTIC_URL = f"http://{ELASTIC_HOST}:{ELASTIC_PORT}"
#GRAN_ELASTIC_PORT = os.getenv("GRAN_ELASTIC_PORT", "9200")
#GRAN_ELASTIC_HOST = os.getenv("GRAN_ELASTIC_HOST")
#GRAN_ELASTIC_URL = f"http://{GRAN_ELASTIC_HOST}:{GRAN_ELASTIC_PORT}"
#QUEUE_URL = os.getenv("SQS_QUEUE_URL")

# Create an SQS client
SQS = boto3.client('sqs', region_name='us-east-1')

#EFS_PATH = os.getenv("EFS_PATH")

DB_TABLE_REGEX = r"[0-9a-zA-Z_]+_GRANULES"

#BATCH_SIZE = int(os.getenv("BATCH_SIZE", "1000"))

logger = logging.getLogger("find_granules")

def db_batch_read(mismatch):
    """
    Retrieve BATCH_SIZE number of records from the database. These records
    will be compared to elastic search to see what is missing. The core
    python pattern for batching records in chunks uses a generator (which is
    this function) to "yield" chunks of records. When the built-in next()
    function is called from the process_db_batches function on the resulting
    generator object (again this function), Python resumes execution of the function
    until it hits the next yield statement.

    Args:
        mismatch: The passed in data structure that contains information 
                  used to query and retrieve granules from the database.
    
    Returns:
        A list of granules' concept ID, revision ID, and revision date.

    Exceptions:
        Raises exceptions if 
        1) Cannot connect to the database
        2) A database table name doesn't exist
        3) The database query cannot execute.
    """
    db_connection = connect_to_db()
    table_name = mismatch['provider'] + "_GRANULES"
    
    if not re.fullmatch(DB_TABLE_REGEX, table_name):
        raise Exception(f"DB table name {table_name} is not valid!")

    collection_id = mismatch['concept_id']

    # The revision_date is in UTC.
    lwt_string = mismatch['timestamp']
    
    search_query = f"""
        SELECT concept_id, 
               MAX(revision_id) AS revision_id, 
               TO_CHAR(MAX(revision_date) KEEP (DENSE_RANK LAST ORDER BY revision_id), 'YYYY-MM-DD HH24:MI:SS TZH:TZM') AS revision_date
        FROM METADATA_DB.{table_name}
        WHERE PARENT_COLLECTION_ID = '{collection_id}'
        AND REVISION_DATE <= TO_TIMESTAMP_TZ('{lwt_string}', 'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM')
        GROUP BY concept_id
        HAVING MAX(deleted) KEEP (DENSE_RANK LAST ORDER BY revision_id) = 0
        ORDER BY concept_id DESC
    """
    ### need to create a new index (PARENT_COLLECTION_ID, REVISION_DATE, CONCEPT_ID, REVISION_ID, DELETED) on the granule tables.

    try:
        with db_connection.cursor() as curr:
            curr.execute(search_query)
            while True:
                batch = curr.fetchmany(CONFIG.get('BATCH_SIZE'))
                if not batch:
                    break
                yield batch
    except oracledb.Error as e:
            raise Exception(f"Failed to execute query: {search_query}\nerror: {e}")
    finally:
        db_connection.close()

def has_granule_been_deleted(db_connection, provider, concept_id):
    """
    Checks the database to see if a specific granule has been deleted.
    This check is done to make sure the granule missing from elastic search
    hasn't been deleted in between the time when the program started and
    when elastic search has been searched.

    Args:
        db_connection: The database connection
        provider: the CMR provider ID that this program is working on.
        concept_id: The granule concept ID of the granule that is being searched.
    
    Returns:
        A 1 (true) if the granule has been deleted. Otherwise a 0 (false) is returned. 

    Exceptions:
        Raises exceptions if the database query cannot execute.
    """
    table_name = provider + "_GRANULES"

    search_query = f"""
    SELECT deleted FROM (SELECT t.deleted, ROW_NUMBER() OVER (ORDER BY revision_id DESC) as rn
                         FROM METADATA_DB.{table_name} t
                         WHERE concept_id = :cid
    )
    WHERE rn = 1
    """

    result = False
    try:
        with db_connection.cursor() as curr:
            curr.execute(search_query, cid=concept_id)
            result = curr.fetchone()[0]

    except oracledb.Error as e:
            logger.error(f"In has_granule_been_deleted Exception occured. {e}")
            raise Exception(f"Failed to execute query: {search_query}\nerror: {e}")
    finally:
        return result

def execute_es_query(index, query):
    """
    Execute the passed in elastic search query using the passed in elastic search index.

    Args:
        index: The query is executed on the elastic search index where the granules reside.
        query: The elastic search query to get the list of granule records.
    
    Returns:
        A json representation of the elastic search results.

    Exceptions:
        Raises exceptions if the elastic search query failed.
    """
    headers = {"Content-Type": "application/json"}
    elastic_response = requests.post(f"{CONFIG.get("GRAN_ELASTIC_URL")}/{index}/_search",
                                     json=query,
                                     headers=headers,
                                     timeout=REQUEST_TIMEOUT_SECONDS)
    if elastic_response.status_code >= 300:
        logger.error(f"ERROR: request to elastic failed with error: {elastic_response.text}")
        sys.exit(1)
    return elastic_response.json()

def elastic_search_query(db_batch, query_size):
    """
    Creates the elastic search query for all of the granule records that exist
    in the db_batch parameter. The query_size parameter limits the number of results. Normally
    the query_size is 0 because we only need the count of records that were found.
    In the off chance that not all records exist, the BATCH_SIZE is used to get back all of the
    queried for records.

    Args:
        db_batch: The granule data to query elastic search.
        query_size: The parameter to limit the size of the result set. It is used to improve performance.
    
    Returns:
        A json representation of the elastic search query.

    Exceptions:
        None
    """
    # Initialize the query structure
    query = {"_source": ["concept-id", "revision-id", "revision-date"],
             "size": query_size,
             "query": {"bool": {"should": []}}}

    for concept_id, revision_id, _ in db_batch:
        bool_query = {"bool": {"must": [{"term": {"concept-id": concept_id}},
                                        {"term": {"revision-id": str(revision_id)}}]}}
        query["query"]["bool"]["should"].append(bool_query)
    return query

def get_collection_entry_title(mismatch):
    """
    Get the entry title for the index message to put on the index queue. Indexer does not
    seem to use the entry title from the collection, but since the current software puts it
    in, it is put in for completeness.

    Args:
        mismatch: The granule data needed to create the elastic search query for the granules collection.
    
    Returns:
        The entry title as a string

    Exceptions:
        None; But logs elastic search query and exists the program.
    """
    collection_concept_id = mismatch['concept_id']
    index = "1_collections_v2_alias"
    query = {"_source": ["entry-title"],
             "query": {"bool": {"must": [{"match": {"concept-id": collection_concept_id}}]}}}
    
    headers = {"Content-Type": "application/json"}
    elastic_response = requests.post(f"{CONFIG.get("ELASTIC_URL")}/{index}/_search",
                                     json=query,
                                     headers=headers,
                                     timeout=REQUEST_TIMEOUT_SECONDS)
    if elastic_response.status_code >= 300:
        logger.error(f"ERROR: request to elastic failed with error: {elastic_response.text}")
        sys.exit(1)

    results = elastic_response.json()
    hits = results["hits"]["hits"]
    entry_title = ""
    for hit in hits:
        entry_title = hit["_source"]["entry-title"]
    
    return entry_title

def publish_message_to_sqs(message_body):
    """
    Put the created event message on to the indexer SQS queue.

    Args:
        message_body: The message body that is sent to the indexer queue.
    
    Returns:
        Nothing

    Exceptions:
        None
    """
    # Convert the message_body dictionary to a JSON string
    message_body_str = json.dumps(message_body, separators=(',', ':'))

    # Send message to SQS queue
    response = SQS.send_message(QueueUrl=CONFIG.get("SQS_QUEUE_URL"),MessageBody=message_body_str)
    logger.info(f"Message {message_body_str} sent. Message ID: {response['MessageId']}")

def process_db_batches(mismatch):
    """
    Compares a set of granule records between the database and elastic search
    for a specific collection.
    1) Create a generator that is a python pattern that uses a function to retrieve
       batches of records from the database.
    2) Get a very rough estimate of how long it will take to compare and fix the
       granules for the collection.
    3) Start comparing the granules
      3A) Get the BATCH_SIZE number of granules
      3B) Query elastic search and compare the number of granules
      3C) If there are fewer records in ES then in the DB for this batch
        3C1) Go through each granule and check to see if it exists in ES.
        3C2) If it doesn't exist, make sure it has not been deleted. If 
             the record exists, re-index the granule and put the granule into the report.
      3D) The process stops when either there are no more records in the database or the 
          same number of granules have been reindexed as the difference count.

    Args:
        mismatch: The structured information used to query and compare granules
        from the database and elastic search for a specific collection.
    
    Returns:
        Nothing; But creates a report of the found missing granules

    Exceptions:
        None; The StopIteration is used to signal the end of the batching process
        to get records from the database.
    """
    has_granules_db_conn = connect_to_db()
    db_generator = db_batch_read(mismatch)
    count = 0

    # The missing_items_count and difference are used to figure out when to stop comparing the granules. 
    # When the number of records have been found (count) (and messages have been sent to
    # index the records) equals the difference the processing stops. It is then
    # assumed that we have found all of the issues.
    difference = mismatch['db_count'] - mismatch['es_count']
    missing_items_count = 0

    # .6 seconds is roughly average time to get 500 records from the database using batching
    estimated_duration = mismatch['db_count'] / CONFIG.get("BATCH_SIZE") * 0.6
    logger.info(f"To process {mismatch['db_count']} records for {mismatch['concept_id']}, it will take about {estimated_duration} seconds to complete processing.")

    entry_title = get_collection_entry_title(mismatch)
    missing_report = open(report_efs_file_name(mismatch), "a", encoding="UTF-8")

    # Continue until the StopIteration exception is raised
    while difference > 0:
        try:
            count += 1

            # A warm fuzzy that the program is still working
            if count % 100 == 0:
                logger.info(f"Processing batch {count}.")

            # Get the first DB batch
            db_batch = next(db_generator)
 
            # Create an elastic query to see if all of the concept ids exist
            query = elastic_search_query(db_batch, 0)
            results = execute_es_query(mismatch['index'], query)
            granule_hits = results["hits"]["total"]["value"]

            if granule_hits < len(db_batch):
                # Figure out from the results which concepts do not exist
                # Extract _source values into an array
                query = elastic_search_query(db_batch, CONFIG.get("BATCH_SIZE"))
                results = execute_es_query(mismatch['index'], query)

                source_array = [hit['_source'] for hit in results['hits']['hits']]
                
                # Convert hits to a set of tuples
                hits_set = set((item['concept-id'], item['revision-id']) for item in source_array)
                db_batch_set = set({(item[0], item[1]) for item in db_batch})

                # Find the difference (items in db_batch but not in hits)
                missing_items = db_batch_set - hits_set

                # process the results
                if missing_items:
                    for item in missing_items:
                        # check in the database to see if the granule has been deleted
                        # if it has then don't do anything
                        if not has_granule_been_deleted(has_granules_db_conn, mismatch['provider'], item[0]):

                            missing_item = {"entry-title":entry_title,"action":"concept-update","concept-id":item[0],"revision-id":item[1]}
                            publish_message_to_sqs(missing_item)

                            # Create a report, so that we can look up why the granule didn't get indexed.
                            lookup_dict = {item[0]: item[2] for item in db_batch}
                            missing_report.write(f"{mismatch['concept_id']},{item[0]},{item[1]},{lookup_dict.get(item[0], 'None')}\n")

                            # if the difference is negative then there are more records in ES than DB, so check all records
                            # otherwise just check until we have found all missing granules.
                            if difference > 0:
                                missing_items_count += 1
                                if missing_items_count >= difference:
                                    logger.info(f"Reached the difference limit of {difference} missing items.")
                                    raise StopIteration
                else:
                    print("All items in db_batch are present in hits.")
        except StopIteration:
            missing_report.flush()
            missing_report.close()
            break

def report_file_name(mismatch):
    """
    The file path and name of the missing granule report that lives in S3.
    """
    return f"{mismatch['provider']}/{mismatch['provider']}_missing_in_es_{mismatch['timestamp']}.csv"

def report_efs_file_name(mismatch):
    """
    The temporary file path and name of the missing granule report that lives on EFS.
    """
    return f"{CONFIG.get("EFS_PATH")}{report_file_name(mismatch)}"

def prepare_report_file(mismatch):
    """
    Create a csv initial report with header information.

    Args:
        mismatch: Contains the information to setup the report file.
    
    Returns:
        Nothing; Creates the report file in EFS.

    Exceptions:
        None
    """
    os.makedirs(f"{CONFIG.get("EFS_PATH")}{mismatch['provider']}", exist_ok=True)
    report = open(report_efs_file_name(mismatch), "w", encoding="UTF-8")
    report.write("Collection Concept ID,Granule Concept ID,Granule Revision,Revision Date\n")
    report.flush()
    report.close()

def process_mismatches(first_time_open_report):
    """
    Process the database and elastic search granule count mismatch information.

    Args:
        first_time_open_report: Flag used to initialize the mismatch granule report.
    
    Returns:
        Nothing; Creates the report file in EFS and at the end uploads the report to S3.

    Exceptions:
        None
    """
    mismatches = []
    save_last_mismatch = {}

    logger.info(f"Started processing records for {PROVIDER}")

    s3_client = boto3.client('s3')
    mismatches_str = find_s3.read_from_s3(s3_client, f"{PROVIDER}/{PROVIDER}_granule_counts_mismatch.json")
    mismatches = json.loads(mismatches_str)

    if mismatches:
        logger.info(f"mismatches: {mismatches}")

        # Iterate over the mismatches
        for mismatch in mismatches:
            save_last_mismatch = mismatch
            provider = mismatch['provider']
            concept_id = mismatch['concept_id']
            index = mismatch['index']
            db_count = mismatch['db_count']
            es_count = mismatch['es_count']
            timestamp = mismatch['timestamp']
        
            logger.info(f"Processing mismatch for {concept_id}:  Provider: {provider}  Index: {index} DB Count: {db_count}   ES Count: {es_count}  Timestamp: {timestamp}  Difference: {db_count - es_count}")

            if first_time_open_report:
                prepare_report_file(mismatch)
                first_time_open_report = False

            process_db_batches(mismatch)

        logger.info(f"Done processing {mismatches}")

        find_s3.upload_file_to_s3(report_efs_file_name(save_last_mismatch), report_file_name(save_last_mismatch))
        logger.info("Done moving S3 file.")

    logger.info(f"Completed processing records for {PROVIDER}")

if __name__ == "__main__":

    setup_logging(PROVIDER)
    load_ssm_params(ENVIRONMENT)

    first_time_open_report = True
    process_mismatches(first_time_open_report)
