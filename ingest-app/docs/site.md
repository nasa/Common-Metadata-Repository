## Site Routes &amp; Web Resources Documentation

See also the [API Documentation](api.html).

### Table of Contents

  * [Routes](#routes)
  * [Static Assets and Content](#static-assets-and-content)
  * [Redirects](#redirects)

### <a name="routes"></a> Routes

The CMR Ingest Site defines the following application routes, relative to the base CMR Ingest URL. These resources at the URLs are generated dynamically using page templates (cached).

| Path  | Description                |
| ----- | -------------------------- |
| /     | The CMR Ingest "home" page |

Note that in production, the base CMR Ingest URL is `/ingest`, while in development it is `/`.

### <a name="static-assets-and-content"></a> Static Assets and Content

The CMR Ingest Site defines the following static resources. As above, the URLs listed are relative to the base CMR Ingest URL.

| Path                   | Description                                         |
| ---------------------- | --------------------------------------------------- |
| /site/docs/ingest      | Documentation links                                 |
| /site/docs/ingest/api  | The API documentation                               |
| /site/docs/ingest/site | The documentation for site routes and web resources |

Additionally, static assets are made available at the site root, serving CSS and JavaScript files.

### <a name="redirects"></a> Redirects

The following redirects are defined in order to assist with a better organized documentation URL structure.

| Path                        | Destination                 | HTTP Status Code |
| --------------------------- | --------------------------- |------------------|
| /site/ingest_api_docs.html  | /site/docs/ingest/api.html  | `301`            |
| /site/docs/ingest/api       | /site/docs/ingest/api.html  | `307`            |
| /site/docs/ingest/site      | /site/docs/ingest/site.html | `307`            |

The permanent redirects have been added as means of providing backwards compatibility for users who have bookmarked the old URLs. The temporary redirects are provided in order to future-proof docs URL organization work. When that work is complete, the redirect locations will be updated status codes will be set to permanent.
