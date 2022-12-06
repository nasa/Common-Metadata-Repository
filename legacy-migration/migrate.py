import argparse
import collections
import json
import oracledb
import os
import requests
import time
import xml.etree.ElementTree as ET

# returns strings or bytes instead of a locator
oracledb.defaults.fetch_lobs = False

user = os.environ.get('MIGRATE_USER')
pwd = os.environ.get('MIGRATE_PWD')
access_token = os.environ.get('ACCESS_TOKEN')

header = {"Content-Type": "application/json",
          "Authorization": access_token,
          "User-id": "legacy_migration"}

db_name = "I_10_0_TESTBED_BUSINESS"
url_root = "https://cmr.sit.earthdata.nasa.gov"

# providers map with guid -> provider_id
providers_map = {}
# data quality summaries with guid -> concept_id
data_quality_summaries = {}
# option definitions with guid -> concept_id
option_definitions = {}
# service option definitions with guid -> concept_id
service_option_definitions = {}
# collections with concept_id -> {'order_umm_s': <order-umm-s-concept-id> 'esi_umm_s': <esi-umm-s-concept-id>}
collection_services = {}

# These global variables are used to keep the counts for each migration data type to save us from passing these counts as arguments through implementation functions
success_count: int = 0
failure_count: int = 0
skipped_count: int = 0

# retrieve providers map with provider_guid_to_name_map
def provider_guid_to_name_map():
    global providers_map
    with oracledb.connect(user=user, password=pwd, dsn="localhost:1521/orcl") as connection:
        with connection.cursor() as cursor:
            sql = f"select guid, provider_id from {db_name}.provider"
            for r in cursor.execute(sql):
                providers_map[r[0]] = r[1]


def get_ingest_concept_id(xml_resp):
    root = ET.fromstring(xml_resp)
    return root.find('concept-id').text


def get_search_concept_id(xml_result):
    root = ET.fromstring(xml_result)
    return root.find('references/reference/id').text


def migrate_dqs_row(id, name, summary, provider_guid):
    global success_count, failure_count
    umm = {"Id": id,
           "Name": name,
           "Summary": summary,
           "MetadataSpecification": {
               "Name": "Data Quality Summary",
               "Version": "1.0.0",
               "URL": "https://cdn.earthdata.nasa.gov/generics/data-quality-summary/v1.0.0"}}

    resp = requests.put(f"{url_root}/ingest/providers/{providers_map[provider_guid]}/data-quality-summaries/{id}",
                        data=json.dumps(umm),
                        headers=header)
    if resp.status_code >= 300:
        failure_count += 1
        print(f"Failed to migrate DQS [{id}] with status code: [{resp.status_code}], error: {resp.text}")
    else:
        success_count += 1
        data_quality_summaries[id] = get_ingest_concept_id(resp.text)
        print(f"Successfully migrated DQS [{id}]")


# Register the concept id in global maps based on the concept type (OD or SOD)
def register_concept(type, id, concept_id):
    if type == 'OD':
        option_definitions[id] = concept_id
    else:
        service_option_definitions[id] = concept_id


# Returns the concept_id of the OrderOption concept with native id of the given id if found; otherwise return None.
def get_order_option_concept_id(id):
    resp = requests.get(f"{url_root}/search/order-options?native_id={id}",
                            headers={"Authorization": access_token})
    if resp.headers['CMR-Hits'] == "1":
        return get_search_concept_id(resp.text)
    else:
        return None

def migrate_into_order_option(type, id, provider_guid, umm):
    global force, success_count, failure_count, skipped_count
    if force:
        matched_concept_id = None
    else:
        matched_concept_id = get_order_option_concept_id(id)

    if matched_concept_id is None:
        resp = requests.put(f"{url_root}/ingest/providers/{providers_map[provider_guid]}/order-options/{id}",
                            data=json.dumps(umm),
                            headers=header)
        if resp.status_code >= 300:
            failure_count += 1
            print(f"Failed to migrate {type} [{id}] with status code: [{resp.status_code}], error: {resp.text}")
        else:
            success_count += 1
            register_concept(type, id, get_ingest_concept_id(resp.text))
            print(f"Successfully migrated {type} [{id}]")
    else:
        skipped_count += 1
        register_concept(type, id, matched_concept_id)
        print(f"Skip migrating {type} [{id}], already exists.")

