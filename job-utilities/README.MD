# job-utilities

These utilites are used for assisting in job operations in a deployed environment and to a degree replicate job scheduling for local development

## job-details

A core piece for these utilities to work is the job-details.json. This file contains details on a jobs endpoint, what service the job is for, 
what schedule the job should be run on, etc. The structure of this file is described below.

{
    "JobName" :
    {
        "target" : {
            "endpoint" : "endpoint/for/job",
            "service" : "service-name",
            "single-target" : true/false
        },
        "scheduling" : {
            "type" : "cron",
            "timing" : {
                "minutes" : 0,
                "hours" : 7,
                "day-of-month" : "*",
                "month" : "*",
                "day-of-week" : "?",
                "year" : "*"
            }
        }
    }
}

{
    "JobName" :
    {
        "target" : {
            "endpoint" : "endpoint/for/job",
            "service" : "service-name",
            "single-target" : true/false
        },
        "scheduling" : {
            "type" : "interval",
            "timing" : {
                "minutes" : 0,
                "hours" : 7
            }
        }
    }
}