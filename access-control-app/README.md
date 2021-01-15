# CMR Access Control Application

The CMR Access Control Application provides access to create, retrieve, update, and find access control groups, access control rules, and authentication tokens.

## <a name="caches"></a> Caches

The caches of the Access Control application can be queried to help debug caches issues in the system. Endpoints are provided for querying the contents of the various caches used by the application.

The following curl will return the list of caches:

    curl -i http://localhost:3011/access-control/caches

The following curl will return the keys for a specific cache:

    curl -i http://localhost:3011/access-control/caches/<cache-name>

This curl will return the value for a specific key in the named cache:

    curl -i http://localhost:3011/access-control/caches/<cache-name>/<cache-key>

## Reindexing All Groups

Reindexing all groups can be accomplished by sending a post request to /reindex-groups

    curl -i -XPOST http://localhost:3011/access-control/reindex-groups?token=XXXX

## Reindexing All Acls

Reindexing all acls can be accomplished by sending a post request to /reindex-acls

    curl -i -XPOST http://localhost:3011/access-control/reindex-acls?token=XXXX

### Run database migration

Update elasticsearch mappings to the latest version:

    curl -v -XPOST -H "Echo-Token: XXXX" http://localhost:3011/db-migrate

***

## License

Copyright © 2016-2021 NASA
