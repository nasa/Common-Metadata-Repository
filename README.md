# cmr-metadata-db-app

## Temporary Notes on id generation

TODO move this stuff to a more permanent home in the README

  * get /concept-id should return a 404 if it doesn't exist in the concepts table
  * There should only be a concepts table
  * Concepts table should have a uniqueness constraint on
    * provider ids and revision id
      * provider-id
      * native-id
      * concept-type
      * revision-id
    * concept id and revision id

If there's a conflict on provider ids and revision id uniqueness then we need to retry the transaction.

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


Impacts to Ingest

  * Ingest flow (after phase 1)
    * retrieve existing metadata
    * Use it for validation with new metadata
    * Send new concept with specified revision id to metadata db
      * If it fails then it would redo the whole flow.
  * Ingest Flow (during phase 1)
    * Send new concept to metadata db
      * Does not need to specify a revision id.
      * Does not need to retrieve a concept id.
        * It should check it's headers for a concept id from catalog rest. If present it should send it to the metadata db.


## Web API

### Sample Concept JSON

#### Collection
  	{
      "concept-type": "collection",
      "native-id": "provider collection id",
      "concept-id": "C1-PROV1",
      "provider-id": "PROV1",
      "metadata": "xml here",
      "format": "echo10",
      "revision-id": 1, //(optional field)
      "extra-fields": {
        "short-name": "short",
        "version-id": "V01",
        "entry-title": "Dataset V01"
      }
    }

#### Granule

    {
      "concept-type": "granule",
      "native-id": "provider granule id",
      "concept-id": "G1-PROV1",
      "provider-id": "PROV1",
      "metadata": "xml here",
      "format": "echo10",
      "revision-id": 1, //(optional field)
      "extra-fields": {
        "parent-collection-id": "C5-PROV1"
      }
    }

### Sample Tombstone (deleted concept) JSON
  	{
  		"concept-type": "collection"
  		"native-id": "provider collection id"
     	"concept-id": "C1-PROV1"
     	"provider-id": "PROV1"
     	"deleted": true
     	"revision-id": 10
      "extra-fields": {
        "short-name": "short",
        "version-id": "V01",
        "entry-title": "Dataset V01"
      }
     }


### Setting up the database
1. Create the user by executing the create_user.clj file from the project
directory

```
    lein exec ./support/create_user.clj
```

2. Run the migration scripts

```
  lein migrate
```

You can use `lein migrate -version version` to restore the database to
a given version. `lein migrate -version 0` will clean the datbase
completely.

You can remove the user by executing `lein exec ./support/drop_user.clj`.

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


## API


### GET /concept-id/:concept-type/:provider-id/:native-id
TODO consider changing this to use query params instead of URL vars
returns: new or existing concept-id

__Example Curl:__
curl -v http://localhost:3001/concept-id/collection/PROV1/native-id

### POST /concepts
params: [concept] - revision-id optionally in concept
returns: revision-id.  revision-id begins at 0.
throws error if revision-id does not match what it will be when saved

__Example Curl:__
curl -v -XPOST -H "Content-Type: application/json" -d '{"concept-type": "collection", "native-id": "native-id", "concept-id": "C1-PROV1", "provider-id": "PROV1", "metadata": "<Collection><ShortName>MINIMAL</ShortName></Collection>", "format": "echo10", "extra-fields": {"short-name": "MINIMAL", "version-id": "V01", "entry-title": "native-id"}}' http://localhost:3001/concepts/

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

### POST /concepts/search/concept-revisions
params: [[concept-id/revision-id tuple] ...]
returns: list of concepts matching the touples provided in the body of the POST

__Example Curl:__
curl -v -XPOST -H "Content-Type: application/json" -d '[["C1-PROV1", 1], ["C2-PROV1", 1]]' http://localhost:3001/concepts/search/concept-revisions

### GET /concepts/search/:concept-types?param1=value&...

Supported combinations of concept type and parameters:
  * colllections, provider-id, short-name, version-id
  * colllections, provider-id, entry-title

__Example Curl:__
curl "http://localhost:3001/concepts/search/collections?provider-id=PROV1&short-name=s&version-id=1"
curl "http://localhost:3001/concepts/search/collections?provider-id=PROV1&entry-title=et"


### DELETE /concepts/#concept-id/#revision-id
params: none
returns: the revision id of the tombstone generated for the concept

__Example Curl:__
curl -v -XDELETE localhost:3001/concepts/C1-PROV1/1

### DELETE /concepts/#concept-id
params: none
returns: the revision id of the tombstone generated for the concept

__Example Curl:__
curl -v -XDELETE localhost:3001/concepts/C1-PROV1

### DELETE /concepts/force-delete/:concept-id/:revision-id
params: none
returns: nothing (status 204)

__Example Curl:__
curl -v -XDELETE /concepts/force-delete/C1-PROV1/1

### POST /reset
params: none
returns: nothing (status 204)

__Example Curl:__
curl -v -XPOST localhost:3001/reset

## Providers API

### POST /providers
params: [provider]
returns: provider-id

__Example Curl:__
curl -v -XPOST -H "Content-Type: application/json" -d '{"provider-id": "PROV1"}' http://localhost:3001/providers

### DELETE /providers/#provider-id
params: none
returns: nothing (status 204)

__Example Curl:__
curl -v XDELETE http://localhost:3001/providers/PROV1

### GET "/providers"
params: none
returns: list of provider-ids

__Example Curl:__
curl http://localhost:3001/providers

 ;; create a new provider
      (POST "/" {:keys [request-context body]}
        (save-provider request-context (get body "provider-id")))
      ;; delete a provider
      (DELETE "/:provider-id" {{:keys [provider-id]} :params request-context :request-context}
        (delete-provider request-context provider-id))
      ;; get a list of providers
      (GET "/" {request-context :request-context}
        (get-providers request-context)))



Different ways to retrieve concepts
1 - by concept-id and revision-id
2 - by concept-id (latest revision)
3 - multiple by concept-ids and revision-ids

## License

Copyright © 2014 NASA