def migrate_od_row(id, name, description, form, scope, sort_key, deprecated, provider_guid):
    umm = {"Id": id,
           "Name": name,
           "Description": description,
           "Form": form,
           "Scope": scope,
           "SortKey": sort_key if sort_key else "",
           "Deprecated": True if deprecated else False,
           "MetadataSpecification": {
               "Name": "Order Option",
               "Version": "1.0.0",
               "URL": "https://cdn.earthdata.nasa.gov/generics/order-option/v1.0.0"}}
    migrate_into_order_option('OD', id, provider_guid, umm)


def migrate_sod_row(id, provider_guid, name, description, form):
    umm = {"Id": id,
           "Name": name,
           "Description": description,
           "Form": form,
           "MetadataSpecification": {
               "Name": "Order Option",
               "Version": "1.0.0",
               "URL": "https://cdn.earthdata.nasa.gov/generics/order-option/v1.0.0"}}
    migrate_into_order_option('SOD', id, provider_guid, umm)


# Migrate Data Quality Summary Assignments for a single DataQualitySummary concept
def migrate_dqsas(dqs_id, coll_concept_ids):
    global success_count, failure_count
    concept_ids = [{"concept_id": id} for id in coll_concept_ids]

    resp = requests.post(f"{url_root}/search/associate/{data_quality_summaries[dqs_id]}",
                         data=json.dumps(concept_ids),
                         headers=header)
    if resp.status_code >= 300:
        failure_count += 1
        print(f"Failed to migrate associations for [{dqs_id}], concept_id: [{data_quality_summaries[dqs_id]}] with status code: [{resp.status_code}], error: {resp.text}")
    else:
        success_count += 1
        print(f"Successfully migrated associations for [{dqs_id}], concept_id: [{data_quality_summaries[dqs_id]}]")


# Get the tag service concept id from entries. Returns None if no value is found.
def get_tag_service_id(entries, tag_name):
    try:
        service_id = entries[0]['tags'][tag_name]['data']['id']
    except (KeyError, IndexError):
        service_id = None
    return service_id    


# Register the collection and its associated UMM-S concepts in global collection_services.
# If the collection already exists in the collection_services, do nothing.
def register_collection(concept_id):
    if concept_id not in collection_services:
        resp = requests.get(f"{url_root}/search/collections.json?concept_id={concept_id}&include_tags=*",
                            headers={"Authorization": access_token,
                                     "User-id": "legacy_migration"})
        entries = resp.json()['feed']['entry']
        if len(entries) == 1:
            collection_services[concept_id] = {'order_umm_s': get_tag_service_id(entries, 'edsc.extra.serverless.subset_service.echo_orders'),
                                               'esi_umm_s': get_tag_service_id(entries, 'edsc.extra.serverless.subset_service.esi')}
        else:
            print(f"Collection with concept_id [{concept_id}] is not found.")
            collection_services[concept_id] = {'order_umm_s': None,
                                               'esi_umm_s': None}


