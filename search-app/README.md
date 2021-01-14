# cmr-search-app

Provides a public search API for concepts in the CMR.

## API Documentation

API docs for the search-app project are located in `api.md`. They can be
generated into the static site by running the following:

```
$ lein generate-static
```

### Data.JSON

Collections with gov.nasa.eosdis tag and returned as opendata to be harvested
by data.nasa.gov can be retrieved with:

    curl -i http://localhost:3003/socrata/data.json

## License

Copyright © 2014-2021 NASA
