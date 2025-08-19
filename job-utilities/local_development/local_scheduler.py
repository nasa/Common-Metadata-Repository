#!/usr/bin/env python3

"""
local_scheduler takes the job details json file to create
a simple schedule with the Python schedule library.
Interval jobs are simply translated unto the library,
cron jobs are assumed to run at a given HH:MM every day.
"""
import argparse
import logging
import time
import json
import os
import sys

# pylint: disable=import-error
import schedule
import urllib3

# setup logger
logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
                    filename=f'{__file__}.log')
logger: logging.Logger = logging.getLogger(__name__)

service_ports_file_name = os.getenv("SERVICE_PORTS_FILE", "service-ports.json")
job_details_file_name = os.getenv("JOB_DETAILS_FILE", "../job-details.json")
cmr_host_name: str = os.getenv("CMR_HOST_NAME", "localhost")

pool_manager = urllib3.PoolManager(headers={"Authorization" : "mock-echo-system-token",
                                            "client-id": f'{__file__}'})

with open(service_ports_file_name, encoding="UTF-8") as service_ports_file:
    service_port_map = json.load(service_ports_file)

def build_endpoint(host_name, job):
    """
    Takes the job details and builds the local job endpoint
    """
    url = "http://{}:{}/{}"
    port = service_port_map[job["target"]["service"]]
    return url.format(host_name, port, job["target"]["endpoint"])

def run_job(job_details: dict, job_name :str):
    """
    Takes the job details and runs a REST request on the job endpoint.
    """
    logger.info('send ' + job_details["target"]["request-type"] + \
          ' to ' + job_details["target"]["endpoint"] + ' for job ' + job_name)
    url: str = build_endpoint(cmr_host_name, job_details)
    pool_manager.request(job_details["target"]["request-type"], url)

def create_schedule():
    """
    Uses the job-details file to create local schedule using
    the schedule python library
    """
    with open(job_details_file_name, encoding="UTF-8") as json_file:
        jobs_map = json.load(json_file)
        for job_name, job_details in jobs_map.items():
            #Cron scheduling will assume it is scheduled to run at a certain time every day
            if job_details["scheduling"]["type"] == "cron":
                hours = str(job_details["scheduling"]["timing"]["hours"]).zfill(2)
                minutes = str(job_details["scheduling"]["timing"]["minutes"]).zfill(2)
                logger.info("Scheduling job " + job_name + " at " + hours + ":" + minutes)

                schedule.every().day.at(hours + ":" + minutes).do(run_job,
                                                      job_details=job_details, job_name=job_name)
            elif job_details["scheduling"]["type"] == "interval":
                minutes = job_details["scheduling"]["timing"].get("minutes", 0)
                hours = job_details["scheduling"]["timing"].get("hours", 0)
                total_minutes = minutes + hours*60
                logger.info("Scheduling job for every %d minutes", total_minutes)

                schedule.every(total_minutes).seconds.do(run_job, job_details=job_details,
                                                         job_name=job_name)

def main():
    """ The primary interface for this script. """
    parser = argparse.ArgumentParser(description="External CMR scheduler")
    parser.add_argument('-t', '--test', action='store_true',
                        help='Do a test run of RefreashKMSCache and exit.')
    args = parser.parse_args()

    if args.test:
        # use these next lines to force a test on a very specific job
        test_detail = {"target": {"request-type": "POST",
            "service": "bootstrap",
            "single-target": True,
            "endpoint": "caches/refresh/kms"}}
        run_job(test_detail, "RefreshKMSCache")
        sys.exit()

    create_schedule()
    while True:
        schedule.run_pending()
        time.sleep(1)

if __name__ == '__main__':
    main()
