import schedule
import time
import urllib3

service_port_map = {
    'metadata-db' : '3001',
    'ingest' : '3002',
    'search' : '3003',
    'indexer' : '3004',
    'bootstrap' : '3006',
    'virtual-product' : '3009',
    'access-control' : 3011
}

jobs_map = {
    "RefreshKMSCache" : {
        "endpoint" : "localhost:3006/caches/refresh/kms"
    }
}

def run_job(job):
    print('send POST to ' + jobs_map[job]["endpoint"] + ' for job ' + job)
    #urllib3.request("POST", jobs_map[job]["endpoint"], headers={"Authorization" : "mock-echo-system-token"})

schedule.every(30).seconds.do(run_job, job="RefreshKMSCache")

while True:
    schedule.run_pending()
    time.sleep(1)