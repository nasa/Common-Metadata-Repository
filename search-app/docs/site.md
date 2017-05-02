## Site Routes &amp; Web Resources Documentation

See also the [API Documentation](api.html).

### Table of Contents

  * [Routes](#routes)
  * [Static Assets and Content](#static-assets-and-content)
  * [Redirects](#redirects)

### <a name="routes"></a> Routes

The CMR Search Site defines the following application routes, relative to the base CMR Search URL. These resources at the URLs are generated dynamically using page templates (cached).

| Path                                                      | Description                                                         |
| --------------------------------------------------------- | ------------------------------------------------------------------- |
| /                                                         | The CMR Search "home" page                                          |
| /sitemap.xml                                              | The master CMR Search sitemap index (submit this to search engines) |
| /site/sitemap.xml                                         | The sitemap for the directory pages                                 |
| /site/collections/directory                               | A listing of supported collection directories                       |
| /site/collections/directory/eosdis                        | A listing of EOSDIS collection directories                          |
| /site/collections/directory/:provider-id/:tag             | A listing of collection landing pages by provider and tag           |
| /site/collections/directory/:provider-id/:tag/sitemap.xml | Sitemaps for collection landing pages by provider and tag           |

Note that in production, the base CMR Search URL is `/search`, while in development it is `/`.

### <a name="static-assets-and-content"></a> Static Assets and Content

The CMR Search Site defines the following static resources. As above, the URLs listed are relative to the base CMR Search URL.

| Path                   | Description                                         |
| ---------------------- | --------------------------------------------------- |
| /site/docs/search      | Documentation index (links)                         |
| /site/docs/search/api  | The API documentation                               |
| /site/docs/search/site | The documentation for site routes and web resources |

Additionally, static assets are made available at the site root, serving CSS and JavaScript files.

### <a name="redirects"></a> Redirects

The following redirects are defined in order to assist with a better organized documentation URL structure.

| Path                        | Destination                 | HTTP Status Code |
| --------------------------- | --------------------------- |------------------|
| /site/search_api_docs.html  | /site/docs/search/api.html  | `301`            |
| /site/search_site_docs.html | /site/docs/search/site.html | `301`            |
| /site/docs/search/api       | /site/docs/search/api.html  | `307`            |
| /site/docs/search/site      | /site/docs/search/site.html | `307`            |

The permanent redirects have been added as means of providing backwards compatibility for users who have bookmarked the old URLs. The temporary redirects are provided in order to future-proof docs URL organization work. When that work is complete, the redirect locations will be updated and status codes will be set to permanent.
