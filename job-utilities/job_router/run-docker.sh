docker run \
-p 9000:8080 \
-e AWS_DEFAULT_REGION=us-east-1 \
-e CMR_ENVIRONMENT=local \
-e CMR_LB_NAME=https://localhost \
job-router
