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

url_root = "https://cmr.sit.earthdata.nasa.gov"
# providers map with guid -> provider_id
providers_map = {}
# data quality summaries with guid -> concept_id
data_quality_summaries = {}

success_count: int = 0
failure_count: int = 0


# retrieve providers map with provider_guid_to_name_map
def provider_guid_to_name_map():
    global providers_map
    with oracledb.connect(user=user, password=pwd, dsn="localhost:1521/orcl") as connection:
        with connection.cursor() as cursor:
            sql = """select guid, provider_id from I_10_0_TESTBED_BUSINESS.provider"""
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

    header = {"Content-Type": "application/json",
              "Authorization": access_token,
              "User-id": "legacy_migration"}

    resp = requests.put(f"{url_root}/ingest/providers/{providers_map[provider_guid]}/data-quality-summaries/{id}",
                        data=json.dumps(umm),
                        headers=header)
    if resp.status_code >= 300:
        failure_count += 1
        print(f"Failed to migrate [{id}] with status code: [{resp.status_code}], error: {resp.text}")
    else:
        success_count += 1
        data_quality_summaries[id] = get_concept_id(resp.text)
        print(f"Successfully migrated [{id}]")


def migrate_dqsas(dqs_id, coll_concept_ids):
    global success_count, failure_count
    concept_ids = [{"concept_id": id} for id in coll_concept_ids]

    header = {"Content-Type": "application/json",
              "Authorization": access_token,
              "User-id": "legacy_migration"}

    resp = requests.post(f"{url_root}/search/associate/{data_quality_summaries[dqs_id]}",
                         data=json.dumps(concept_ids),
                         headers=header)
    if resp.status_code >= 300:
        failure_count += 1
        print(f"Failed to migrate associations for [{dqs_id}], concept_id: [{data_quality_summaries[dqs_id]}] with status code: [{resp.status_code}], error: {resp.text}")
    else:
        success_count += 1
        print(f"Successfully migrated associations for [{dqs_id}], concept_id: [{data_quality_summaries[dqs_id]}]")


def migrate_data_quality_summary():
    global success_count, failure_count
    success_count = 0
    failure_count = 0
    start = time.time()
    print("Starting DataQualitySummary data migration...")

    with oracledb.connect(user=user, password=pwd, dsn="localhost:1521/orcl") as connection:
        with connection.cursor() as cursor:
            sql = """select GUID, NAME, SUMMARY, OWNER_PROVIDER_GUID from I_10_0_TESTBED_BUSINESS.DATA_QUAL_SUMMARY_DEF"""
            for r in cursor.execute(sql):
                migrate_dqs_row(r[0], r[1], r[2], r[3])

    end = time.time()
    print(f"Total DataQualitySummary data migration time spent: {end - start} seconds, Succeeded: {success_count}, Failed: {failure_count}")


def migrate_data_quality_summary_assignment():
    global success_count, failure_count
    success_count = 0
    failure_count = 0
    start = time.time()
    print("Starting DATA_QUAL_SUMMARY_ASSIGN data migration...")
    assignments = collections.defaultdict(list)

    with oracledb.connect(user=user, password=pwd, dsn="localhost:1521/orcl") as connection:
        with connection.cursor() as cursor:
            sql = """select DEFINITION_GUID, CATALOG_ITEM_GUID from I_10_0_TESTBED_BUSINESS.DATA_QUAL_SUMMARY_ASSIGN"""
            for r in cursor.execute(sql):
                assignments[r[0]].append(r[1])

    for k, v in assignments.items():
       migrate_dqsas(k, v)

    end = time.time()
    print(f"Total DATA_QUAL_SUMMARY_ASSIGN data migration time spent: {end - start} seconds, Succeeded: {success_count}, Failed: {failure_count}")


# Main
provider_guid_to_name_map()
migrate_data_quality_summary()
migrate_data_quality_summary_assignment()
