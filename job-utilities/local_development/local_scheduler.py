"""
local_scheduler takes the job details json file to create
a simple schedule with the Python schedule library.
Interval jobs are simply translated unto the library,
cron jobs are assumed to run at a given HH:MM every day.
"""
import time
import json
import os
import schedule
import urllib3

service_ports_file_name = os.getenv("SERVICE_PORTS_FILE", "service-ports.json")

pool_manager = urllib3.PoolManager(headers={"Authorization" : "mock-echo-system-token"})

with open(service_ports_file_name, encoding="UTF-8") as service_ports_file:
    service_port_map = json.load(service_ports_file)

def build_endpoint(job):
    """
    Takes the job details and builds the local job endpoint
    """
    url = "http://{}:{}"
    port = service_port_map[job["target"]["service"]]
    return url.format(port, job["target"]["endpoint"])

def run_job(details, name):
    """
    Takes the job details and runs a POST request on the job endpoint.
    """
    print('send POST to ' + details["target"]["endpoint"] + ' for job ' + name)
    pool_manager.request("POST", build_endpoint(details))

def create_schedule():
    """
    Uses the job-details file to create local schedule using
    the schedule python library
    """
    with open('../job-details.json', encoding="UTF-8") as json_file:
        jobs_map = json.load(json_file)
        for job_name, job_details in jobs_map.items():
            #Cron scheduling will assume it is scheduled to run at a certain time every day
            if job_details["scheduling"]["type"] == "cron":
                hours = str(job_details["scheduling"]["timing"]["hours"]).zfill(2)
                minutes = str(job_details["scheduling"]["timing"]["minutes"]).zfill(2)
                print("Scheduling job " + job_name + " at " + hours + ":" + minutes)

                schedule.every().day.at(hours + ":" + minutes).do(run_job,
                                                      job_details=job_details, job_name=job_name)
            elif job_details["scheduling"]["type"] == "interval":
                minutes = job_details["scheduling"]["timing"].get("minutes", 0)
                hours = job_details["scheduling"]["timing"].get("hours", 0)
                total_minutes = minutes + hours*60
                print("Scheduling job for every " + str(total_minutes) + " minutes")

                schedule.every(total_minutes).seconds.do(run_job, job_details=job_details,
                                                         job_name=job_name)

if __name__ == '__main__':
    create_schedule()

    while True:
        schedule.run_pending()
        time.sleep(1)
