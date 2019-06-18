# browse-scaler

Nodejs application to return thumbnails for NASA's Earthdata Search

# Building

## NOTE: because Sharp uses C++ extensions, it must be built in a docker container to run on AWS (unless your machine is also linux)

You can do this by running `docker-compose up --build` from the `browse-scaler/src` directory.

You are able to package the code for deployment easily with the `zip-browse-scaler.sh` script. This script builds the node_modules
automatically do you do not have to worry about manual builds.

# Testing

We use the `Jest` framework and tests can be run by going into the `src/__test__` directory and running `jest <filename>`.
For example `jest cmr.test.js` will run that test file
