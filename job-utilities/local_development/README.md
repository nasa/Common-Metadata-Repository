# local development job-utilities

Python utilities to help with local development. Currently there is just a program for scheduling jobs to run locally

## local_scheduler

This program takes the job details from the `job-details.json` file and maps them to a schedule using the schedule Python library.
Cron jobs are assumed to be run once every day at a given time. Most jobs are likely to be interval based, which run every given period of time.

### Running

Ensure you have the right dependencies by running `pip3 install -r requirements.txt` and then simply run the local_scheduler.py program.
Note that the program currently does not run jobs immediately upon scheduling. To run a job right away as a test, use the -t flag.
