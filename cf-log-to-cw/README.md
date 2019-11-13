# CMR CloudFront Log to CloudWatch exporter

This uses the [Serverless Framework](https://serverless.com/) to define a lambda that gunzips the CloudFront logs and writes the log out to the lambda's CloudWatch log. The Cloudwatch log will be picked up by Splunk agent and become searchable in Splunk.

This is just a quick and dirty dump of the CloudFront log to CloudWatch, no metadata timestamp will be preserved. We will use the Splunk indexing and searching capability to analyze the CloudFront logs rather than using CloudWatch.

## Development Quick Start

### Prerequisites

* node.js 12.12.0
* AWS CLI

### Setup

`npm install -g serverless`
`npm install serverless-pseudo-parameters`
`npm install serverless-plugin-log-subscription`

### Deploying

`serverless deploy -v --stage <environment name> --cfs3bucket <cloudfront logs bucket>`

For example: `serverless deploy -v --stage smwl --cfs3bucket cloudfront-logs-1234` will deploy a CloudFormation stack including a lambda function with name `cmr-cf-log-to-cw-smwl-export` in the Small Workload environment.

### Note
The IAM role resource `IamRoleCustomResourcesLambdaExecution` in serverless.yml cannot be renamed. It is needed by Serverless internally to support existing s3 bucket and it has to be that exact name.
