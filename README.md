# cmr-search-app

Provides a public search API for concepts in the CMR.

## TODO

  * Draw informal design for this application
  * Test against indexed collections as indexed by catalog-rest
    * This will be a way to make it work before other components are complete.
    * Use curl requests to send searches.


## Example CURL requests


### Find all collections
```
curl -H "Accept: application/json" -i "http://localhost:3003/collections"
```

### Find all collections with a bad parameter
```
curl -H "Accept: application/json" -i "http://localhost:3003/collections?foo=5"
```

### Find all collections with an entry title
```
curl -H "Accept: application/json" -i "http://localhost:3003/collections?entry_title\[\]=DatasetId%204"
```

### Find all collections with a dataset id (alias for entry title)
```
curl -H "Accept: application/json" -i "http://localhost:3003/collections?dataset_id\[\]=DatasetId%204"
```

### Find all collections with a entry title case insensitively
```
curl -H "Accept: application/json" -i "http://localhost:3003/collections?entry_title\[\]=datasetId%204&options\[entry_title\]\[ignore_case\]=true"
```

### Find all collections with a entry title pattern
```
curl -H "Accept: application/json" -i "http://localhost:3003/collections?entry_title\[\]=DatasetId*&options\[entry_title\]\[pattern\]=true"
```

### Find all collections with multiple dataset ids
```
curl -H "Accept: application/json" -i "http://localhost:3003/collections?entry_title\[\]=DatasetId%204&entry_title\[\]=DatasetId%205"
```

### Find all collections with multiple temporal
```
curl -H "Accept: application/json" -i "http://localhost:3003/collections?temporal[]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z,30,60&temporal[]=2000-01-01T10:00:00Z,,30&temporal[]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z"
```

### Find as XML
TODO implement support for retrieving in XML.
Also make sure enough information is returned that Catalog-REST can work.
```
curl -H "Accept: application/xml" -i "http://localhost:3003/collections"
```



## Prerequisites

You will need [Leiningen][1] 1.7.0 or above installed.

[1]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein ring server

## License

Copyright © 2014 NASA
