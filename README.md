# cmr-ingest-app

This is the ingest component of the CMR system. It is responsible for collaborating with metadata db and indexer components of the CMR system to maintain the lifecycle of concepts coming into the system.

## Prerequisites

You will need [Leiningen][1] 1.7.0 or above installed.

[1]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein ring server

## Curl statements
- ensure Metadata db, ES, Indexer, Ingest are functioning

......
create concept:
curl -i -v  -X PUT -H "Content-Type:application/iso19115+xml" -H "Accept:application/json" --data \
"<Collection> <ShortName>ShortName_Larc</ShortName> <VersionId>Version01</VersionId> <InsertTime>1999-12-31T19:00:00-05:00</InsertTime> <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate> <LongName>LarcLongName</LongName> <DataSetId>LarcDatasetId</DataSetId> <Description>A minimal valid collection</Description> <Orderable>true</Orderable> <Visible>true</Visible> </Collection>"  http://localhost:3002/providers/CurlPROV009/collections/nativeId8

sample output:
{"concept-id":"C12-CurlPROV009","revision-id":0}

index check:
curl -i -v -XGET "http://localhost:9200/collections/_search?pretty=true&q=_id:C12-CurlPROV009"
 (we should see  hit total = 1)

mdb check: LATER
.........

delete concept:
curl -i -v -XDELETE -H "Content-Type: application/json" http://localhost:3002/providers/CurlPROV009/collections/nativeId8

sample output:
{"concept-id":"C12-CurlPROV009","revision-id":1}

- sample url to check concept existence in index
curl -XGET "http://localhost:9200/collections/_search?pretty=true&q=_id:C27-PROV968964”
 (we should see  hit total = 0)
.........

get concept id: 
curl -XGET http://localhost:3001/concept-id/collection/PROV99622/nativeId1

output: {"concept-id":"C13-PROV99622"}

.............
## License

Copyright © 2014 NASA
