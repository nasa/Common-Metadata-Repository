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

success_count: int = 0
failure_count: int = 0

# retrieve providers map with provider_guid_to_name_map
def provider_guid_to_name_map():
    global providers_map
    with oracledb.connect(user=user, password=pwd, dsn="localhost:1521/orcl") as connection:
        with connection.cursor() as cursor:
            sql = f"select guid, provider_id from {db_name}.provider"
            for r in cursor.execute(sql):
                providers_map[r[0]] = r[1]


def get_concept_id(xml_resp):
    root = ET.fromstring(xml_resp)
    return root.find('concept-id').text


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
        data_quality_summaries[id] = get_concept_id(resp.text)
        print(f"Successfully migrated DQS [{id}]")


def migrate_od_row(id, name, description, form, scope, sort_key, deprecated, provider_guid):
    global success_count, failure_count
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

    resp = requests.put(f"{url_root}/ingest/providers/{providers_map[provider_guid]}/order-options/{id}",
                        data=json.dumps(umm),
                        headers=header)
    if resp.status_code >= 300:
        failure_count += 1
        print(f"Failed to migrate OD [{id}] with status code: [{resp.status_code}], error: {resp.text}")
    else:
        success_count += 1
        option_definitions[id] = get_concept_id(resp.text)
        print(f"Successfully migrated OD [{id}]")


def migrate_sod_row(id, provider_guid, name, description, form):
    global success_count, failure_count
    umm = {"Id": id,
           "Name": name,
           "Description": description,
           "Form": form,
           "MetadataSpecification": {
               "Name": "Service Option",
               "Version": "1.0.0",
               "URL": "https://cdn.earthdata.nasa.gov/generics/service-option/v1.0.0"}}

    resp = requests.put(f"{url_root}/ingest/providers/{providers_map[provider_guid]}/service-options/{id}",
                        data=json.dumps(umm),
                        headers=header)
    if resp.status_code >= 300:
        failure_count += 1
        print(f"Failed to migrate SOD [{id}] with status code: [{resp.status_code}], error: {resp.text}")
    else:
        success_count += 1
        service_option_definitions[id] = get_concept_id(resp.text)
        print(f"Successfully migrated SOD [{id}]")


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


# Migrate Option Definition Assignments for a single OptionDefinition concept
def migrate_odas(od_id, coll_concept_ids):
    global success_count, failure_count
    concept_ids = [{"concept_id": id} for id in coll_concept_ids]

    resp = requests.post(f"{url_root}/search/associate/{option_definitions[od_id]}",
                         data=json.dumps(concept_ids),
                         headers=header)
    if resp.status_code >= 300:
        failure_count += 1
        print(f"Failed to migrate associations for [{od_id}], concept_id: [{option_definitions[od_id]}] with status code: [{resp.status_code}], error: {resp.text}")
    else:
        success_count += 1
        print(f"Successfully migrated associations for [{od_id}], concept_id: [{option_definitions[od_id]}]")


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
    global success_count, failure_count
    success_count = 0
    failure_count = 0
    start = time.time()
    print("Starting OptionDefinition data migration...")

    with oracledb.connect(user=user, password=pwd, dsn="localhost:1521/orcl") as connection:
        with connection.cursor() as cursor:
            sql = f"select GUID, NAME, DESCRIPTION, FORM, SCOPE, SORT_KEY, DEPRECATED, PROVIDER_GUID from {db_name}.EJB_OPTION_DEF"
            for r in cursor.execute(sql):
                migrate_od_row(r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7])

    end = time.time()
    print(f"Total OptionDefinition data migration time spent: {end - start} seconds, Succeeded: {success_count}, Failed: {failure_count}")


def migrate_service_option_definition():
    global success_count, failure_count
    success_count = 0
    failure_count = 0
    start = time.time()
    print("Starting ServiceOptionDefinition data migration...")

    with oracledb.connect(user=user, password=pwd, dsn="localhost:1521/orcl") as connection:
        with connection.cursor() as cursor:
            sql = f"select GUID, PROVIDER_GUID, NAME, DESCRIPTION, FORM from {db_name}.SERVICE_OPTION_DEFINITION"
            for r in cursor.execute(sql):
                migrate_sod_row(r[0], r[1], r[2], r[3], r[4])

    end = time.time()
    print(f"Total ServiceOptionDefinition data migration time spent: {end - start} seconds, Succeeded: {success_count}, Failed: {failure_count}")


def migrate_data_quality_summary_assignment():
    global success_count, failure_count
    success_count = 0
    failure_count = 0
    start = time.time()
    print("Starting DATA_QUAL_SUMMARY_ASSIGN data migration...")
    assignments = collections.defaultdict(list)

    with oracledb.connect(user=user, password=pwd, dsn="localhost:1521/orcl") as connection:
        with connection.cursor() as cursor:
            sql = f"select DEFINITION_GUID, CATALOG_ITEM_GUID from {db_name}.DATA_QUAL_SUMMARY_ASSIGN"
            for r in cursor.execute(sql):
                assignments[r[0]].append(r[1])

    for k, v in assignments.items():
        migrate_dqsas(k, v)

    end = time.time()
    print(f"Total DATA_QUAL_SUMMARY_ASSIGN data migration time spent: {end - start} seconds, Succeeded: {success_count}, Failed: {failure_count}")


def migrate_option_definition_assignment():
    global success_count, failure_count
    success_count = 0
    failure_count = 0
    start = time.time()
    print("Starting EJB_CAT_OPTN_ASSGN data migration...")
    assignments = collections.defaultdict(list)

    with oracledb.connect(user=user, password=pwd, dsn="localhost:1521/orcl") as connection:
        with connection.cursor() as cursor:
            sql = f"select OPTION_DEF_GUID, CATALOG_ITEM_GUID from {db_name}.EJB_CAT_OPTN_ASSGN"
            for r in cursor.execute(sql):
                assignments[r[0]].append(r[1])

    for k, v in assignments.items():
        migrate_odas(k, v)

    end = time.time()
    print(f"Total EJB_CAT_OPTN_ASSGN data migration time spent: {end - start} seconds, Succeeded: {success_count}, Failed: {failure_count}")


# Main
provider_guid_to_name_map()
migrate_data_quality_summary()
migrate_data_quality_summary_assignment()
migrate_option_definition()
migrate_option_definition_assignment()
migrate_service_option_definition()
