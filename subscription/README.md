# subscription

This project contains the subscription-worker python tool. This tool reads messages from an AWS SQS queue, checks to see if the user has permission to view these records, and then publishes them to the cmr-subscription-{env} topic, so that external users can receive messages to their subscriptions.

## To build and deploy this project manually for AWS - this is not for local development.

Cd into the subscription project.
You will need to make sure that Docker is up and running.
Buid the project: docker build -t {AWS Repository}/cmr-subscription-worker-{env}:latest .
Log in docker to the AWS repository: aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin {AWS Repository}
Using docker to push the deployment artifact: docker push {AWS Repository}/cmr-subscription-worker-{env}:latest
For the ECS to update the service: aws ecs update-service --force-new-deployment --service subscription-worker-sit --cluster cmr-service-sit


## locally
docker build -f Dockerfile.local -t subscription_worker .
run script start.sh
