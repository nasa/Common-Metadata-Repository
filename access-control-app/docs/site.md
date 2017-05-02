## Site Routes &amp; Web Resources Documentation

See also the [API Documentation](api.html).

### Table of Contents

  * [Routes](#routes)
  * [Static Assets and Content](#static-assets-and-content)
  * [Redirects](#redirects)

### <a name="routes"></a> Routes

The CMR Access Control site defines the following application routes, relative to the base CMR Access Control URL. These resources at the URLs are generated dynamically using page templates (cached).

| Path   | Description                                                         |
| ------ | ------------------------------------------------------------------- |
| /      | The CMR Access Control "home" page                                  |

Note that in production, the base CMR Access Control URL is `/access-control`, while in development it is `/`.

### <a name="static-assets-and-content"></a> Static Assets and Content

The CMR Access Control site defines the following static resources. As above, the URLs listed are relative to the base CMR Access Control URL.

| Path                             | Description                                         |
| -------------------------------- | --------------------------------------------------- |
| /site/docs/access-control        | Documentation index (links)                         |
| /site/docs/access-control/api    | Access Control API documentation                    |
| /site/docs/access-control/usage  | Access Control usage documentation                  |
| /site/docs/access-control/schema | Access Control schema documentation                 |
| /site/docs/access-control/site   | The documentation for site routes and web resources |

Additionally, static assets are made available at the site root, serving CSS and JavaScript files.

### <a name="redirects"></a> Redirects

The following redirects are defined in order to assist with a better organized documentation URL structure.

| Path                               | Destination                         | HTTP Status Code |
| ---------------------------------- | ----------------------------------- | ---------------- |
| /site/access_control_api_docs.html | /site/docs/access-control/api.html  | `301`            |
| /site/docs/access-control/api      | /site/docs/access-control/api.html  | `307`            |
| /site/docs/access-control/site     | /site/docs/access-control/site.html | `307`            |

The permanent redirect has been added as means of providing backwards compatibility for users who have bookmarked the old URL. The temporary redirects are provided in order to future-proof docs URL organization work. When that work is complete, the redirect locations will be updated and status codes will be set to permanent.