# Migrate Option Definition Assignments for a single collection
def migrate_odas(coll_concept_id, od_ids):
    global success_count, failure_count, skipped_count
    if len(od_ids) > 1:
        print(f"Collection can only be associated with one UMM-S concept with type 'ECHO ORDERS', but was associated with {od_ids}. Only {od_ids[0]} will be migrated.")
        skipped_count += len(od_ids) - 1

    od_id = od_ids[0]
    register_collection(coll_concept_id)
    umm_s_concept_id = collection_services[coll_concept_id]['order_umm_s']
    if umm_s_concept_id is None:
        print(f"No UMM-S with type 'ECHO ORDERS' is associated with the collection {coll_concept_id}, skip OrderOption {od_id} assignment migration.")
        skipped_count += 1
    else:
        payload = [{"concept_id": coll_concept_id, "data": {"order_option": option_definitions[od_id]}}]
        resp = requests.post(f"{url_root}/search/services/{umm_s_concept_id}/associations",
                             data=json.dumps(payload),
                             headers=header)
        if resp.status_code >= 300:
            failure_count += 1
            print(f"Failed to migrate associations for [{od_id}], collection concept_id: [{coll_concept_id}] with status code: [{resp.status_code}], error: {resp.text}")
        else:
            success_count += 1
            print(f"Successfully migrated associations for [{od_id}], collection concept_id: [{coll_concept_id}]")


# Migrate Service Option Definition Assignments for a single collection
def migrate_soas(coll_concept_id, sod_ids):
    global success_count, failure_count, skipped_count
    if len(sod_ids) > 1:
        print(f"Collection can only be associated with one UMM-S concept with type 'ESI', but was associated with {sod_ids}. Only {sod_ids[0]} will be migrated.")
        skipped_count += len(sod_ids) - 1

    sod_id = sod_ids[0]
    register_collection(coll_concept_id)
    umm_s_concept_id = collection_services[coll_concept_id]['esi_umm_s']
    if umm_s_concept_id is None:
        print(f"No UMM-S with type 'ESI' is associated with the collection {coll_concept_id}, skip ServiceOptionDefinition {sod_id} assignment migration.")
        skipped_count += 1
    else:
        payload = [{"concept_id": coll_concept_id, "data": {"order_option": service_option_definitions[sod_id]}}]
        resp = requests.post(f"{url_root}/search/services/{umm_s_concept_id}/associations",
                             data=json.dumps(payload),
                             headers=header)
        if resp.status_code >= 300:
            failure_count += 1
            print(f"Failed to migrate associations for [{sod_id}], collection concept_id: [{coll_concept_id}] with status code: [{resp.status_code}], error: {resp.text}")
        else:
            success_count += 1
            print(f"Successfully migrated associations for [{sod_id}], collection concept_id: [{coll_concept_id}]")


def migrate_data_quality_summary():
    global success_count, failure_count
    success_count = 0
    failure_count = 0
    start = time.time()
    print("Starting DataQualitySummary data migration...")

    with oracledb.connect(user=user, password=pwd, dsn="localhost:1521/orcl") as connection:
        with connection.cursor() as cursor:
            sql = f"select GUID, NAME, SUMMARY, OWNER_PROVIDER_GUID from {db_name}.DATA_QUAL_SUMMARY_DEF"
            for r in cursor.execute(sql):
                migrate_dqs_row(r[0], r[1], r[2], r[3])

    end = time.time()
    print(f"Total DataQualitySummary data migration time spent: {end - start} seconds, Succeeded: {success_count}, Failed: {failure_count}")


def migrate_option_definition():
    global success_count, failure_count, skipped_count
    success_count = 0
    failure_count = 0
    skipped_count = 0
    start = time.time()
    print("Starting OptionDefinition data migration...")

    with oracledb.connect(user=user, password=pwd, dsn="localhost:1521/orcl") as connection:
        with connection.cursor() as cursor:
            sql = f"select GUID, NAME, DESCRIPTION, FORM, SCOPE, SORT_KEY, DEPRECATED, PROVIDER_GUID from {db_name}.EJB_OPTION_DEF"
            for r in cursor.execute(sql):
                migrate_od_row(r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7])

    end = time.time()
    print(f"Total OptionDefinition data migration time spent: {end - start} seconds, Succeeded: {success_count}, Failed: {failure_count}, Skipped: {skipped_count}.")


