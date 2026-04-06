### <a name="general-request-details"></a> General Request Details

#### <a name="maximum-url-length"></a> Maximum URL Length

The Maximum URL Length supported by CMR is indirectly controlled by the Request Header Size setting in Jetty which is set to 1MB. This translates to roughly 500k characters, however it is recommended that any GET request be limited to 6,000 characters, and in a web browser the recommended length is no longer than 2000 characters. Clients using the Search API with query parameters should be careful not to exceed this limit or they will get an HTTP response of 413 FULL HEAD. If a client expects that the query url could be extra long so that it exceeds 6k characters, they should use the POST API for searching.

#### <a name="cors-header-support"></a> CORS Header support

The CORS headers are supported on search endpoints. Check [CORS Documentation](https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS) for an explanation of CORS headers. Custom CORS request headers supported are Authorization, Client-Id, CMR-Request-Id, X-Request-Id, CMR-Scroll-Id (**deprecated**) and CMR-Search-After. Custom response headers supported are CMR-Hits, CMR-Request-Id, X-Request-Id, CMR-Scroll-Id (**deprecated**), CMR-Search-After, CMR-Timed-Out, CMR-Shapefile-Original-Point-Count and CMR-Shapefile-Simplified-Point-Count.

#### <a name="query-parameters"></a> Query Parameters

 * `page_size` - Number of results per page - default is 10, max is 2000.
 * `page_num` - The page number to return. (**deprecated**)
 * `offset` - As an alternative to page_num, a 0-based offset of individual results may be specified. (**deprecated**)
 * `scroll` - A boolean flag (true/false) that allows all results to be retrieved somewhat efficiently. `page_size` is supported with `scroll` while `page_num` and `offset` are not. If `scroll` is `true` then the first call of a scroll session sets the page size; `page_size` is ignored on subsequent calls. (**deprecated**)
 * `sort_key` - Indicates one or more fields to sort on. Described below.
 * `pretty` - Return formatted results if set to true.
 * `token` - Specifies a user token from EDL or Launchpad for use as authentication. Using the standard [Authorization header](#headers) is the prefered way to supply a token. This parameter may be deprecated in the future.
 * `echo_compatible` - When set to true results will be returned in an ECHO compatible format. This mostly removes fields and features specific to the CMR such as revision id, granule counts and facets in collection results. Metadata format style results will also use ECHO style names for concept ids such as `echo_granule_id` and `echo_dataset_id`.

#### <a name="paging-details"></a> Paging Details

__NOTE:__ Deep paging is being deprecated in favor of [Search After](#search-after). Please switch your scroll based queries to [Search After](#search-after) which is more efficient and easier to use.

The CMR contains many more results than can be returned in a single response so the number of results that can be returned is limited. The parameters `page_num`, `offset`, and `page_size` along with the sort specified by `sort_key` control which items will be returned. The query parameter `page_size`, defaulting to 10, controls the amount of items that will be returned in a response. One of `page_num` or `offset` can be provided to index into the search results.

`page_num`, defaulting to 1, chooses a "page" of items to return. If a search matched 50 items the parameters `page_num=3&page_size=5` would return the 11th item through the 15th item.

`offset` is a 0 based index into the result set of a query. If a search matched 50 items the parameters `offset=3&page_size=5` would return 4th result through the 8th result.

__Note__: In the event which an ingest or delete occurs between paging requests, the order of your results may change, causing inconsistent results across pages.

You can not page past the 1 millionth item. Please contact the CMR Team at cmr-support@nasa.gov if you need to retrieve items in excess of 1 million from the CMR. Additionally granule queries which do not target a set of collections are limited to paging up to the 10,000th item.

#### <a name="search-after"></a> Search After

__NOTE__: This is currently the prefered way to request large sets of records.

Search After supersedes scrolling and is the replacement for the deprecated CMR-Scroll-Id functionality. Search After allows the retrieval of all results of a query in a stateless manner and is the recommended way for deep paging by Elasticsearch. It is supported through the `CMR-Search-After` header. Search After is primarily intended to support harvesting of metadata.

Search After is only supported for parameter queries and JSON queries. All query parameters are available with the exception of the `page_num` and `offset` parameters.

Search After is stateless, it is always resolved against the latest version of the data. Any search against CMR that has results not fully returned in the current request will return a `search-after` value in the `CMR-Search-After` header of the search response. A user can then pass this returned value in the `CMR-Search-After` header of the following request to retrieve the next page of result based on the specified page_size. Each search request will result in a new `search-after` value returned in the `CMR-Search-After` response header. Supplying the new `search-after` value in the following request's `CMR-Search-After` header will retrieve the next page. Similar to regular paging requests in CMR, if an ingest or delete occurs between these `search-after` paging requests, the order of your results may change, causing inconsistent results across pages.

The `CMR-Hits` header is useful for determining the number of requests that will be needed to retrieve all the available results.

When all the results have been returned, the subsequent search will return an empty result set and no `CMR-Search-After` header in the response.

Different from scrolling requests, each search-after request needs to supply all the search parameters, and the `CMR-Search-After` header needs to be updated with the new value returned from the previous search to page through the whole result set. Although user can change the search parameters and still get results back as long as the sort order of the search is unchanged, it breaks the rationale of paging and offers no real use case. Thus user should always supply the same search parameters while using Search After requests to page through a large result set.

__Example__

```
curl -i "%CMR-ENDPOINT%/granules?concept_id=C1-PROV1&page_size=200"
```
returns 200 granule references and the following info in the response header:
```
CMR-Hits: 408
CMR-Search-After: ["aaa", 123, 456]
```
This tells us that there are total 408 granules matching our search and we can use the `CMR-Search-After: ["aaa", 123, 456]` header to get the next page of the result set. To do that, we run:
```
curl -i -H 'CMR-Search-After: ["aaa", 123, 456]' "%CMR-ENDPOINT%/granules?concept_id=C1-PROV1&page_size=200"
```
this returns the next 200 granules in the result set and the following info in the response header:
```
CMR-Hits: 408
CMR-Search-After: ["xyz", 789, 999]
```
We can then use the new `CMR-Search-After: ["xyz", 789, 999]` header to get the next page of the result set.
```
curl -i -H 'CMR-Search-After: ["xyz", 789, 999]' "%CMR-ENDPOINT%/granules?concept_id=%CMR-EXAMPLE-COLLECTION-ID%&page_size=200"
```
There will be only 8 granules in the result set. We can deem the search has reached the end because the number of results returned is less than the page_size, but if we search again with the new `CMR-Search-After` header value returned, we will get an empty result set and there won't be a `CMR-Search-After` header in the response.

#### <a name="scrolling-details"></a> Scrolling Details

__IMPORTANT:__ Scrolling is deprecated and will be disabled in the future. When disabled, requests using CMR-Scroll-Id or scroll parameter will return HTTP 400 errors with instructions to migrate to [Search After](#search-after). Please migrate your applications to use the Search After pattern instead, which is more efficient and easier to use.

Scrolling allows the retrieval of all results of a query in an efficient manner. This parameter is primarily intended to support harvesting of metadata. Scrolling is only supported for parameter queries, but all query parameters are available with the exception of the `page_num` and `offset` parameters. The response format for scrolling queries is identical to the response for normal parameter queries with the exception of the addition of the `CMR-Scroll-Id` header. The `CMR-Hits` header is useful for determining the number of requests that will be needed to retrieve all the available results.

Scrolling is *session based*; the first search conducted with the `scroll` parameter set to `true` or `defer` will return a session id in the form of a `CMR-Scroll-Id` header. This header should be included in subsequent searches until the desired number of results have been retrieved. Sessions time out after 10 minutes of inactivity; each new query before the timeout is reached with a given `CMR-Scroll-Id` header will reset the timeout to 10 minutes. Queries occurring after a session has timed out will result in an HTTP 404 status code and error message.

Setting the `scroll` parameter to `defer` will not return any search results with the initial response. The second request passing in the `CMR-Scroll-Id` header will return the first page of results with subsequent requests returning subsequent pages. This is useful for staging a scrolling session and getting the total number of hits before beginning the process of retrieving results. `defer` works with `HEAD`, `GET`, and `POST` requests.

When all the results have been returned subsequent calls using the same `CMR-Scroll-Id` header will return an empty list.

Important note: Clients using scrolling (especially via programmatic API or scripts) should explicitly invoke [`clear scroll session`] (#clear-scroll) to release the scroll session when they are finished. This will end the scroll session and free up system resources.

#### <a name="parameter-options"></a> Parameter Options

The behavior of search with respect to each parameter can be modified using the `options` parameter. The `options` parameter takes the following form:

  `options[parameter][option_key]=value`

where parameter is the URL parameter whose behavior is to be affected, value is either `true` or `false`, and `option_key` is one of the following:

 * `ignore_case` - if set to true, the search will be case insensitive and if set to false, the search will be case sensitive. Defaults to true.
 * `pattern` - if set to true, the search will treat the value provided for the parameter as a pattern with wild-cards, in which '\*' matches zero or more characters and '?' matches any single character. For example, `platform[]=AB?D*&options[platform][pattern]=true` would match 'ABAD123', 'ABCD12', 'ABeD', etc. Defaults to false. **NOTE:** Patterns with leading wildcards, like "\*ODIS", are computationally expensive. The CMR limits the maximum number of patterns with a leading wildcard per query to 5.
 * `and` - if set to true and if multiple values are listed for the param, the concepts must have ALL of these values in order to match. The default is `false` which means concepts with ANY of the values match. This option only applies to fields which may be multi-valued; these are documented here.
 * `or` - this option only applies to granule attributes or science-keywords searches. If set to true, the collection/granule will match when any of the grouped search condition is matched. The default is false.

##### <a name="collection-result-features"></a> Collection Result Feature Parameters

These are query parameters that control what extra data is included with collection search results. They do not impact which collections are matched but can add additional data to the search results like facets, granule counts, and tags.

  * `include_has_granules` - If this parameter is set to "true" this will include a flag indicating true or false if the collection has any granules at all. Supported in all response formats except opendata. To limit search results to collections with or without granules, see the [`has_granules`](#c-has-granules) parameter.
  * `include_granule_counts` - If this parameter is set to "true" this will include a count of the granules in each collection that would match the spatial and temporal conditions from the collection query. Supported in all response formats except opendata and kml.
  * `include_facets` There are 3 values allowed: "true", "false", or "v2". If this parameter is set to "true" or "v2" facets will be included in the collection results (not applicable to opendata results). Facets are described in detail below.
  * `facets_size[field-name]` is used to customize the max number of values displayed for the facet. Values allowed: positive integer. Supported field-name are: science-keywords, platforms, instrument, data-center, project, processing-level-id and variables.
  * `hierarchical_facets` - If this parameter is set to "true" and the parameter `include_facets` is set to "true" the facets that are returned will be hierarchical. Hierarchical facets are described in the facets section below.
  * `include_highlights` - If this parameter is set to "true", the collection results will contain an additional field, 'highlighted_summary_snippets'. The field is an array of strings which contain a snippet of the summary which highlight any terms which match the terms provided in the keyword portion of a search. By default up to 5 snippets may be returned with each individual snippet being up to 100 characters, and keywords in the snippets are delineated with begin tag `<em>` and end tag `</em>`. This is configurable using `options[highlights][param]=value`. Supported option params are `begin_tag`, `end_tag`, `snippet_length` and `num_snippets`. The values for `snippet_length` and `num_snippets` must be integers greater than 0.
  * `include_tags` - If this parameter is set (e.g. `include_tags=gov.nasa.earthdata.search.*,gov.nasa.echo.*`), the collection results will contain an additional field 'tags' within each collection. The value of the tags field is a list of tag_keys that are associated with the collection. Only the tags with tag_key matching the values of `include_tags` parameter (with wildcard support) are included in the results. This parameter is supported in JSON, ATOM, ECHO10, DIF, DIF10, ISO19115 and native result formats.
  * `spatial` - This option allows the user to specify whether spatial conditions are ANDed or ORed together. This will apply across different types of spatial conditions.

  _There is a known bug with the `snippet_length` parameter that occasionally leads to snippets that are longer than `snippet_length` characters._

  _The `include_highlights` feature is only supported for the JSON response format and only applies to keyword searches._


#### <a name="headers"></a> HTTP Headers

##### Request Headers
`Accept` - specifies the MimeType to return search results in. Default is "application/xml".

    `curl -H "Accept: application/xml" -i "%CMR-ENDPOINT%/collections"`

`Authorization: Bearer` - specifies an EDL bearer token to use to authenticate yourself.

    `curl -H "Authorization: Bearer <access_token>" -i "%CMR-ENDPOINT%/collections"`

`Authorization:` - specifies a Launchpad token to use to authenticate yourself.

    `curl -H "Authorization: <launchpad_token>" -i "%CMR-ENDPOINT%/collections"`

`Client-Id` - Indicates a name for the "client" using the CMR API. A client can be either a person's name, organization's name, or the name of the script/process making the request. Specifying this header helps Operations monitor query performance per client. This field can also make it easier for the Operations team to identify your requests if you contact them for assistance.

`CMR-Pretty` - Returns formatted results if set to true.

`X-Request-Id` - This provides standard `X-Request-Id` support to allow user to pass in some random ID which will be logged on the server side for debugging purposes.

`CMR-Request-Id` - This header serves the same purpose as `X-Request-Id` header. It is kept to support legacy systems.

##### The response headers

* `CMR-Hits` and `CMR-Took` indicate the number of result hits and the time to build and execute the query, respectively.
* `CMR-Request-Id` and `X-Request-Id` return the same value - the value passed in through `CMR-Request-Id` request header or `X-Request-Id` request header or a unique id generated for the client request when no value is passed in, This can be used to help debug client errors.
* `CMR-Shapefile-Original-Point-Count` returns the original shapefile point count when shapefile simplification is requested
* `CMR-Shapefile-Simplified-Point-Count` return the reduced point count when shapefile simplification is requested
* `content-md5` returns the MD5 hash of the content.
* `content-sha1` returns the SHA1 hash value for the content.

#### <a name="extensions"></a> Extensions

Besides MimeTypes, client can also use extensions to specify the format for search results. Default is xml.

  * `curl -i "%CMR-ENDPOINT%/collections"`
  * `curl -i "%CMR-ENDPOINT%/collections.json"`
  * `curl -i "%CMR-ENDPOINT%/collections.echo10"`
  * `curl -i "%CMR-ENDPOINT%/collections.iso19115"`

Here is a list of supported extensions and their corresponding MimeTypes:

  * `html`      "text/html"
  * `json`      "application/json"
  * `xml`       "application/xml"
  * `echo10`    "application/echo10+xml"
  * `iso`       "application/iso19115+xml"
  * `iso19115`  "application/iso19115+xml"
  * `dif`       "application/dif+xml"
  * `dif10`     "application/dif10+xml"
  * `csv`       "text/csv"
  * `atom`      "application/atom+xml"
  * `opendata`  "application/opendata+json" (only supported for collections)
  * `kml`       "application/vnd.google-earth.kml+xml"
  * `stac`      "application/json; profile=stac-catalogue"
  * `native`    "application/metadata+xml" (Returns search results in their individual native formats)
  * `umm-json`   "application/vnd.nasa.cmr.legacy_umm_results+json" (only supported for collections)
    * **Caution**: The UMM JSON format was originally used for an alpha version of UMM JSON search results. Currently it still returns data in that style to avoid breaking clients dependent on it. This will be changed in a future version to return the latest version of the UMM.
  * `umm_json`   "application/vnd.nasa.cmr.umm_results+json" (supported for collections, granules, variables and services)
    * The UMM JSON extension returns concepts in the latest version of the UMM.
  * `umm_json_vN_N_N` "application/vnd.nasa.cmr.umm_results+json; version=N.N.N (where `_N` can be repeat as many times as is necessary)."

  __NOTE__: As of version "%CMR-RELEASE-VERSION%", the latest UMM Collection version is `%COLLECTION-UMM-VERSION%`.

#### <a name="request-timeouts"></a> Request Timeouts

The CMR operating environment imposes a hard limit of 180 seconds on any request, after which a 504 error is
returned. To avoid this, the CMR has an internal query timeout of 170 seconds - any query taking longer will time
out and a subset of the total hit results will be returned instead of an error. The response for queries that time
out will include the `CMR-Time-Out` header set to `true`.

#### <a name="request-moderation"></a> Moderating Client Request Traffic

In order to provide robust availability and performance for all clients of the service, CMR Search deploys a set of rate throttling rules for request traffic. These rules are defined to target specific request signatures, throttling the allowed rate of these searches in an effort to prevent overall degradation of system performance and availability. If a client request should exceed one of these rate throttling rules, the request will be rejected and a `429` error status returned to the client along with a `retry-after` header value. The suggested practice for any CMR Search client is to honor the `retry-after` header value and delay accordingly before re-issuing the failed request and continuing with its CMR Search processing.