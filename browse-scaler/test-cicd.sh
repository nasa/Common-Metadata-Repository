#!/bin/bash
# Runs tests in docker and saves output as junit.xml

echo "Running tests..."
(cd src && docker-compose -f docker-compose.test.yml run --rm browse-scaler-cicd)

# Cleanup image
echo "Cleaning up image..."

(cd src && docker-compose -f docker-compose.test.yml stop)

docker rmi -f \
  $(docker inspect browse-scaler:test-cicd \
  | jq -r .[].Id \
  | cut -f2 -d: \
  )
