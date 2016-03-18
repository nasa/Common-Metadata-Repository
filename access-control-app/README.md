# CMR Access Control Application

The CMR Access Control Application provides access to create, retrieve, update, and find access control groups, access control rules, and authentication tokens.

## <a name="caches"></a> Caches

The caches of the Access Control application can be queried to help debug caches issues in the system. Endpoints are provided for querying the contents of the various caches used by the application.

The following curl will return the list of caches:

    curl -i %CMR-ENDPOINT%/caches

The following curl will return the keys for a specific cache:

    curl -i %CMR-ENDPOINT%/caches/<cache-name>

This curl will return the value for a specific key in the named cache:

    curl -i %CMR-ENDPOINT%/caches/<cache-name>/<cache-key>

***

## License

Copyright © 2016 NASA
