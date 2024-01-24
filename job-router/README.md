# job-router

Python application to route scheduled jobs to ECS services on AWS infrastructure

## Building

A docker image for local use can be built using 'docker build --platform linux/amd64 -t image-name:image-tag .'
Putting together a package for deployment to Lambda is done with the following steps:

mkdir package
pip install --target ./package -r requirements.txt
cd package
zip -r ../deployment_package.zip .
cd ..
zip deployment_package.zip lambda_function.py

## Deploying

A manual deployment of the zip package is easy. Simply go to the job-router Lambda and upload the zip package of code to update it.
Otherwise there is a deployment in Bamboo that uses the deploy_lambda.sh script in cmr-cloud-deployment.

## Testing/Running locally

The job-router Lambda can be tested locally by building the docker image and using the run-docker.sh script. Testing from local is fine,
but there's not much reason to actually use this code as a regular part of local dev, since it just routes to endpoints that are directly
available to local development.

## Invoking

When the Lambda is invoked there are certain fields that are required in the event package.
{
    "service": "service-name",
    "endpoint": "job-endpoint",
    "target": "single-or-not"
}

service - The service that the job needs to be run for (Bootstrap, Ingest, Search, etc.)
endpoint - The endpoint for the job to be run (i.e, if the service is "bootstrap" and the KMS cache needs to be refreshed, the endpoint is "caches/refresh/kms")
target - Some jobs need to run on a single instance of a service, others need to be run on all instances of a service. "single" will run on a single instance,
         anything else will hit each instance