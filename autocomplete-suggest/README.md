# Autocomplete

## Run Unit Tests
```bash
yarn test
```

## Build Production
```bash
yarn install
yarn build
```

## Environment Variables

| Variable       | Desc | Default | 
|----------------|------|---------|
| CMR_SEARCH_API | root path for CMR search API | https://cmr.earthdata.nasa.gov/search |


| Variable   | Desc | Default | Valid Values |
|------------|------|---------|--------|
| ES_VERSION |  Elasticsearch Version | 7 |  1, 7 |                                             
| ES_HOST   | host name | localhost | |
| ES_PORT   | ES port | 9200 | |
| ES_HTTP_SCHEMA | protocol | https ||

## Run Live Development Environment
```bash
# Install depdendencies
yarn install

# Start local containers
docker-compose up

# Run serverless in offline
serverless offline

curl -XGET http://localhost:3000/autocomplete?q=<your term>
```
