# db-es-audit

Python application to compare and fix any granule discrepancies between the database and elastic search.

There are 2 main programs find_granule_counts.py and find_granules.py. Find_granule_counts.py is the starting point and the basic flow is:

 1) Get the list of providers that have collections by querying the database to see which ones contain granules
 
 2) Get a list of all collections for those providers that have granules. For each provider save the list of collections.

 3) For a provider get the granule counts for each collection. If the collection has granules, get the elastic search granule count. If the granule counts do not match, then record and save that information. this step is done in a limited parallel fashion by provider.  

 4) Once all of the granule count information has been saved, the find_granule_counts program launches the find_granules program in a limited parallel fashion by provider. Both the database and elastic search is checked to find missing granules. When a missing granule has been found, elastic search is corrected. Not all granules are checked however, once the number of offending granules has been fixed, the program terminates.


## Infrastructure
The underlying AWS infrastructure is in terraform using the db_es_audit module. The following steps can be taken to deploy the terraform module

 1) In the cmr-cloud-deployments repository if terraform has not been run before you will need to run: terraform init assuming you have terraform installed.

 2) To get the current environment terraform managed resources, run: ./scripts/generate-backend.sh <environment (sit|uat|prod)> <us-east-1>
 This step will create 3 files in your directory
 terraform/terraform_backend.tf
 terraform/deployments/core_infrastructure/terraform_backend.tf
 terraform/deployments/workload/terraform_backend.tf

 3) Now the terraform module can be deployed. Go into the terraform directory: cd terraform
 Then run: terraform plan -target=module.db_es_audit -out plan
 Once the plan has completed run the following to apply the changes: terraform apply plan (plan being the name of the output file.) 

 Once the infrastructure is in place the software can be deployed. First off, the software is run as AWS ECS tasks. The find_granule_counts' task definition is called cmr-db-es-audit-granule-counts-<environment> and the find_granules task definition is called cmr-db-es-audit-find-granules-<environment>. There will only be 1 cmr-db-es-audit-granule-counts-<environment> task, but there will be multiple cmr-db-es-audit-find-granules-<environment> tasks

 The following environment variables need to be set in AWS Parameter Store:
 
 * DB_USERNAME: The oracle read only user name
 * DB_PASSWORD: The oracle read only password that is stored as a secure string
 * DB_URL: The oracle database URL
 * GRAN_ELASTIC_PORT: The granule elastic cluster load balancer port. If not set then it uses the default value of 9200.
 * GRAN_ELASTIC_HOST: The full AWS granule Elastic search load balancer name.
 * ELASTIC_PORT: The collection elastic cluster load balancer port. If not set then it uses the default value of 9200.
 * ELASTIC_HOST: The full AWS collection elastic search load balancer name.
 * SQS_QUEUE_URL: The indexer queue URL.
 * BATCH_SIZE: The number of records that are compared between the database and elastic search per request to each service. The bigger the number the faster the processing, but also more memory is used.
 * ENVIRONMENT: The lowercase environment name

 The following environment variables are created by terraform and should not be created manually.

 * AUDIT_S3_BUCKET_NAME: The bucket where all auditing files go.
 * EFS_PATH: The AWS Elastic File System mount directory name.


## Building and Deploying

Two docker image exist, one for each main program (find_granule_counts.py, find_granules.py). To build and deploy the find_granule_counts.py do the following:

 1) log in your docker with AWS ECR:
 aws ecr get-login-password --region "us-east-1" | docker login --username AWS --password-stdin <account number>.dkr.ecr.us-east-1.amazonaws.com

 2) Locally build:
 docker build -f Dockerfile_find_granule_counts -t cmr-db-es-audit-granule-counts .

 3) tag the image:
 docker tag cmr-db-es-audit-granule-counts:latest 832706493240.dkr.ecr.us-east-1.amazonaws.com/cmr-db-es-audit-granule-counts-sit:latest

 4) Push the image to the AWS ECR:
 docker push <account number>.dkr.ecr.us-east-1.amazonaws.com/cmr-db-es-audit-granule-counts-sit:latest

To build and deploy the find_granules.py do the following:

 1) log in your docker with AWS ECR:
aws ecr get-login-password --region "us-east-1" | docker login --username AWS --password-stdin <account number>.dkr.ecr.us-east-1.amazonaws.com

 2) Locally build:
 docker build -f Dockerfile_find_granules -t cmr-db-es-audit-find-granules .

 3) Tag the image:
 docker tag cmr-db-es-audit-find-granules:latest <account number>.dkr.ecr.us-east-1.amazonaws.com/cmr-db-es-audit-find-granules-sit:latest

 4) Push the image to the AWS ECR:
 docker push <account number>.dkr.ecr.us-east-1.amazonaws.com/cmr-db-es-audit-find-granules-sit:latest


## Testing

Run the run-tests-cicd.sh script.

## Running the program

The AWS EventBridge scheduler is setup to run this tool on a periodic basis. At the time of this writing the job fires on the 1st and 15th of every month at 2 AM ET. (EventBrige times are in UTC. In terraform, local time can be specified.)

To run the tool from a local machine run the following:
aws ecs run-task \
    --cluster "cmr-service-<environment>" \
    --task-definition "cmr-db-es-audit-granule-counts-<environment>" \
    --launch-type FARGATE \
    --network-configuration "awsvpcConfiguration={subnets=[subnet-#, ...],securityGroups=[sg-#, ...],assignPublicIp=DISABLED}" \
    --platform-version LATEST

The logs go to cloudwatch using the following streams:
/<environment>/cmr-db-es-audit-find-granules-<environment>
/<environment>/cmr-db-es-audit-granule-counts-<environment>
