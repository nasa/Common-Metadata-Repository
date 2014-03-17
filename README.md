# cmr-metadata-db-app

## Web API

### get-concept-id
params: [concept-type provider-id native-id]
returns: new or existing concept-id

### save-concept
params: [concept] - revision-id optionally in concept
returns: revision-id.  revision-id begins at 0.
throws error if revision-id does not match what it will be when saved

### get-concept
params: [concept-id <revision-id>]
returns: concept

### get-concepts
params: [[concept-id/revision-id tuple]]
returns: list of concepts 

Different ways to retrieve concepts
1 - by concept-id and revision-id
2 - by concept-id (latest revision)
3 - multiple by concept-ids and revision-ids

## License

Copyright © 2014 NASA
