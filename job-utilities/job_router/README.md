# job-router

Python application to route scheduled jobs to ECS services on AWS infrastructure

## Building

A docker image for local use can be built using 'docker build --platform linux/amd64 -t image-name:image-tag .'
Putting together a package for deployment to Lambda is done with the following steps:

```
mkdir package
pip3 install --target ./package -r requirements.txt
cd package
zip -r ../deployment_package.zip .
cd ..
zip deployment_package.zip lambda_function.py
```

## Deploying

A manual deployment of the zip package is easy. Simply go to the job-router Lambda and upload the zip package of code to update it.

## Testing/Running locally

Build the docker image with `docker build . -t job-router` then run it with `./run-docker.sh`. Job-router will now be running on a locally running docker container built upon the AWS lambda image.
With the docker container running, commands need to be sent as 
```curl -XPOST "http://localhost:9000/2015-03-31/functions/function/invocations" -d '
        {                         
            "endpoint" : "endpoint",
            "service" : "service",
            "single-target" : true|false,
            "request-type" : "POST|GET"
        }'```
Available payloads can be found in the `../job-details.json` file as the `"target"` item in each job listing.


## Invoking

When the Lambda is invoked there are certain fields that are required in the event package.
```
{
    "service": "service-name",
    "endpoint": "job-endpoint",
    "single-target": true/false,
    "request-type": "REST request type"
}
```

service - The service that the job needs to be run for (Bootstrap, Ingest, Search, etc.)
endpoint - The endpoint for the job to be run (i.e, if the service is "bootstrap" and the KMS cache needs to be refreshed, the endpoint is "caches/refresh/kms")
single-target - Some jobs need to run on a single instance of a service, others need to be run on all instances of a service. true will run on a single instance, false will hit each instance
request-type - The REST request type required for the service endpoint
