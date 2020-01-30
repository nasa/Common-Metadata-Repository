# Autocomplete

# Production

```bash
export REDIS_PORT=<aws redis port>
export REDIS_HOST=<aws redis host>
```

# Development

```bash
# Install depdendencies
yarn install

# Start local redis instance
docker-compose up

# Run serverless in offline
serverless offline

curl -XGET http://localhost:3000/autocomplete?q=<your term>
```

# Architecture
redis stores indexed hashes from flexsearch
retrieve those hashes based on facets provided, or all


update/generate hashes daily? hourly?
