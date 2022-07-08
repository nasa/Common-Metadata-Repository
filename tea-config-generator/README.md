# CMR TEA Configuration Generator
A lambda project to generate [Thin Egress App][teacode] (TEA) configuration files using [Serverless][sls]

## Overrview
A set of lambda functions to run under the CMR domain name which generate a TEA configuration.

See: [Cumulus thin egress app][tea]

## Usage

The [run.sh](run.sh) command will execute many of the stages of the software needed
get the application started. To see the options supported, run `./run.sh -h`. To
start the application, run in one terminal window: `./run.sh -o`. This will install
the lambda functions and start the offline server. In another terminal window try
the following commands:

    curl 'http://localhost:3000/dev/configuration/tea/capabilities'
    curl -I 'http://localhost:3000/dev/configuration/tea/status'
    curl -H 'Authorization: Bearer uat-token-value' 'http://localhost:3000/dev/configuration/tea/provider/POCLOUD'

| Action       | URL                                       | Description |
| ------------ | ----------------------------------------- | ----------- |
| Capabilities | /configuration/tea/                       | Describe the service |
| Capabilities | /configuration/tea/capabilities           | Describe the service |
| Status       | /configuration/tea/status                 | Returns the service status |
| Generate     | /configuration/tea/provider/{provider-id} | Generate the TEA configuration file |

## Building

1. Install serverless using one of these commands:
    * `npm install -g serverless`
    * `curl -o- -L https://slss.io/install | bash`
2. Plugin: `serverless plugin install -n serverless-python-requirements`
3. Plugin: `serverless plugin install -n serverless-s3-local`
4. Python dependencies: `pip3 install -r requirements.txt`
5. Install locally: `serverless offline`
6. Run some example URLS: `./run.sh -e`

### Docker

The Dockerfile defines a node image which includes python3 and serverless for
use as a build and deployment environment when running under a CI/CD system.

## Deploying to AWS

Several methods are provided to publish this application to specific CMR AWS
environments. These processes can be reused for other similar environments based
on need.

### Deployment

The [serverless.yml](serverless.yml) file will create an IAM role for the lambda
functions and install them under an Application Load Balancer. The ARN is to be
provided. The IAM role will use the following Role name:
`tea-config-generator-role-${self:custom.env}-${self:provider.region}` and will
use `arn:aws:iam::${aws:accountId}:policy/NGAPShRoleBoundary` for a Permissions
Boundary. This setup is suitable for situations where a public URL is applied at
the Loadbalancer level. The CMR URL must be set if production CMR is not to be used. See example below.

* **NOTE**: This is the file used for deployment to SIT, UAT, and OPS

Example usage for UAT:

	AWS_PROFILE=<your-profile-name-here> \
	    AWS_TEA_CONFIG_CMR='https://cmr.uat.earthdata.nasa.gov' \
	    serverless deploy --stage uat

### Sandbox
The [serverless-sandbox.yml](serverless-sandbox.yml) file will also create an IAM
role and be bound to the same Permissions Boundary, however it will use an API 
Gateway to publish the lambda functions. The URL for the API Gateway can be used
for internal testing or for use within an AWS account. 

* **NOTE**: This is the file used for testing on AWS but not SIT, UAT, or OPS.


### Local Use
The [serverless-local.yml](serverless-local.yml) file is meant for use locally by
developers of the application. It will locally publish an API Gateway and allow
access to the lambda functions through that. It also includes an S3 bucket for
displaying the HTML API documentation. 

* **NOTE**: for localhost use only.

## Testing
Read the details in the ./run.sh script. There are functions which have all the
predefined parameters for running pylint and unit testing:

* Unit Testing: `./run.sh -u`
* Lint: `./run -l`

## License
Copyright © 2022-2022 United States Government as represented by the Administrator
of the National Aeronautics and Space Administration. All Rights Reserved.

----

[tea]: https://nasa.github.io/cumulus/docs/deployment/thin_egress_app "Thin Egress App"
[teacode]: https://github.com/asfadmin/thin-egress-app "TEA @ GitHub"
[sls]: https://serverless.com "Serverless"