def migrate_service_option_definition():
    global success_count, failure_count, skipped_count
    success_count = 0
    failure_count = 0
    skipped_count = 0
    start = time.time()
    print("Starting ServiceOptionDefinition data migration...")

    with oracledb.connect(user=user, password=pwd, dsn="localhost:1521/orcl") as connection:
        with connection.cursor() as cursor:
            sql = f"select GUID, PROVIDER_GUID, NAME, DESCRIPTION, FORM from {db_name}.SERVICE_OPTION_DEFINITION"
            for r in cursor.execute(sql):
                migrate_sod_row(r[0], r[1], r[2], r[3], r[4])

    end = time.time()
    print(f"Total ServiceOptionDefinition data migration time spent: {end - start} seconds, Succeeded: {success_count}, Failed: {failure_count}, Skipped: {skipped_count}.")


def migrate_data_quality_summary_assignment():
    global success_count, failure_count
    success_count = 0
    failure_count = 0
    start = time.time()
    print("Starting DataQualitySummaryAssignment data migration...")
    assignments = collections.defaultdict(list)

    with oracledb.connect(user=user, password=pwd, dsn="localhost:1521/orcl") as connection:
        with connection.cursor() as cursor:
            sql = f"select DEFINITION_GUID, CATALOG_ITEM_GUID from {db_name}.DATA_QUAL_SUMMARY_ASSIGN"
            for r in cursor.execute(sql):
                assignments[r[0]].append(r[1])

    for k, v in assignments.items():
        migrate_dqsas(k, v)

    end = time.time()
    print(f"Total DataQualitySummaryAssignment data migration time spent: {end - start} seconds, Succeeded: {success_count}, Failed: {failure_count}")


def migrate_option_definition_assignment():
    global success_count, failure_count, skipped_count
    success_count = 0
    failure_count = 0
    skipped_count = 0
    start = time.time()
    print("Starting OptionDefinitionAssignment data migration...")
    assignments = collections.defaultdict(list)

    with oracledb.connect(user=user, password=pwd, dsn="localhost:1521/orcl") as connection:
        with connection.cursor() as cursor:
            sql = f"select CATALOG_ITEM_GUID, OPTION_DEF_GUID from {db_name}.EJB_CAT_OPTN_ASSGN"
            for r in cursor.execute(sql):
                assignments[r[0]].append(r[1])

    for k, v in assignments.items():
        migrate_odas(k, v)

    end = time.time()
    print(f"Total OptionDefinitionAssignment data migration time spent: {end - start} seconds, Succeeded: {success_count}, Failed: {failure_count}, Skipped: {skipped_count}.")


def migrate_service_option_assignment():
    global success_count, failure_count, skipped_count
    success_count = 0
    failure_count = 0
    skipped_count = 0
    start = time.time()
    print("Starting ServiceOptionAssignment data migration...")
    assignments = collections.defaultdict(list)

    with oracledb.connect(user=user, password=pwd, dsn="localhost:1521/orcl") as connection:
        with connection.cursor() as cursor:
            sql = f"select CATALOG_ITEM_GUID, SERVICE_OPTION_DEFINITION_GUID from {db_name}.SERVICE_OPTION_ASSIGNMENT"
            for r in cursor.execute(sql):
                assignments[r[0]].append(r[1])

    for k, v in assignments.items():
        migrate_soas(k, v)

    end = time.time()
    print(f"Total ServiceOptionAssignment data migration time spent: {end - start} seconds, Succeeded: {success_count}, Failed: {failure_count}, Skipped: {skipped_count}.")

# Main
parser = argparse.ArgumentParser()
# Add -f option to force migration of OrderOptionDefinitions and ServiceOptionDefinitions regardless of their existence in CMR.
# By default, OrderOptionDefinitions and ServiceOptionDefinitions will not ingested into CMR again if a matching order-option concept already exist.
parser.add_argument('-f', '--force', action='store_true', help='force migration of order-option concepts')
args = parser.parse_args()
force = args.force

provider_guid_to_name_map()
migrate_data_quality_summary()
migrate_data_quality_summary_assignment()
migrate_option_definition()
migrate_option_definition_assignment()
migrate_service_option_definition()
migrate_service_option_assignment()
