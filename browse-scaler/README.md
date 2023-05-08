# browse-scaler

Nodejs application to return thumbnails for NASA's Earthdata Search

# Building

**NOTE**: because Sharp uses C++ extensions, it must be built in a Docker container to run on AWS (unless your machine is also linux). If you are using macOs
you may need to un-install sharp using: `npm uninstall sharp` and then re-install it using: `npm install --platform=linux --arch=x64 sharp`

You can do this by running `docker-compose up --build` from the `browse-scaler/src` directory.

You are able to package the code for deployment easily with the `zip-browse-scaler.sh` script. This script builds the node_modules
automatically so you do not have to worry about manual builds.

# Testing

We use the `Jest` framework and tests can be run by going into the `src/__test__` directory and running `jest <filename>`.
For example `jest cmr.test.js` will run that test file. Test output is produced in the junit format because it cooperates better with our CI/CD environment. To run the jest suite of tests you can utilize the `npm test` command.

# Testing locally

The lambda can be executed locally using Docker and the amazon/aws-lambda-nodejs:18 image. Run the following in `browse-scaler root directory` (It must be run on the root dir if you get an error `Error: Cannot find module 'index'` it is likely that you tried to start it up in the `/src` dir) to start the lambda in docker and listening on host port 9000. In order ot have active cache you must spin up your own redis docker container. This can be done with: `docker container run -p 6379:6379 redis:7` note if this is not installed on your machine initially, docker will pull down the image.

```
docker run --rm \
	-p 9000:8080 \
	-e REDIS_URL=docker.for.mac.host.internal \
	-e CMR_ROOT=cmr.sit.earthdata.nasa.gov \
	-e CMR_ENVIRONMENT=sit \
	-e CMR_ECHO_TOKEN=$sit_token \
	-v $PWD/src:/var/task \
	amazon/aws-lambda-nodejs:18 \
	index.handler
```

**Note**: this docker container does not auto refresh code changes. You need to restart the container to pick up any code changes.

[event_C1200377661-CMR_ONLY.json](event_C1200377661-CMR_ONLY.json) and [event_C1200382534-CMR_ONLY.json](event_C1200382534-CMR_ONLY.json) are example event JSON files that return the "Not Found" image
and a valid image, respectively.

To test browse-scaler processing `event_C1200377661-CMR_ONLY.json`, run the following:

Collections:

C1200382534-CMR_ONLY: will return with image
```
curl -XPOST \
	"http://localhost:9000/2015-03-31/functions/function/invocations" \
	-d @./event_C1200382534-CMR_ONLY.json
```

C1200377661-CMR_ONLY: Will return with the default image because the image is not found
```
curl -XPOST \
	"http://localhost:9000/2015-03-31/functions/function/invocations" \
	-d @./event_C1200377661-CMR_ONLY.json
```

Granules:

curl -XPOST \
	"http://localhost:9000/2015-03-31/functions/function/invocations" \
	-d @./event_G1200461852-CMR_ONLY.json
```

# Decoding Locally:

This website is helpful for decoding the responses 
	`https://devpal.co/base64-image-decode/`


# Invoking

This function can be invoked with the following routes:

For collections:

```https://cmr.earthdata.nasa.gov/browse-scaler/browse_images/datasets/<COLLECTION-ID>?h=<HEIGHT>&w=<WIDTH>```

For granules:

```https://cmr.earthdata.nasa.gov/browse-scaler/browse_images/granules/<GRANULE-ID>?h=<HEIGHT>&w=<WIDTH>```

**Note**: the collection endpoint will check the first available granule for relevant browse imagery before showing the `image-not-found` response

Creating events locally:
 to test events locally there are three required parameters `path, h, w`
 path is either datasets/<coll-concept-id> for collections and granules/<granule-concept-id> for granules. 'h' refers to what the height of the returned image should be in pixels and 'w' is the width in pixels for the returned image. There is also an optional fourth arg which specifies which granule image should be used. This is for cases where there are multiple browsable images in a granule metadata's links. This will match exactly the passed in granule image; if it is in the record it will return that image otherwise, it will return the first browsable image that can be found. If there are none it will return the default 'image cannot be found' image.

An example of a granule event. This will fetch the browse image for the granule in the path if it exists. 
{
    "path": "granules/G1200460416-ESA",
    "queryStringParameters": {
        "h ": 85,
        "w": 85
    }
}

Example of a granule event passing a specified image
{
    "path": "granules/G1200460416-ESA",
    "queryStringParameters": {
        "h ": 85,
        "w": 85,
        "imageSrc":"https://airsl2.gesdisc.eosdis.nasa.gov/data/Aqua_AIRS_Level2/AIRH2CCF.006/2002/243/AIRS.2002.08.31.028.L2.CC_H.v6.0.12.0.G14101130602.hdf.jpg"
    }
}
