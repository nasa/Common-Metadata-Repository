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

# Testing locally using lambci

The lambda can be executed locally using Docker and the lambci/lambda image. The `local_test.sh` script will do this. E

# Invoking

This function can be invoked with the following routes:

- For collections: https://cmr.earthdata.nasa.gov/browse-scaler/browse_images/datasets/<COLLECTION-ID>?h=<HEIGHT>&w=<WIDTH>
- For granules: https://cmr.earthdata.nasa.gov/browse-scaler/browse_images/granules/<GRANULE-ID>?h=<HEIGHT>&w=<WIDTH>
  Note: the collection endpoint will check the first available granule for relevant browse imagery before showing the `image-not-found` response
