# Usage


**Contents**

* REST API
* JVM Library
* Client Library


## REST API

The CMR Graph REST API is accessible via the endpoint
[http://localhost:3012](http://localhost:3012), following the port numbering
conventions of the CMR.

For this endpoint, the following resources have been defined:

Collection Routes

* `GET    /collections`
* `DELETE /collections`
* `POST   /collections/import`
* `POST   /collection`
* `GET    /collection/:concept-id`
* `DELETE /collection/:concept-id`
* `UPDATE /collection/:concept-id`

Relationship Routes

* `GET    /relationships/related-urls/collections/:concept-id`

Queries

* `GET    /queries/cypher`
* `POST   /queries/cypher`

Admin Routes

* `GET    /health`
* `GET    /ping`
* `POST   /ping`
* `DELETE /reset`
* `POST   /reload`

Demo Routes

* `GET    /demo/movie/graph/:limit`
* `GET    /demo/movie/search?q=`
* `GET    /demo/movie/title/:title`

Static Routes

* `GET    /static/*`

Examples:

```
$ curl --silent "http://localhost:3012/health"|jq .
```
```json
{
  "config": {
    "ok?": true
  },
  "httpd": {
    "ok?": true
  },
  "elastic": {
    "ok?": true
  },
  "logging": {
    "ok?": true
  },
  "neo4j": {
    "ok?": true
  }
}
```
```
$ curl --silent "http://localhost:3012/demo/movie/search?q=Matrix"|jq .
```
```json
[
  {
    "movie": {
      "tagline": "Welcome to the Real World",
      "title": "The Matrix",
      "released": 1999
    }
  },
  {
    "movie": {
      "tagline": "Free your mind",
      "title": "The Matrix Reloaded",
      "released": 2003
    }
  },
  {
    "movie": {
      "tagline": "Everything that has a beginning has an end",
      "title": "The Matrix Revolutions",
      "released": 2003
    }
  }
]
```


## JVM Library

TBD


## Client Library

TBD
