#!/bin/sh

API_NAME=browse-scaler
REGION=us-east-1
STAGE=test

function fail() {
    echo $2
    exit $1
}

awslocal lambda create-function \
    --region ${REGION} \
    --function-name ${API_NAME} \
    --runtime nodejs8.10 \
    --handler index.handler \
    --memory-size 128 \
    --zip-file fileb://src/scaler.zip \
    --role arn:aws:iam::123456:role/irrelevant

[ $? == 0 ] || fail 1 "Failed: AWS / lambda / create-function"

LAMBDA_ARN=$(awslocal lambda list-functions --query "Functions[?FunctionName==\`${API_NAME}\`].FunctionArn" --output text --region ${REGION})

awslocal apigateway create-rest-api \
    --region ${REGION} \
    --name ${API_NAME}

[ $? == 0 ] || fail 2 "Failed: AWS / apigateway / create-rest-api"

API_ID=$(awslocal apigateway get-rest-apis --query "items[?name==\`${API_NAME}\`].id" --output text --region ${REGION})
PARENT_RESOURCE_ID=$(awslocal apigateway get-resources --rest-api-id ${API_ID} --query 'items[?path==`/`].id' --output text --region ${REGION})

awslocal apigateway create-resource \
    --region ${REGION} \
    --rest-api-id ${API_ID} \
    --parent-id ${PARENT_RESOURCE_ID} \
    --path-part "{browse-scaler}"

[ $? == 0 ] || fail 3 "Failed: AWS / apigateway / create-resource"

RESOURCE_ID=$(awslocal apigateway get-resources --rest-api-id ${API_ID} --query 'items[?path==`/{browse-scaler*}`].id' --output text --region ${REGION})

awslocal apigateway put-method \
    --region ${REGION} \
    --rest-api-id ${API_ID} \
    --resource-id ${RESOURCE_ID} \
    --http-method GET \
    --request-parameters "method.request.path.browse-scaler=true" \
    --authorization-type "NONE" \

[ $? == 0 ] || fail 4 "Failed: AWS / apigateway / put-method"

awslocal apigateway put-integration \
    --region ${REGION} \
    --rest-api-id ${API_ID} \
    --resource-id ${RESOURCE_ID} \
    --http-method GET \
    --type AWS_PROXY \
    --integration-http-method POST \
    --uri arn:aws:apigateway:${REGION}:lambda:path/2015-03-31/functions/${LAMBDA_ARN}/invocations \
    --passthrough-behavior WHEN_NO_MATCH \

[ $? == 0 ] || fail 5 "Failed: AWS / apigateway / put-integration"

awslocal apigateway create-deployment \
    --region ${REGION} \
    --rest-api-id ${API_ID} \
    --stage-name ${STAGE} \

[ $? == 0 ] || fail 6 "Failed: AWS / apigateway / create-deployment"

ENDPOINT=http://localhost:4567/restapis/${API_ID}/${STAGE}/

echo "API available at: ${ENDPOINT}"

echo "Testing GET:"
curl -i ${ENDPOINT}
