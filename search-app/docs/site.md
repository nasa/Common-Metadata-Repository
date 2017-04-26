## Site Routes &amp; Web Resources Documentation

See also the [API Documentation](search_api_docs.html).

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

| Path            | Description                                         |
| --------------- | --------------------------------------------------- |
| /site/docs      | Documentation links                                 |
| /site/docs/api  | The API documentation                               |
| /site/docs/site | The documentation for site routes and web resources |

Additionally, static assets are made available at the site root, serving CSS and JavaScript files.

### <a name="redirects"></a> Redirects

The following redirects are defined in order to assist with a better organized documentation URL structure.

| Path                       | Destination         |
| -------------------------- | ------------------- |
| site/search_api_docs.html  | site/docs/api.html  |
| site/search_site_docs.html | site/docs/site.html |
| site/docs/api              | site/docs/api.html  |
| site/docs/site             | site/docs/site.html |

Each of these are provided as means of providing backwards compatibility for users who have bookmarked the old URLs. They return an HTTP response status code of `301`.