# browse-scaler

Nodejs application to return thumbnails for NASA's Earthdata Search

# Building

## NOTE: because Sharp uses C++ extensions, it must be built in a docker container to run on AWS (unless your machine is also linux)

You can do this by running `docker-compose up --build` from the `browse-scaler/src` directory.

You are able to package the code for deployment easily with the `zip-browse-scaler.sh` script. This script builds the node_modules
automatically so you do not have to worry about manual builds.

# Testing

We use the `Jest` framework and tests can be run by going into the `src/__test__` directory and running `jest <filename>`.
For example `jest cmr.test.js` will run that test file. Test output is produced in the junit format because it cooperates better with our CI/CD environment

# Testing locally

The lambda can be executed locally using Docker and the amazon/aws-lambda-nodejs:14 image. Run the following in `browse-scaler root directory` to start the lambda in docker and listening on host port 9000.

```
docker run --rm -p 9000:8080 -e REDIS_URL=docker.for.mac.host.internal -e CMR_ROOT=cmr.sit.earthdata.nasa.gov -e CMR_ENVIRONMENT=sit -e CMR_ECHO_TOKEN=$sit_token -v $PWD/src:/var/task amazon/aws-lambda-nodejs:14 index.handler
```
Note: this docker container does not auto refresh code changes. You need to restart the container to pick up any code changes.

`event_C1000001740-NSIDC_ECS.json` and `event_C1200382534-CMR_ONLY.json` are example event JSON files that return the "Not Found" image
and a valid image, respectively.

To test browse-scaler processing `event_C1000001740-NSIDC_ECS.json`, run the following:
```
curl -XPOST "http://localhost:9000/2015-03-31/functions/function/invocations" -d @./event_C1000001740-NSIDC_ECS.json
```

# Invoking

This function can be invoked with the following routes:

- For collections: https://cmr.earthdata.nasa.gov/browse-scaler/browse_images/datasets/<COLLECTION-ID>?h=<HEIGHT>&w=<WIDTH>
- For granules: https://cmr.earthdata.nasa.gov/browse-scaler/browse_images/granules/<GRANULE-ID>?h=<HEIGHT>&w=<WIDTH>
  Note: the collection endpoint will check the first available granule for relevant browse imagery before showing the `image-not-found` response
