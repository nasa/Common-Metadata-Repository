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

### POST /concepts
params: [concept] - revision-id optionally in concept
returns: revision-id.  revision-id begins at 0.
throws error if revision-id does not match what it will be when saved

### GET /concepts/#concept-id
params: none
returns: latest revision of a concept with the given concept-id

### GET /concepts/#concept-id/#revision-id
params: none
returns: concept with the given concept-id and revision-id

### POST /concepts/search
params: [{"concept-revisions": [concept-id/revision-id tuple] ...]}]
returns: list of concepts matching the touples provided in the body of the POST

Different ways to retrieve concepts
1 - by concept-id and revision-id
2 - by concept-id (latest revision)
3 - multiple by concept-ids and revision-ids

## License

Copyright © 2014 NASA
