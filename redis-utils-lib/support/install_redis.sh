#!/bin/bash

set -e

if [ $# -ne 2 ]; then
  printf "Usage: $0 <redis-version> <redis-hash>\n"
  exit 1
fi

rm -rf redis
mkdir redis
cd redis

redis_version="redis-$1"
redis_hash=$2

curl -s -o $redis_version.tar.gz "http://download.redis.io/releases/$redis_version.tar.gz"
downloaded_hash=$(openssl dgst -sha256 $redis_version.tar.gz | awk '{print $2}')

if [ "$downloaded_hash" != "$redis_hash" ]; then
  printf "ERROR: Hash does not match. Exiting...\n"
  cd -
  exit 1
fi

tar xzf $redis_version.tar.gz
(cd $redis_version && make)
mv $redis_version redis

cd -
printf "Successfully installed redis.\n"
