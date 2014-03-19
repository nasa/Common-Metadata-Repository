# cmr-metadata-db-app

## Web API

### Sample Concept JSON
	{
		"concept-type": "collection"
   	"native-id": "provider collection id"
   	"concept-id": "C1-PROV1"
   	"provider-id": "PROV1"
   	"metadata": "xml here"
   	"format": "echo10"
   	"revision-id": 1 (optional field)
	}

### Sample Tombstone (deleted concept) JSON
	{
		"concept-type": "collection"
		"native-id": "provider collection id"
   	"concept-id": "C1-PROV1"
   	"provider-id": "PROV1"
   	"deleted": true
   	"revision-id": 10
   }

### GET /concept-id
params: [concept-type provider-id native-id]
returns: new or existing concept-id

__Example Curl:__ 
curl -v http://localhost:3001/concept-id/collection/PROV1/native-id

### POST /concepts
params: [concept] - revision-id optionally in concept
returns: revision-id.  revision-id begins at 0.
throws error if revision-id does not match what it will be when saved

__Example Curl:__ 
curl -v -XPOST -H "Content-Type: application/json" -d '{"concept-type": "collection", "native-id": "native-id", "concept-id": "C1-PROV1", "provider-id": "PROV1", "metadata": "xml here", "format": "echo10"}' http://localhost:3001/concepts/

### GET /concepts/#concept-id
params: none
returns: latest revision of a concept with the given concept-id

__Example Curl:__
curl -v http://localhost:3001/concepts/C1-PROV1

### GET /concepts/#concept-id/#revision-id
params: none
returns: concept with the given concept-id and revision-id

__Example Curl:__
curl -v http://localhost:3001/concepts/C1-PROV1/2

### POST /concepts/search
params: [{"concept-revisions": [concept-id/revision-id tuple] ...]}]
returns: list of concepts matching the touples provided in the body of the POST

__Example Curl:__
curl -v -XPOST -H "Content-Type: application/json" -d '{"concept-revisions": [["C1-PROV1", 1], ["C2-PROV1", 1]]}' http://localhost:3001/concepts/search


Different ways to retrieve concepts
1 - by concept-id and revision-id
2 - by concept-id (latest revision)
3 - multiple by concept-ids and revision-ids

## License

Copyright © 2014 NASA
