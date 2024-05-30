#!/usr/bin/env python3

"""
Run all the unit tests in threads so as to get more of them done in a shorter
amount of time. The processing follows a "worker-bee" style solution, where a
jobs list is populated with the projects to be tested and then workers pull off
that list and processes them. Jobs can be filtered out either because they do
not need to be tested (see opt_out) or because they have not been written in
such a way as to allow them to be run while other tests run, because they use a
common resource like redis (see run_alone).
"""

from concurrent.futures import ThreadPoolExecutor, wait
from threading import Lock
import argparse
import datetime
import multiprocessing
import os
import subprocess
import time

# Originally written using https://github.com/jceaser/shellwrap, the color file
# imported here is instead included as a file so as to not require any pip
# actions when running on bamboo
import color

# for a more descriptive run locally, remove these settings
env = {"color": False, "verbose": color.VMode.ERROR}

# pylint: disable=global-statement
# pylint: disable=global-variable-not-assigned
lock = Lock() # used to control when the globals are written to
active_threads = 0 # pylint: disable=invalid-name
total_time = 0 # pylint: disable=invalid-name
total_jobs = 0 # pylint: disable=invalid-name
work_list = [] # will hold a list of modules from lein to test
base = os.getcwd()

opt_out = ["cmr-dev-system",
    "cmr-system-int-test",
    "cmr-oracle-lib",
    "cmr-mock-echo-app",]

run_alone = ["cmr-common-app-lib", "cmr-indexer-app", "cmr-search-app"]

# ##############################################################################

def update_active_threads_locking(amount):
    "Up the number of active threads, but make sure only one thread at a time can do this"
    global active_threads
    with lock:
        active_threads += amount

def update_total_time_locking(durration):
    "Update the total time spent, but make sure only one thread at a time can do this"
    global total_time
    with lock:
        total_time = total_time + durration

def get_work_list():
    "Get a dump of all the projects that lein manages"
    cmd_result = subprocess.run(["lein", "dump"], check=True, capture_output=True)
    raw_work_list = cmd_result.stdout.decode('utf-8')

    list_of_projects = []
    for item in raw_work_list.split("\n"):
        item = item.strip(" ")
        if item.startswith("cmr-") and (item not in opt_out) and (item not in run_alone):
            list_of_projects.append(item[4:]) #remove cmr-
        else:
            print (f"skipping {item}")
    return list_of_projects

def worker(_context, id_number):
    "Function for one thread, this is run many times."
    global work_list, total_jobs, total_time, lock

    color.cprint(color.tcode.green, f"Task {id_number} started.", environment=env)

    update_active_threads_locking(1)

    while 0 < len(work_list):
        with lock:
            total_jobs = total_jobs + 1
            task = work_list.pop()

        if task is None or len(task)<1:
            color.cprint(color.tcode.red, f"restarting: {task}:{len(task)}",
                verbose=color.VMode.ERROR, environment=env)
            continue

        color.cprint(color.tcode.white, f"+task {id_number} working on {task}.",
            environment=env)

        st = time.time()
        # Run the external command in the task directory inside a try/except
        # block, to ensure thread never dies
        try:
            os.chdir(base+"/"+task)
            subprocess.run(["lein", "ci-utest"], check=True, capture_output=True)
        except Exception as e: # pylint: disable=broad-exception-caught
            color.cprint(color.tcode.red, f"{id_number}: {task} - {e}", environment=env)
        et = time.time()
        update_total_time_locking(et-st)

        # show status
        stat_msg = f"- task {id_number} took {(et-st):.3f}s on {task}. {len(work_list)} tasks left."
        color.cprint(color.tcode.yellow, stat_msg, environment=env)
    update_active_threads_locking(-1)

    color.cprint(color.tcode.red, f"Done {id_number}", environment=env)

def init_argparse() -> argparse.ArgumentParser:
    " Setup the argparse with the options for this script. "
    parser = argparse.ArgumentParser(
        usage="%(prog)s [OPTION]",
        description="Run the CMR module unit tests in parallel."
    )
    parser.add_argument('-c', '--color', action='store_true',
        help='Print statments using color.')
    parser.add_argument('-t', '--threads', type=int,
        help='Number of threads if between 1 and 32, otherwise half the CPUs')
    parser.add_argument(
        '-V', '--version', action='version',
        version = f"{parser.prog} version 1.0.0"
    )
    parser.add_argument('-v', '--verbose', action='store_true',
        help="Print more output")
    return parser

def main():
    " Main function, called in command line mode "
    global work_list, total_jobs, total_time

    #handle command line input
    parser = init_argparse()
    args = parser.parse_args()
    if args.verbose:
        env["verbose"] = color.VMode.WARN
    if args.color:
        env["color"] = True
    if args.threads is None or args.threads<1 or args.threads>32:
        args.threads = int(max(2, min(32, multiprocessing.cpu_count()/2)))

    print (f"{datetime.datetime.now()}")
    print ("This is the new script to run unit tests: run_unit_tests.py")

    with ThreadPoolExecutor() as executor:
        work_list = get_work_list()

        color.cprint(color.tcode.yellow,
            "Using {args.threads} threads on {len(work_list)} tasks.",
            environment=env)

        jobs = []

        # Create all the worker threads
        for iindex in range(args.threads):
            jobs.append(executor.submit(worker, env, iindex))
        for future in jobs:
            result = future.result()
            if result is not None:
                print(result)
        wait(jobs)
    if active_threads>0:
        color.cprint(color.tcode.white,
            f"there are still {active_threads} running.",
            environment=env)
        time.sleep(10)
    else:
        color.cprint(color.tcode.white,
            f"{active_threads} active threads remaining.",
            environment=env)

    # There are some tests which can not run along side each other because they
    # start up services.
    color.cprint('\033[0;36m', "Starting single threads", environment=env)
    work_list = [task[4:] for task in run_alone]
    worker({}, -1)

    color.cprint(color.tcode.yellow, f"Done processing {total_jobs}", environment=env)
    color.cprint(color.tcode.yellow, f"Total: {total_time:.3f}s", environment=env)
    color.cprint(color.tcode.yellow, f"Average: {total_time/total_jobs:.3f}s", environment=env)
    print (f"{datetime.datetime.now()}")

if __name__ == "__main__":
    main()
