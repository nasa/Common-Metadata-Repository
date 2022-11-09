import json
import oracledb
import os
import requests
import time

# returns strings or bytes instead of a locator
oracledb.defaults.fetch_lobs = False

user = os.environ.get('MIGRATE_USER')
pwd = os.environ.get('MIGRATE_PWD')
access_token = os.environ.get('ACCESS_TOKEN')

url_root = "https://cmr.sit.earthdata.nasa.gov"
providers_map = {}
success_count: int = 0
failure_count: int = 0


## retrieve providers map with provider_guid_to_name_map
def provider_guid_to_name_map():
    global providers_map
    with oracledb.connect(user=user, password=pwd, dsn="localhost:1521/orcl") as connection:
        with connection.cursor() as cursor:
            sql = """select guid, provider_id from I_10_0_TESTBED_BUSINESS.provider"""
            for r in cursor.execute(sql):
                providers_map[r[0]] = r[1]

def migrate_dqs_row(id, name, summary, provider_guid):
    global success_count, failure_count
    umm = {"Id": id,
           "Name": name,
           "Summary": summary,
           "MetadataSpecification": {
               "Name": "DataQualitySummary",
               "Version": "1.0.0",
               "URL": "https://cdn.earthdata.nasa.gov/generics/dataqualitysummary/v1.0.0"}}

    header = {"Content-Type": "application/json",
              "Authorization": access_token,
              "User-id": "legacy_migration"}

    resp = requests.put(f"{url_root}/ingest/providers/{providers_map[provider_guid]}/dataqualitysummaries/{id}",
                        data=json.dumps(umm),
                        headers=header)
    if resp.status_code >= 300:
        failure_count += 1
        print(f"Failed to migrate [{id}] with status code: [{resp.status_code}], error: {resp.text}")
    else:
        success_count += 1
        print(f"Successfully migrated [{id}]")

def migrate_data_quality_summary():
    start = time.time()
    print("Starting DataQualitySummary data migration...")
    provider_guid_to_name_map()

    with oracledb.connect(user=user, password=pwd, dsn="localhost:1521/orcl") as connection:
        with connection.cursor() as cursor:
            sql = """select GUID, NAME, SUMMARY, OWNER_PROVIDER_GUID from I_10_0_TESTBED_BUSINESS.DATA_QUAL_SUMMARY_DEF"""
            for r in cursor.execute(sql):
                migrate_dqs_row(r[0], r[1], r[2], r[3])

    end = time.time()
    print(f"Total DataQualitySummary data migration time spent: {end - start} seconds, Succeeded: {success_count}, Failed: {failure_count}")


## Main
migrate_data_quality_summary()
