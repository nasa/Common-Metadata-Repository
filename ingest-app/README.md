# cmr-ingest-app

This is the ingest component of the CMR system. It is responsible for collaborating with metadata db and indexer components of the CMR system to maintain the lifecycle of concepts coming into the system.

## Setting up the database

There are two ways database operations can be done. It can happen through leiningen commands for local development or using the built uberjar.

### Leiningen commands

1. Create the user

```
lein create-user
```

2. Run the migration scripts

```
lein migrate
```

You can use `lein migrate -version version` to restore the database to
a given version. `lein migrate -version 0` will clean the datbase
completely.

3. Remove the user

```
lein drop-user
```

### Java commands through uberjar

1. Create the user

```
CMR_DB_URL=thin:@localhost:1521:orcl CMR_INGEST_PASSWORD=****** java -cp target/cmr-ingest-app-0.1.0-SNAPSHOT-standalone.jar cmr.db create-user
```

2. Run db migration

```
CMR_DB_URL=thin:@localhost:1521:orcl CMR_INGEST_PASSWORD=****** java -cp target/cmr-ingest-app-0.1.0-SNAPSHOT-standalone.jar cmr.db migrate
```

You can provider additional arguments to migrate the database to a given version as in lein migrate.

3. Remove the user

```
CMR_DB_URL=thin:@localhost:1521:orcl CMR_INGEST_PASSWORD=****** java -cp target/cmr-ingest-app-0.1.0-SNAPSHOT-standalone.jar cmr.db drop-user
```

### Message queues

The ingest application will publish messages for the indexer application to consume.  The messages will be to index or delete concepts from elasticsearch.  Messaging is handled using the message-queue-lib which uses RabbitMQ.

#### Ingest unable to queue a message

This could happen because queueing the message times out, RabbitMQ has surpassed the configured limit for requests, or RabbitMQ is unavailable.  Ingest will treat this as an internal error and return the error to the provider. If this happens the data will still be left in Metadata DB, but won't be indexed. It's possible this newer version of a granule could be returned from CMR but it is unlikely to happen.

## License

Copyright © 2014-2015 NASA
