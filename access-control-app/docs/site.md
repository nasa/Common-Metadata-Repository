## Site Routes &amp; Web Resources Documentation

See also the [API Documentation](api.html).

### Table of Contents

  * [Routes](#routes)
  * [Static Assets and Content](#static-assets-and-content)
  * [Redirects](#redirects)

### <a name="routes"></a> Routes

The CMR Search Site defines the following application routes, relative to the base CMR Search URL. These resources at the URLs are generated dynamically using page templates (cached).

| Path   | Description                                                         |
| ------ | ------------------------------------------------------------------- |
| /      | The CMR Access Control "home" page                                  |

Note that in production, the base CMR Search URL is `/search`, while in development it is `/`.

### <a name="static-assets-and-content"></a> Static Assets and Content

The CMR Search Site defines the following static resources. As above, the URLs listed are relative to the base CMR Search URL.

| Path                   | Description                                         |
| ---------------------- | --------------------------------------------------- |
| /site/docs             | Documentation links                                 |
| /site/docs/api         | Access Control API documentation                    |
| /site/docs/acl-usage   | Access control usage documentation                  |
| /site/docs/acl-schema  | Access control schema documentation                 |
| /site/docs/site        | The documentation for site routes and web resources |

Additionally, static assets are made available at the site root, serving CSS and JavaScript files.

### <a name="redirects"></a> Redirects

The following redirects are defined in order to assist with a better organized documentation URL structure.

| Path                               | Destination         | HTTP Status Code |
| ---------------------------------- | ------------------- | ---------------- |
| site/access_control_api_docs.html  | site/docs/api.html  | `301`            |
| site/docs/api                      | site/docs/api.html  | `307`            |
| site/docs/site                     | site/docs/site.html | `307`            |

Each of these are provided as means of providing backwards compatibility for users who have bookmarked the old URLs.
