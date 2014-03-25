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

General Workflow

Update Flow

  - Retrieve latest revision from DB using provider-id, concept-type, and native id.
  - Compare revision from client if given to DB revision. If the revision from the client is not the next one we send a conflict error back to the client.
  - Create a new concept record
    - increment revision from DB
    - Reuse concept-id
    - Set all other fields
  - Insert into table
  - If we get a conflict from a uniqueness constraint restart from beginning of this flow

Insert Flow

  - Retrieve latest revision from DB (and none are found)
  - Check if revision id sent by client is 0 if present. If the revision from the client is not 0 we send a conflict error back to the client.
  - Create a new concept record
    - Revision is 0
    - Generate a new concept-id using a sequence from Oracle or use value from client if provided.
      - This supports catalog rest specifying the concept-id.
    - Set all other fields
  - Insert into table
  - If we get a conflict from a uniqueness constraint restart from beginning of this flow


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

### DELETE /concepts/#concept-id
params: none
returns: the revision id of the tombstone generated for the concept

__Example Curl:__
curl -v -XDELETE localhost:3001/concepts/C1-PROV1

### DELETE /concepts/force-delete/:concept-id/:revision-id
params: none
returns: nothing (status 204)

__Example Curl:__
curl -v -XDELETE /concepts/force-delet/C1-PROV1/1

### POST /reset
params: none
returns: nothing (status 204)

__Example Curl:__
curl -v -XDELETE localhost:3001/reset


Different ways to retrieve concepts
1 - by concept-id and revision-id
2 - by concept-id (latest revision)
3 - multiple by concept-ids and revision-ids

## License

Copyright © 2014 NASA
