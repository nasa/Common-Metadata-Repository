# Autocomplete

# Production

```bash
export REDIS_PORT=<aws redis port>
export REDIS_HOST=<aws redis host>

# optional
export REDIS_PASSWORD=<redis password>
```

# Development

## Run Unit Tests
```bash
yarn test
```

## Run Live Development Environment
```bash
# Install depdendencies
yarn install

# Start local redis instance
docker-compose up

# Run serverless in offline
serverless offline

curl -XGET http://localhost:3000/autocomplete?q=<your term>
```
