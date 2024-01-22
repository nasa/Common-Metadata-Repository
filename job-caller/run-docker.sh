docker run --platform linux/amd64 \
-p 9000:8080 \
-e CMR_ROOT_URL=localhost \
-e CMR_JOB=refreshkmscache \
-e CMR_SERVICE=bootstrap \
-e AWS_ACCESS_KEY_ID= \
-e AWS_SECRET_ACCESS_KEY= \
-e AWS_DEFAULT_REGION=us-east-1 \
job-caller:test