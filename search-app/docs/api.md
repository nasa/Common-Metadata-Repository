## API Documentation

See the [CMR Client Partner User Guide](https://wiki.earthdata.nasa.gov/display/CMR/CMR+Client+Partner+User+Guide) for a general guide to developing a client utilizing the CMR Search API.

### Table of Contents

  * [General Request Details](#general-request-details)
    * [Maximum URL Length](#maximum-url-length)
    * [CORS Header support](#cors-header-support)
    * [Query Parameters](#query-parameters)
    * [Paging Details](#paging-details)
    * [Search After](#search-after)
    * [Scrolling Details](#scrolling-details)
    * [Parameter Options](#parameter-options)
    * [Collection Result Feature Parameters](#collection-result-features)
    * [Headers](#headers)
    * [Extensions](#extensions)
    * [Request Timeouts](#request-timeouts)
    * [Moderating Client Request Traffic](#request-moderation)
  * [Supported Result Formats](#supported-result-formats)
    * [ATOM](#atom)
    * [CSV](#csv)
    * [DIF-9](#dif-9)
    * [DIF-10](#dif-10)
    * [ECHO 10](#echo-10)
    * [ISO-MENDS](#iso-mends)
    * [ISO-SMAP](#iso-smap)
    * [JSON](#json)
    * [UMM JSON](#umm-json)
    * [KML](#kml)
    * [Open Data](#open-data)
    * [STAC](#stac)
    * [XML Reference](#xml-reference)
  * [Temporal Range Searches](#temporal-range-searches)
  * [Facet Autocompletion](#autocomplete-facets)
  * [Collection Search By Parameters](#collection-search-by-parameters)
    * [Find all collections](#find-all-collections)
    * [Concept id](#c-concept-id)
    * [DOI value](#c-doi-value)
    * [Echo collection id](#c-echo-collection-id)
    * [Provider short name](#c-provider-short-name)
    * [Entry title](#c-entry-title)
    * [Entry id](#c-entry-id)
    * [Archive center](#c-archive-center)
    * [Data center](#c-data-center)
    * [Temporal](#c-temporal)
    * [Project](#c-project)
    * [Consortium](#c-consortium)
    * [Updated since](#c-updated-since)
    * [Revision date](#c-revision-date)
    * [Created at](#c-created-at)
    * [Collections with new Granules](#c-with-new-granules)
    * [Collections with revised Granules](#c-with-revised-granules)
    * [Processing level id](#c-processing-level-id)
    * [Platform](#c-platform)
    * [Instrument](#c-instrument)
    * [Sensor](#c-sensor)
    * [Spatial keyword](#c-spatial-keyword)
    * [Science keywords](#c-science-keywords)
    * [TwoD coordinate system](#c-twod-coordinate-system)
    * [Collection data type](#c-collection-data-type)
    * [Granule data format](#c-granule-data-format)
    * [Online only](#c-online-only)
    * [Downloadable](#c-downloadable)
    * [Browse only](#c-browse-only)
    * [Browsable](#c-browsable)
    * [Keyword (free text)](#c-keyword)
    * [Provider](#c-provider)
    * [Native Id](#c-native-id)
    * [Short name](#c-short-name)
    * [Version](#c-version)
    * [Tag parameters](#c-tag-parameters)
    * [Variable parameters](#c-variable-parameters)
    * [Variables](#c-variables)
    * [Service parameters](#c-service-parameters)
    * [Tool parameters](#c-tool-parameters)
    * [Spatial](#c-spatial)
        * [Polygon](#c-polygon)
        * [Bounding Box](#c-bounding-box)
        * [Point](#c-point)
        * [Line](#c-line)
        * [Circle](#c-circle)
    * [Shapefile](#c-shapefile)
    * [Shapefile Simplification](#c-shapefile-simplification)
    * [Additional Attribute](#c-additional-attribute)
    * [Author](#c-author)
    * [With/without granules](#c-has-granules)
    * [With/without granules Or Cwic](#c-has-granules-or-cwic)
    * [With/without granules Or OpenSearch](#c-has-granules-or-opensearch)
    * [OPeNDAP service URL](#c-has-opendap-url)
    * [Cloud Hosted](#c-cloud-hosted)
    * [Standard Product](#c-standard-product)
  * [Sorting Collection Results](#sorting-collection-results)
  * [Retrieving all Revisions of a Collection](#retrieving-all-revisions-of-a-collection)
  * [Granule Search By Parameters](#granule-search-by-parameters)
    * [Find all granules](#find-all-granules)
    * [Granule UR](#g-granule-ur)
    * [Producer granule id](#g-producer-granule-id)
    * [Granule UR or producer granule id](#g-granule-ur-or-producer-granule-id)
    * [Online only](#g-online-only)
    * [Downloadable](#g-downloadable)
    * [Browsable](#g-browsable)
    * [Additional attribute](#g-additional-attribute)
    * [Spatial](#g-spatial)
        * [Polygon](#g-polygon)
        * [Bounding Box](#g-bounding-box)
        * [Point](#g-point)
        * [Line](#g-line)
        * [Circle](#g-circle)
    * [Shapefile](#g-shapefile)
    * [Shapefile Simplification](#g-shapefile-simplification)
    * [Orbit number](#g-orbit-number)
    * [Orbit equator crossing longitude](#g-orbit-equator-crossing-longitude)
    * [Orbit equator crossing date](#g-orbit-equator-crossing-date)
    * [Updated since](#g-updated-since)
    * [Revision date](#g-revision-date)
    * [Created at](#g-created-at)
    * [Production date](#g-production-date)
    * [Cloud cover](#g-cloud-cover)
    * [Platform](#g-platform)
    * [Instrument](#g-instrument)
    * [Sensor](#g-sensor)
    * [Project](#g-project)
    * [Concept id](#g-concept-id)
    * [Day/night flag](#g-day-night-flag)
    * [TwoD coordinate system](#g-twod-coordinate-system)
    * [Provider](#g-provider)
    * [Native Id](#g-native-id)
    * [Short name](#g-short-name)
    * [Version (collection)](#g-version)
    * [Entry Title (collection)](#g-entry-title)
    * [Entry Id (collection)](#g-entry-id)
    * [Temporal](#g-temporal)
    * [Cycle](#g-cycle)
    * [Passes](#g-passes)
    * [Exclude by id](#g-exclude-by-id)
  * [Sorting granule results](#sorting-granule-results)
  * [Retrieving concepts by concept-id and revision-id](#retrieving-concepts-by-concept-id-and-revision-id)
  * [Search with POST](#search-with-post)
  * [Search Response as Granule Timeline](#search-response-as-granule-timeline)
  * [Retrieve Provider Holdings](#retrieve-provider-holdings)
  * [Search with JSON Query](#search-with-json-query)
  * [Search with AQL](#search-with-aql)
  * [Document Scoring](#document-scoring)
  * [Facets](#facets)
    * [Version 2 Facets Response Format](#facets-v2-response-format)
    * [Collection Facets in XML Responses](#facets-in-xml-responses)
        * [Flat XML Facets](#flat-xml-facets)
        * [Hierarchical XML Facets](#hierarchical-xml-facets)
    * [Collection Facets in JSON Responses](#facets-in-json-responses)
        * [Flat JSON facets](#flat-json-facets)
        * [Hierarchical JSON facets](#hierarchical-json-facets)
  * [Humanizers](#humanizers)
    * [Updating Humanizers](#updating-humanizers)
    * [Retrieving Humanizers](#retrieving-humanizers)
    * [Humanizers Report](#facets-humanizers-report)
  * [Search for Tiles](#search-for-tiles)
  * [Retrieve Controlled Keywords](#retrieve-controlled-keywords)
  * [Find collections that have been deleted after a given date](#deleted-collections)
  * [Find granules that have been deleted after a given date](#deleted-granules)
  * [Tagging](#tagging)
    * [Tag Access Control](#tag-access-control)
    * [Creating a Tag](#creating-a-tag)
    * [Retrieving a Tag](#retrieving-a-tag)
    * [Updating a Tag](#updating-a-tag)
    * [Deleting a Tag](#deleting-a-tag)
    * [Tag association](#tag-association)
    * [Associating Collections with a Tag by query](#associating-collections-with-a-tag-by-query)
    * [Associating Collections with a Tag by collection concept ids](#associating-collections-with-a-tag-by-concept-ids)
    * [Tag dissociation](#tag-dissociation)
    * [Dissociating a Tag from Collections by query](#dissociating-collections-with-a-tag-by-query)
    * [Dissociating a Tag from Collections by collection concept ids](#dissociating-collections-with-a-tag-by-concept-ids)
    * [Searching for Tags](#searching-for-tags)
  * [Variable](#variable)
    * [Searching for Variables](#searching-for-variables)
        * [Variable Search Parameters](#variable-search-params)
        * [Variable Search Response](#variable-search-response)
    * [Retrieving All Revisions of a Variable](#retrieving-all-revisions-of-a-variable)
    * [Sorting Variable Results](#sorting-variable-results)
    * [Variable Access Control](#variable-access-control)
  * [Service](#service)
    * [Searching for Services](#searching-for-services)
        * [Service Search Parameters](#service-search-params)
        * [Service Search Response](#service-search-response)
    * [Retrieving All Revisions of a Service](#retrieving-all-revisions-of-a-service)
    * [Sorting Service Results](#sorting-service-results)
    * [Service Access Control](#service-access-control)
    * [Service association](#service-association)
    * [Service dissociation](#service-dissociation)
  * [Tool](#tool)
    * [Searching for Tools](#searching-for-tools)
        * [Tool Search Parameters](#tool-search-params)
        * [Tool Search Response](#tool-search-response)
    * [Retrieving All Revisions of a Tool](#retrieving-all-revisions-of-a-tool)
    * [Sorting Tool Results](#sorting-tool-results)
    * [Tool Access Control](#tool-access-control)
    * [Tool association](#tool-association)
    * [Tool dissociation](#tool-dissociation)
  * [Subscription](#subscription)
    * [Searching for Subscriptions](#searching-for-subscriptions)
        * [Subscription Search Parameters](#subscription-search-params)
        * [Subscription Search Response](#subscription-search-response)
    * [Retrieving All Revisions of a Subscription](#retrieving-all-revisions-of-a-subscription)
    * [Sorting Subscription Results](#sorting-subscription-results)
    * [Subscription Access Control](#subscription-access-control)
  * %GENERIC-TABLE-OF-CONTENTS%
  * [Community Usage Metrics](#community-usage-metrics)
    * [Updating Community Usage Metrics](#updating-community-usage-metrics)
    * [Retrieving Community Usage Metrics](#retrieving-community-usage-metrics)
  * [Administrative Tasks](#administrative-tasks)
    * [Clear the cache](#clear-the-cache)
    * [Clear scroll session](#clear-scroll)
    * [Reset the application to the initial state](#reset-the-application-to-the-initial-state)
    * [Querying caches](#querying-caches)
    * [Check application health](#check-application-health)
  * [Associate Any Concepts](#associate-any-concepts)
     * [Concept associations](#concept-associations)
     * [Concept dissociations](#concept-dissociations)
     * [Associations in search result](#associations-in-search-result)
 * [CMR-Graphql](#cmr-graphql)
     * [Navigating Apollo Studio](#cmr-graphql-apollo-studio)
     * [Programmatic requests to CMR-Graphql](#cmr-graphql-programmatic-requests)
     * [Additional Information](#cmr-graphql-addtional-information)

### <a name="general-request-details"></a> General Request Details

#### <a name="maximum-url-length"></a> Maximum URL Length

The Maximum URL Length supported by CMR is indirectly controlled by the Request Header Size setting in Jetty which is set to 1MB. This translates to roughly 500k characters, however it is recommended that any GET request be limited to 6,000 characters, and in a web browser the recommended length is no longer than 2000 characters. Clients using the Search API with query parameters should be careful not to exceed this limit or they will get an HTTP response of 413 FULL HEAD. If a client expects that the query url could be extra long so that it exceeds 6k characters, they should use the POST API for searching.

#### <a name="cors-header-support"></a> CORS Header support

The CORS headers are supported on search endpoints. Check [CORS Documentation](https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS) for an explanation of CORS headers. Custom CORS request headers supported are Authorization, Client-Id, CMR-Request-Id, X-Request-Id, CMR-Scroll-Id and CMR-Search-After. Custom response headers supported are CMR-Hits, CMR-Request-Id, X-Request-Id, CMR-Scroll-Id, CMR-Search-After, CMR-Timed-Out, CMR-Shapefile-Original-Point-Count and CMR-Shapefile-Simplified-Point-Count.

#### <a name="query-parameters"></a> Query Parameters

 * `page_size` - Number of results per page - default is 10, max is 2000.
 * `page_num` - The page number to return.
 * `offset` - As an alternative to page_num, a 0-based offset of individual results may be specified.
 * `scroll` - A boolean flag (true/false) that allows all results to be retrieved efficiently. `page_size` is supported with `scroll` while `page_num` and `offset` are not. If `scroll` is `true` then the first call of a scroll session sets the page size; `page_size` is ignored on subsequent calls.
 * `sort_key` - Indicates one or more fields to sort on. Described below.
 * `pretty` - Return formatted results if set to true.
 * `token` - Specifies a user token from EDL or Launchpad for use as authentication. Using the standard [Authorization header](#headers) is the prefered way to supply a token. This parameter may be deprecated in the future.
 * `echo_compatible` - When set to true results will be returned in an ECHO compatible format. This mostly removes fields and features specific to the CMR such as revision id, granule counts and facets in collection results. Metadata format style results will also use ECHO style names for concept ids such as `echo_granule_id` and `echo_dataset_id`.

#### <a name="paging-details"></a> Paging Details

The CMR contains many more results than can be returned in a single response so the number of results that can be returned is limited. The parameters `page_num`, `offset`, and `page_size` along with the sort specified by `sort_key` control which items will be returned. The query parameter `page_size`, defaulting to 10, controls the amount of items that will be returned in a response. One of `page_num` or `offset` can be provided to index into the search results.

`page_num`, defaulting to 1, chooses a "page" of items to return. If a search matched 50 items the parameters `page_num=3&page_size=5` would return the 11th item through the 15th item.

`offset` is a 0 based index into the result set of a query. If a search matched 50 items the parameters `offset=3&page_size=5` would return 4th result through the 8th result.

Note: In the event which an ingest or delete occurs between paging requests, the order of your results may change, causing inconsistent results across pages.

You can not page past the 1 millionth item. Please contact the CMR Team at cmr-support@earthdata.nasa.gov if you need to retrieve items in excess of 1 million from the CMR. Additionally granule queries which do not target a set of collections are limited to paging up to the 10000th item.

#### <a name="search-after"></a> Search After

Search After supersedes scrolling. Search After allows the retrieval of all results of a query in a stateless manner and is the recommended way for deep paging by Elasticsearch. It is supported through the `CMR-Search-After` header. Search After is primarily intended to support harvesting of metadata.

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
curl -i -H 'CMR-Search-After: ["xyz", 789, 999]' "%CMR-ENDPOINT%/granules?concept_id=C1-PROV1&page_size=200"
```
There will be only 8 granules in the result set. We can deem the search has reached the end because the number of results returned is less than the page_size, but if we search again with the new `CMR-Search-After` header value returned, we will get an empty result set and there won't be a `CMR-Search-After` header in the response.

#### <a name="scrolling-details"></a> Scrolling Details

__NOTE:__ Scrolling is being deprecated in favor of [Search After](#search-after). Please switch your scroll based queries to [Search After](#search-after) which is more efficient and easier to use.

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
    * The UMM JSON format was originally used for an alpha version of UMM JSON search results. Currently it still returns data in that style to avoid breaking clients dependent on it. This will be changed in a future version to return the latest version of the UMM.
  * `umm_json`   "application/vnd.nasa.cmr.umm_results+json" (supported for collections, granules, variables and services)
    * The UMM JSON extension returns concepts in the latest version of the UMM.
  * `umm_json_vN_N_N` "application/vnd.nasa.cmr.umm_results+json; version=N.N.N (where `_N` can be repeat as many times as is necessary)."

#### <a name="request-timeouts"></a> Request Timeouts

The CMR operating environment imposes a hard limit of 180 seconds on any request, after which a 504 error is
returned. To avoid this, the CMR has an internal query timeout of 170 seconds - any query taking longer will time
out and a subset of the total hit results will be returned instead of an error. The response for queries that time
out will include the `CMR-Time-Out` header set to `true`.

#### <a name="request-moderation"></a> Moderating Client Request Traffic

In order to provide robust availability and performance for all clients of the service, CMR Search deploys a set of rate throttling rules for request traffic. These rules are defined to target specific request signatures, throttling the allowed rate of these searches in an effort to prevent overall degradation of system performance and availability. If a client request should exceed one of these rate throttling rules, the request will be rejected and a `429` error status returned to the client along with a `retry-after` header value. The suggested practice for any CMR Search client is to honor the `retry-after` header value and delay accordingly before re-issuing the failed request and continuing with its CMR Search processing.

### <a name="supported-result-formats"></a> Supported Result Formats

#### <a name="html"></a> HTML

The HTML response format is intended to be used only in a web browser to view a single collection record. The collection html representation can be viewed by putting the following in the web browser address bar.

```
%CMR-ENDPOINT%/concepts/<concept-id>
```
For private collection, an EDL bearer token or a Launchpad token can be used to grant permission. e.g.

```
%CMR-ENDPOINT%/concepts/<concept-id>?token=EDL-xxxxxx
```

#### <a name="atom"></a> Atom

See the [Atom specification](http://tools.ietf.org/html/rfc4287) for a full description of Atom.

The CMR Atom format provides search results in an XML file representing a feed of entries. The feed has the following fields:

|                         Atom Feed Level Feeds                         ||
|   Field    |                        Description                        |
| ---------- | --------------------------------------------------------- |
| id         | the URL linking to this feed                              |
| title      | Either 'ECHO dataset metadata' or 'ECHO granule metadata' |
| updated    | The date/time the search was executed                     |

 Each entry represents one search result, consisting of the following Atom standard fields and additional CMR specific fields:


|                                                           Atom Standard Fields                                                          ||
|            Field            |                                                Description                                                 |
| --------------------------- | ---------------------------------------------------------------------------------------------------------- |
| id                          | the CMR identifier for the result                                                                          |
| consortiums (collections only)             | Consortiums the collection is associated to.                                                |
| title                       | the UMM Entry Title                                                                                        |
| summary  (collections only) | the summary of intentions with which this collection was developed. - corresponds to the UMM summary field |
| updated                     | date/time of the last update to the associated metadata                                                     |

The following fields are specific to the CMR output and most correspond to ECHO10 fields of the same name.
Important to note, dataCenter corresponds to the provider id while organizations will contain the data center names:

|                                                                      CMR Specific Fields                                                                         ||
|                  Field                     |                                                     Description                                                      |
| ------------------------------------------ | -------------------------------------------------------------------------------------------------------------------- |
| echo:datasetId                             | UMM entry title of the collection                                                                                    |
| echo:shortName (collections only)          | provider defined short name of the collection                                                                        |
| echo:versionId (collections only)          | provider defined version id of the collection                                                                        |
| echo:collectionDataType (collections only) | type of the collection, e.g. Science Quality or Near Real Time                                                       |
| echo:producerGranuleId (granules only)     | producer granule id of the granule                                                                                   |
| echo:collectionConceptId (granules only)   | collection concept id of the granule                                                                                 |
| echo:granuleSizeMB (granules only)         | granule size in megabytes                                                                                            |
| echo:originalFormat                        | original metadata format                                                                                             |
| echo:dataCenter                            | provider id                                                                                   |
| echo:archiveCenter (collections only)      | archive center of the metadata                                                                                       |
| echo:organizations (collections only)      | organizations associated with the metadata, including data centers                                                                           |
| echo:processingLevelId (collections only)  | processing level id of the metadata                                                                                  |
| time:start                                 | start time of the metadata                                                                                           |
| time:end                                   | end time of the metadata                                                                                             |
| link                                       | online access and online resource urls associated with the metadata                                                  |
| echo:orbit (granules only)                 | orbit info of the metadata                                                                                           |
| echo:orbitCalSpatialDomain (granules only) | orbit calculated spatial domain info of the metadata                                                                  |
| echo:coordinateSystem                      | coordinate system info of the metadata                                                                               |
| echo:orbitParameters (collections only)    | fields related to the satellite orbit (startCircularLatitude, numberOfOrbits, inclinationAngle, period, swathWidth) |
| georss:point                               | spatial point info of the metadata                                                                                   |
| georss:line                                | spatial line info of the metadata                                                                                    |
| georss:box                                 | spatial bounding box info of the metadata                                                                            |
| georss:polygon                             | spatial polygon info of the metadata                                                                                 |
| echo:difId (collections only)              | associated dif id of the collection                                                                                  |
| echo:onlineAccessFlag                      | true if the data is available online                                                                                 |
| echo:browseFlag                            | true if the data contains browse imagery                                                                             |
| echo:hasGranules (collections only)        | true if there are granules associated with the collection                                                            |
| echo:granuleCount (collections only)       | granule count of the collection                                                                                      |
| echo:hasVariables (collections only)       | true if there are variables associated with the collection                                                           |
| echo:hasFormats (collections only)         | true if there are multiple supported formats for any services associated with the collection                         |
| echo:hasTransforms (collections only)      | true if there are transformations (subset, interpolation or projection) in any of its associated services            |
| echo:hasSpatialSubsetting (collections only)| true if any of its associated services support spatial subsetting            |
| echo:hasTemporalSubsetting (collections only)| true if any of its associated services support temporal subsetting            |
| echo:cloudHosted (collections only)        | true if the collection is hosted in the cloud            |
| relevance:score (collections only)         | relevance score of the collection to search parameters                                                               |
| echo:tag (collections only)                | tags associated with the collection. It includes sub-elements of tagKey and optional data which is in embedded JSON. |
| echo:dayNightFlag (granules only)          | day night flag of the granule                                                                                        |
| echo:cloudCover (granules only)            | cloud cover of the granule                                                                                           |

__Example__

```xml
<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns:os="http://a9.com/-/spec/opensearch/1.1/"
      xmlns:georss="http://www.georss.org/georss/10"
      xmlns="http://www.w3.org/2005/Atom"
      xmlns:dc="http://purl.org/dc/elements/1.1/"
      xmlns:echo="http://www.echo.nasa.gov/esip"
      xmlns:esipdiscovery="http://commons.esipfed.org/ns/discovery/1.2/"
      xmlns:gml="http://www.opengis.net/gml"
      xmlns:time="http://a9.com/-/opensearch/extensions/time/1.0/"
      esipdiscovery:version="1.2">
   <updated>2015-06-05T20:10:39.166Z</updated>
   <id>%CMR-ENDPOINT%/collections.atom?pretty=true</id>
   <title type="text">ECHO dataset metadata</title>
   <entry>
      <id>C1200000000-PROV1</id>
      <title type="text">dataset-id</title>
      <summary type="text">The AMSR-E/Aqua Level-3 5-day snow water equivalent (SWE) product includes global 5-day maximum SWE on Northern and Southern Hemisphere 25 km EASE-Grids, generated by the GSFC algorithm using Level-2A TBs.</summary>
      <updated>2010-10-06T11:45:39.530Z</updated>
      <echo:datasetId>dataset-id</echo:datasetId>
      <echo:shortName>short</echo:shortName>
      <echo:versionId>v1</echo:versionId>
      <echo:originalFormat>ECHO10</echo:originalFormat>
      <echo:dataCenter>PROV1</echo:dataCenter>
      <echo:orbitParameters/>
      <echo:onlineAccessFlag>false</echo:onlineAccessFlag>
      <echo:browseFlag>false</echo:browseFlag>
      <echo:hasVariables>true</echo:hasVariables>
      <echo:hasFormats>false</echo:hasFormats>
      <echo:hasTransforms>false</echo:hasTransforms>
      <echo:cloudHosted>true</echo:cloudHosted>
      <echo:tag>
        <echo:tagKey>tag1</echo:tagKey>
        <echo:data>{"status":"Reviewed","score":85}</echo:data>
      </echo:tag>
      <echo:tag>
        <echo:tagKey>tag2</echo:tagKey>
        <echo:data>"cloud cover &gt; 80"</echo:data>
      </echo:tag>
   </entry>
</feed>
```

#### <a name="csv"></a> CSV

The comma separated value (CSV) format is supported for collections and granules.
Version and Processing Level are strings. When importing into the spreadsheet, please specify text as their column types. This will prevent version "004" to be displayed as "4" in the spreadsheet.

__Examples__

Collection response
```csv
Data Provider,Short Name,Version,Entry Title,Processing Level,Platforms,Start Time,End Time
PROV1,short-name,V1,dataset-id,L1,platform #1,1970-01-01T12:00:00.000Z,
PROV2,very-short,v1,data-id,L3,platform2,2019-08-03T20:11:59.143Z,
```

Granule response
```csv
Granule UR,Producer Granule ID,Start Time,End Time,Online Access URLs,Browse URLs,Cloud Cover,Day/Night,Size
SC:SPL1AA.001:12345,SMAP_L1C_S0_HIRES_00016_A_20150530T160100_R03001_001.h5,,,,,,,
```

#### Metadata Responses (DIF, DIF 10, ECHO 10, ISO-SMAP, ISO-MENDS)

All of the XML Metadata formats (except the XML used in returning references only) have the same structure, differing only in the way each result is returned. These formats return a single XML document with a `<results>` XML element containing the following fields as sub-elements:

|         Field         |                    Description                     |
| --------------------- | -------------------------------------------------- |
| hits                  | the number of results matching the search query    |
| took                  | time in milliseconds it took to perform the search |
| result (zero or more) | a single search result - documented below          |

The results are returned as a sequence of `<result>` XML elements, the contents of which are documents in the specified format (DIF, ECHO 10 , etc.). If tags are included in the response a `<tags>` element will directly follow the metadata in the `<result>` element. Each `<result>` XML element contains the following attributes:

|  Attribute  |                  Description                  |
| ----------- | --------------------------------------------- |
| concept-id  | the CMR unique identifier for the concept     |
| format      | the mime-type for the returned metadata       |
| revision-id | the CMR revision number of the stored concept |

#### <a name="dif-9"></a> DIF 9

__NOTE:__ The DIF 9 format is being deprecated in favor of DIF 10. While it is still supported, we strongly discourage its use for new metadata.

Mime-type application/dif+xml corresponds to the DIF 9 format. See the [specification](https://cdn.earthdata.nasa.gov/dif/9.x)

__Example__

```xml
<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>30</took>
    <result concept-id="C1200000000-PROV1" format="application/dif+xml" revision-id="1">
        <DIF xmlns="http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
            xmlns:dif="http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/ http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/dif_v9.9.3.xsd">
            <Entry_ID>short_v1</Entry_ID>
            <Entry_Title>dataset-id</Entry_Title>
            <Data_Set_Citation>
                <Version>v1</Version>
            </Data_Set_Citation>
            <Quality/>
            <Use_Constraints/>
            <Summary>
                <Abstract>The AMSR-E/Aqua Level-3 5-day snow water equivalent (SWE) product includes global 5-day maximum SWE on Northern and Southern Hemisphere 25 km EASE-Grids, generated by the GSFC algorithm using Level-2A TBs.</Abstract>
                <Purpose/>
            </Summary>
            <Metadata_Name>CEOS IDN DIF</Metadata_Name>
            <Metadata_Version>VERSION 9.9.3</Metadata_Version>
            <DIF_Creation_Date>2008-04-22T12:53:38.320Z</DIF_Creation_Date>
            <Last_DIF_Revision_Date>2010-10-06T11:45:39.530Z</Last_DIF_Revision_Date>
        </DIF>
        <tags>
          <tag>
            <tagKey>tag1</tagKey>
            <data>{"status":"Reviewed","score":85}</data>
          </tag>
          <tag>
            <tagKey>tag2</tagKey>
            <data>"cloud cover &gt; 80"</data>
          </tag>
        </tags>
    </result>
</results>
```

#### <a name="dif-10"></a> DIF 10

See the [specification](https://cdn.earthdata.nasa.gov/dif/10.x/) for details.

__Example__

```xml
<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>30</took>
    <result concept-id="C1200000000-PROV1"
        format="application/dif10+xml" revision-id="1">
        <DIF xmlns="http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
            xmlns:dif="http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
            <Entry_ID>short_v1</Entry_ID>
            <Version>v1</Version>
            <Entry_Title>dataset-id</Entry_Title>
            <Science_Keywords>
                <Category>Not provided</Category>
                <Topic>Not provided</Topic>
                <Term>Not provided</Term>
            </Science_Keywords>
            <Platform>
                <Type>Not provided</Type>
                <Short_Name>Not provided</Short_Name>
                <Instrument>
                    <Short_Name>Not provided</Short_Name>
                </Instrument>
            </Platform>
            <Spatial_Coverage>
                <Granule_Spatial_Representation>CARTESIAN</Granule_Spatial_Representation>
            </Spatial_Coverage>
            <Project>
                <Short_Name>Not provided</Short_Name>
            </Project>
            <Quality/>
            <Use_Constraints/>
            <Summary>
                <Abstract>The AMSR-E/Aqua Level-3 5-day snow water equivalent (SWE) product includes global 5-day maximum SWE on Northern and Southern Hemisphere 25 km EASE-Grids, generated by the GSFC algorithm using Level-2A TBs.</Abstract>
                <Purpose/>
            </Summary>
            <Related_URL>
                <URL>Not provided</URL>
            </Related_URL>
            <Metadata_Name>CEOS IDN DIF</Metadata_Name>
            <Metadata_Version>VERSION 10.1</Metadata_Version>
            <Metadata_Dates>
                <Metadata_Creation>2008-04-22T12:53:38.320Z</Metadata_Creation>
                <Metadata_Last_Revision>2010-10-06T11:45:39.530Z</Metadata_Last_Revision>
                <Data_Creation>1970-01-01T00:00:00</Data_Creation>
                <Data_Last_Revision>1970-01-01T00:00:00</Data_Last_Revision>
            </Metadata_Dates>
        </DIF>
        <tags>
          <tag>
            <tagKey>tag1</tagKey>
            <data>{"status":"Reviewed","score":85}</data>
          </tag>
          <tag>
            <tagKey>tag2</tagKey>
            <data>"cloud cover &gt; 80"</data>
          </tag>
        </tags>
    </result>
</results>
```

#### <a name="echo-10"></a> ECHO 10

See the [specification](https://cdn.earthdata.nasa.gov/echo/schemas/10.0/) for details.

__Example__

```xml
<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>17</took>
    <result concept-id="C1200000000-PROV1"
        format="application/echo10+xml" revision-id="1">
        <Collection>
            <ShortName>short</ShortName>
            <VersionId>v1</VersionId>
            <InsertTime>2008-04-22T12:53:38.320Z</InsertTime>
            <LastUpdate>2010-10-06T11:45:39.530Z</LastUpdate>
            <LongName>AMSR-E/Aqua 5-Day L3 Global Snow Water Equivalent EASE-Grids</LongName>
            <DataSetId>dataset-id</DataSetId>
            <Description>The AMSR-E/Aqua Level-3 5-day snow water equivalent (SWE) product includes global 5-day maximum SWE on Northern and Southern Hemisphere 25 km EASE-Grids, generated by the GSFC algorithm using Level-2A TBs.</Description>
            <Orderable>false</Orderable>
            <Visible>true</Visible>
        </Collection>
        <tags>
          <tag>
            <tagKey>tag1</tagKey>
            <data>{"status":"Reviewed","score":85}</data>
          </tag>
          <tag>
            <tagKey>tag2</tagKey>
            <data>"cloud cover &gt; 80"</data>
          </tag>
        </tags>
    </result>
</results>
```

#### <a name="iso-mends"></a> ISO-MENDS (ISO-19115-2)

See the [specification](https://cdn.earthdata.nasa.gov/iso/)

__Example__

```xml
<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>1373</took>
    <result concept-id="C1200000000-PROV1"
        format="application/iso19115+xml" revision-id="1">
        <gmi:MI_Metadata
            xmlns:eos="http://earthdata.nasa.gov/schema/eos"
            xmlns:gco="http://www.isotc211.org/2005/gco"
            xmlns:gmd="http://www.isotc211.org/2005/gmd"
            xmlns:gmi="http://www.isotc211.org/2005/gmi"
            xmlns:gml="http://www.opengis.net/gml/3.2"
            xmlns:gmx="http://www.isotc211.org/2005/gmx"
            xmlns:gsr="http://www.isotc211.org/2005/gsr"
            xmlns:gss="http://www.isotc211.org/2005/gss"
            xmlns:gts="http://www.isotc211.org/2005/gts"
            xmlns:srv="http://www.isotc211.org/2005/srv"
            xmlns:swe="http://schemas.opengis.net/sweCommon/2.0/"
            xmlns:xlink="http://www.w3.org/1999/xlink"
            xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
            <!--Other Properties, all:0, coi:0,ii:0,si:0,pli:0,pri:0,qi:0,gi:0,ci:0,dk:0,pcc:0,icc:0,scc:0-->
            <gmd:fileIdentifier>
                <gco:CharacterString>gov.nasa.echo:dataset-id</gco:CharacterString>
            </gmd:fileIdentifier>
            <gmd:language>
                <gco:CharacterString>eng</gco:CharacterString>
            </gmd:language>
            <gmd:characterSet>
                <gmd:MD_CharacterSetCode
                    codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_CharacterSetCode" codeListValue="utf8">utf8</gmd:MD_CharacterSetCode>
            </gmd:characterSet>
            <gmd:hierarchyLevel>
                <gmd:MD_ScopeCode
                    codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode" codeListValue="series">series</gmd:MD_ScopeCode>
            </gmd:hierarchyLevel>
            <gmd:contact gco:nilReason="missing"/>
            <gmd:dateStamp>
                <gco:DateTime>2015-06-05T16:17:30.386-04:00</gco:DateTime>
            </gmd:dateStamp>

            ...

            <gmd:dataQualityInfo>
                <gmd:DQ_DataQuality>
                    <gmd:scope>
                        <gmd:DQ_Scope>
                            <gmd:level>
                                <gmd:MD_ScopeCode
                                    codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode" codeListValue="series">series</gmd:MD_ScopeCode>
                            </gmd:level>
                        </gmd:DQ_Scope>
                    </gmd:scope>
                    <gmd:lineage>
                        <gmd:LI_Lineage>
                            <gmd:processStep>
                                <gmi:LE_ProcessStep>
                                    <gmd:description gco:nilReason="unknown"/>
                                </gmi:LE_ProcessStep>
                            </gmd:processStep>
                        </gmd:LI_Lineage>
                    </gmd:lineage>
                </gmd:DQ_DataQuality>
            </gmd:dataQualityInfo>
            <gmd:metadataMaintenance>
                <gmd:MD_MaintenanceInformation>
                    <gmd:maintenanceAndUpdateFrequency>
                        <gmd:MD_MaintenanceFrequencyCode
                            codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_MaintenanceFrequencyCode" codeListValue="irregular">irregular</gmd:MD_MaintenanceFrequencyCode>
                    </gmd:maintenanceAndUpdateFrequency>
                    <gmd:maintenanceNote>
                        <gco:CharacterString>Translated from ECHO using ECHOToISO.xsl Version: 1.31 (Nov. 3, 2014)</gco:CharacterString>
                    </gmd:maintenanceNote>
                </gmd:MD_MaintenanceInformation>
            </gmd:metadataMaintenance>
            <gmi:acquisitionInformation>
                <gmi:MI_AcquisitionInformation/>
            </gmi:acquisitionInformation>
        </gmi:MI_Metadata>
        <tags>
          <tag>
            <tagKey>tag1</tagKey>
            <data>{"status":"Reviewed","score":85}</data>
          </tag>
          <tag>
            <tagKey>tag2</tagKey>
            <data>"cloud cover &gt; 80"</data>
          </tag>
        </tags>
    </result>
</results>
```

#### <a name="iso-smap"></a> ISO-SMAP

See the [specification](https://cdn.earthdata.nasa.gov/iso/)

__Example__

```xml
<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>15</took>
    <result concept-id="C1200000000-PROV1"
        format="application/iso:smap+xml" revision-id="1">
        <gmd:DS_Series xmlns:gco="http://www.isotc211.org/2005/gco"
            xmlns:gmd="http://www.isotc211.org/2005/gmd"
            xmlns:gmi="http://www.isotc211.org/2005/gmi"
            xmlns:gml="http://www.opengis.net/gml/3.2"
            xmlns:gmx="http://www.isotc211.org/2005/gmx"
            xmlns:gsr="http://www.isotc211.org/2005/gsr"
            xmlns:gss="http://www.isotc211.org/2005/gss"
            xmlns:gts="http://www.isotc211.org/2005/gts"
            xmlns:xlink="http://www.w3.org/1999/xlink"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.isotc211.org/2005/gmd file:/Users/bweiss/Desktop/SMAP/Metadata/NASASchemaFolder/schema.xsd">
            <gmd:composedOf gco:nilReason="inapplicable"/>
            <gmd:seriesMetadata>
                <gmi:MI_Metadata
                    xmlns:srv="http://www.isotc211.org/2005/srv" xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <gmd:fileIdentifier>
                        <gco:CharacterString>L1A_Radar</gco:CharacterString>
                        <!-- Assume that the file Identifier for series metadata would be the identifier that denotes that a file belongs to this series. -->
                    </gmd:fileIdentifier>
                    <gmd:language>
                        <gco:CharacterString>eng</gco:CharacterString>
                    </gmd:language>
                    <gmd:characterSet>
                        <gmd:MD_CharacterSetCode
                            codeList="http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#MD_CharacterSetCode" codeListValue="utf8">utf8</gmd:MD_CharacterSetCode>
                    </gmd:characterSet>

                    ...

                    <gmd:identificationInfo>
                        <gmd:MD_DataIdentification>
                            <gmd:citation>
                                <gmd:CI_Citation>
                                    <gmd:title>
                                    <gco:CharacterString>DIFID</gco:CharacterString>
                                    </gmd:title>
                                    <gmd:date>
                                    <gmd:CI_Date>
                                    <gmd:date>
                                    <gco:DateTime>2013-04-05T17:15:00Z</gco:DateTime>
                                    </gmd:date>
                                    <gmd:dateType>
                                    <gmd:CI_DateTypeCode
                                    codeList="http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode" codeListValue="revision">revision</gmd:CI_DateTypeCode>
                                    </gmd:dateType>
                                    </gmd:CI_Date>
                                    </gmd:date>
                                    <gmd:identifier>
                                    <gmd:MD_Identifier>
                                    <gmd:code>
                                    <gco:CharacterString>A_DIF_ID</gco:CharacterString>
                                    </gmd:code>
                                    </gmd:MD_Identifier>
                                    </gmd:identifier>
                                </gmd:CI_Citation>
                            </gmd:citation>
                            <gmd:abstract>
                                <gco:CharacterString>DIFID</gco:CharacterString>
                            </gmd:abstract>
                            <gmd:purpose>
                                <gco:CharacterString>DIFID</gco:CharacterString>
                            </gmd:purpose>
                            <gmd:language>
                                <gco:CharacterString>eng</gco:CharacterString>
                            </gmd:language>
                        </gmd:MD_DataIdentification>
                    </gmd:identificationInfo>
                </gmi:MI_Metadata>
            </gmd:seriesMetadata>
        </gmd:DS_Series>
    </result>
</results>
```

#### <a name="json"></a> JSON

The JSON response contains all the fields in the ATOM response, plus the the following fields:

* associations - a map of the concept ids of concepts that are associated with the collection.
* association-details - a map of the concept ids, optional revision ids, and optional data of concepts that are associated with the collection.
* platforms - a list of platform short names of the collection.
* service_features - a map of service features for OPeNDAP, ESI and Harmony services.

__Example__

```json
{
  "feed": {
    "updated": "2015-06-05T17:52:10.316Z",
    "id": "%CMR-ENDPOINT%/collections.json?pretty=true",
    "title": "ECHO dataset metadata",
    "entry": [{
      "version_id": "v1",
      "updated": "2010-10-06T11:45:39.530Z",
      "dataset_id": "dataset-id",
      "data_center": "PROV1",
      "short_name": "short",
      "title": "dataset-id",
      "summary": "The AMSR-E/Aqua Level-3 5-day snow water equivalent (SWE) product includes global 5-day maximum SWE on Northern and Southern Hemisphere 25 km EASE-Grids, generated by the GSFC algorithm using Level-2A TBs.",
      "orbit_parameters": {},
      "id": "C1200000000-PROV1",
      "original_format": "ECHO10",
      "browse_flag": false,
      "has_variables": true,
      "has_formats": false,
      "has_transforms": false,
      "has_spatial_subsetting": false,
      "has_temporal_subsetting": false,
      "cloud_hosted": true,
      "online_access_flag": false,
      "platforms": ["Platform1"],
      "consortiums" : [ "ESA", "FEDEO" ],
      "service_features": {
        "opendap": {
          "has_formats": false,
          "has_variables": true,
          "has_transforms": true,
          "has_spatial_subsetting": true,
          "has_temporal_subsetting": true
        },
        "esi": {
          "has_formats": true,
          "has_variables": false,
          "has_transforms": true,
          "has_spatial_subsetting": true,
          "has_temporal_subsetting": true
        },
        "harmony": {
          "has_formats": true,
          "has_variables": true,
          "has_transforms": false,
          "has_spatial_subsetting": false,
          "has_temporal_subsetting": false
        }
      },
      "tags": {
        "tag1": {
          "data": {
            "score": 85,
            "status": "reviewed"
          }
        },
        "tag2": {
          "data": "cloud cover > 80"
        }
      },
      "associations": {
        "variables": ["V1200000007-PROV1"],
        "services": ["S1200000008-PROV1", "S1200000009-PROV1","S1200000010-PROV1"],
        "tools": ["TL1200000011-PROV1", "TL1200000012-PROV1","TL1200000013-PROV1"]
      },
      "association-details": {
        "variables": [{"concept-id": "V1200000007-PROV1"}],
        "services": [{"concept-id": "S1200000008-PROV1",
                      "data": {"formatting-type": "zarr",
                               "regridding-type": {"xyz": "zyx"}}},
                     {"concept-id": "S1200000009-PROV1"},
                     {"concept-id": "S1200000010-PROV1"}],
        "tools": [{"concept-id": "TL1200000011-PROV1"},
                  {"concept-id": "TL1200000012-PROV1"},
                  {"concept-id": "TL1200000013-PROV1"}]
      }
    }]
  }
}
```

#### <a name="umm-json"></a> UMM JSON

The UMM JSON response contains meta-metadata of the collection and the UMM fields. The UMM JSON format is applicable to collection, granule, variable, service and tool searches. The UMM-JSON response is helpful if you wish to get the native-id of a concept after ingesting it. The version of the UMM returned will be the version requested or the latest version. Clients are recommended to always specify a version to avoid breaking changes in UMM.

This format can be retrieved in a variety of methods:

* A url extension with a version: `%CMR-ENDPOINT%/collections.umm_json_v1_4`
  * Note extension `.umm-json` still returns the original alpha version of this response to avoid breaking clients. This will change in the future.
* An accept header: `application/vnd.nasa.cmr.umm_results+json; version=1.4`
   * The version is optional but recommended.

__Example__

```json
{
  "hits" : 2,
  "took" : 11,
  "items" : [ {
    "meta" : {
      "revision-id" : 3,
      "deleted" : false,
      "format" : "application/echo10+xml",
      "provider-id" : "PROV1",
      "native-id" : "et1",
      "concept-id" : "C1200000000-PROV1",
      "revision-date" : "2016-07-27T12:00:17Z",
      "concept-type" : "collection",
      "has-variables" : true,
      "has-formats" : false,
      "has-transforms" : false,
      "has-spatial-subsetting" : false,
      "has-temporal-subsetting" : false,
      "associations" : {
        "variables" : [ "V1200000007-PROV1" ],
        "services" : [ "S1200000008-PROV1", "S1200000009-PROV1" ]
        "tools" : [ "TL1200000010-PROV1", "TL1200000011-PROV1" ]
      }
    },
    "umm" : {
      "SpatialExtent" : {
        "HorizontalSpatialDomain" : {
          "Geometry" : {
            "CoordinateSystem" : "GEODETIC",
            "Points" : [ {
              "Longitude" : 0.0,
              "Latitude" : 90.0
            } ]
          }
        },
        "GranuleSpatialRepresentation" : "GEODETIC"
      },
      "ScienceKeywords" : [ {
        "Category" : "Cat1",
        "Topic" : "Topic1",
        "Term" : "Term1"
      } ],
      "TemporalExtents" : [ {
        "RangeDateTimes" : [ {
          "BeginningDateTime" : "2000-01-01T00:00:00.000Z"
        } ]
      } ],
      "ProcessingLevel" : {
        "Id" : "Level 1"
      },
      "ShortName" : "s1",
      "EntryTitle" : "et1",
      "RelatedUrls" : [ {
        "Description" : "description648",
        "URLContentType" : "DistributionURL",
        "Type" : "GET DATA" ,
        "URL" : "http://example.com/file649"
      } ],
      "DataDates" : [ {
        "Date" : "2012-01-11T10:00:00.000Z",
        "Type" : "CREATE"
      }, {
        "Date" : "2012-01-19T18:00:00.000Z",
        "Type" : "UPDATE"
      } ],
      "Abstract" : "long-name651",
      "Version" : "v2",
      "DataCenters" : [ {
        "Roles" : [ "ARCHIVER" ],
        "ShortName" : "Not provided"
      } ],
      "Platforms" : [ {
        "Type" : "Type647",
        "ShortName" : "platform",
        "LongName" : "long-name646"
      } ]
    }
  }, {
    "meta" : {
      "native-id" : "et3",
      "provider-id" : "PROV2",
      "concept-type" : "collection",
      "concept-id" : "C1200000002-PROV2",
      "revision-date" : "2016-07-27T12:00:17Z",
      "user-id" : "user3",
      "deleted" : false,
      "revision-id" : 1,
      "format" : "application/echo10+xml",
      "has-variables" : false,
      "has-formats" : false,
      "has-transforms" : false,
      "has-spatial-subsetting" : false,
      "has-temporal-subsetting" : false
    },
    "umm" : {
      "..."
    }
  } ]
}
```

When retrieving data in UMM JSON that has a native format other than UMM JSON, if there is an error parsing an individual field an "\_errors" field will be added to the UMM with details about the parsing error.

__Example__

```json
{ "Projects" : [ {
    "ShortName" : "Project2 Short Name",
    "LongName" : "Project2 Long Name",
    "Campaigns" : [ "Project 2 Campaign1 Short Name", "Project 2 Campaign2 Short Name" ],
    "_errors" : {
      "StartDate" : "Could not parse date-time value: 2002:03:01T01:00:00Z",
      "EndDate" : "Could not parse date-time value: 2002:04:02T01:00:00Z"
    }
  } ] }
```

A collection containing "\_errors" is not valid UMM and cannot be ingested into the CMR.

#### <a name="kml"></a> KML

KML is the [XML language](http://www.opengeospatial.org/standards/kml) used by the Google Earth application and is used by the CMR to return spatial data associated with a collection or granule.

__Example__

```xml
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
   <Document>
      <Style id="geodetic_style">
         <LineStyle>
            <color>ffffffff</color>
            <colorMode>random</colorMode>
            <width>2</width>
         </LineStyle>
         <IconStyle>
            <color>ffffffff</color>
            <colorMode>random</colorMode>
            <scale>3.0</scale>
         </IconStyle>
         <PolyStyle>
            <fill>0</fill>
         </PolyStyle>
      </Style>
      <Style id="cartesian_style">
         <LineStyle>
            <color>ffffffff</color>
            <colorMode>random</colorMode>
            <width>2</width>
         </LineStyle>
         <IconStyle>
            <color>ffffffff</color>
            <colorMode>random</colorMode>
            <scale>3.0</scale>
         </IconStyle>
         <PolyStyle>
            <color>00ffffff</color>
         </PolyStyle>
      </Style>
      <Placemark>
         <name>SC:GLAH06.034:52592022</name>
         <styleUrl>#geodetic_style</styleUrl>
         <Polygon>
            <tessellate>1</tessellate>
            <outerBoundaryIs>
               <LinearRing>
                  <coordinates>54.78656326403332,-50.09193846025951 51.20574574139643,-31.559024005924392 48.4203450238013,-12.998336645829609 45.85491943219788,5.57000444208618 43.19937665770624,24.135158655763025 40.044971936186634,42.68280248541986 38.50355532538929,49.84326030061438 38.53129884465536,49.845207013547224 40.06934290325555,42.68451022431186 43.21903945805716,24.13653428810218 45.87295686415376,5.571265771529057 48.438767344855485,-12.997048263813937 51.22679482714953,-31.55755075104193 54.81444766176902,-50.0899817427687 54.78656326403332,-50.09193846025951</coordinates>
               </LinearRing>
            </outerBoundaryIs>
         </Polygon>
      </Placemark>
   </Document>
</kml>
```

#### <a name="open-data"></a> Open Data

The Open Data format was developed as part of [Project Open Data](https://project-open-data.cio.gov) in an attempt to make data more accessible. See the Open Data [schema](https://project-open-data.cio.gov/v1.1/schema/) for details.

__Example__

```json
{
  "conformsTo" : "https://project-open-data.cio.gov/v1.1/schema",
  "dataset" : [ {
    "description" : "The AMSR-E/Aqua Level-3 5-day snow water equivalent (SWE) product includes global 5-day maximum SWE on Northern and Southern Hemisphere 25 km EASE-Grids, generated by the GSFC algorithm using Level-2A TBs.",
    "accessLevel" : "public",
    "bureauCode" : [ "026:00" ],
    "publisher" : {
      "name" : null,
      "subOrganizationOf" : {
        "name" : "National Aeronautics and Space Administration",
        "subOrganizationOf" : {
          "name" : "U.S. Government"
        }
      }
    },
    "contactPoint" : {
      "fn" : "undefined",
      "hasEmail" : "mailto:support@earthdata.nasa.gov"
    },
    "modified" : "2010-10-06T11:45:39.530Z",
    "title" : "dataset-id",
    "theme" : [ "geospatial" ],
    "keyword" : [ "National Geospatial Data Asset", "NGDA" ],
    "language" : [ "en-US" ],
    "programCode" : [ "026:001" ],
    "identifier" : "C1200000000-PROV1",
    "issued" : "2008-04-22T12:53:38.320Z"
  } ]
}
```

#### <a name="stac"></a> STAC

The STAC (SpatioTemporal Asset Catalog) result format is a specification for describing geospatial data with JSON and GeoJSON. The related STAC-API specification defines an API for searching and browsing STAC catalogs. See the [STAC Specification](https://stacspec.org/) for details.

CMR supports STAC result format for collection/granule retrieval and granule searches. Because STAC search is based on paging by page number, parameters `offset`, `scroll`, and `CMR-Scroll-Id` and `CMR-Search-After` headers are not supported with STAC format. The maximum number of results returned in STAC result format is 1 million.

__Granule Retrieval in STAC Format Response Example via GET__

```json
{
  "type": "FeatureCollection",
  "stac_version": "1.0.0",
  "numberReturned": 1,
  "numberMatched": 3,
  "features": [
    {
      "type": "Feature",
      "stac_version": "1.0.0",
      "id": "G1200000011-PROV1",
      "collection": "C1200000009-PROV1",
      "stac_extensions": [
        "https://stac-extensions.github.io/eo/v1.0.0/schema.json"
      ],
      "geometry": {
        "type": "Polygon",
        "coordinates": [
          [
            [ 10, 0 ],
            [ 20, 0 ],
            [ 20, 30 ],
            [ 10, 30 ],
            [ 10, 0 ]
          ]
        ]
      },
      "properties": {
        "eo:cloud_cover": 20,
        "start_datetime": "2011-02-01T12:00:00.000Z",
        "end_datetime": "2011-02-11T12:00:00.000Z",
        "datetime": "2011-02-01T12:00:00.000Z"
      },
      "bbox": [ 10, 0, 20, 30 ],
      "links": [
        {
          "rel": "self",
          "href": "%CMR-ENDPOINT%/concepts/G1200000011-PROV1.stac"
        },
        {
          "rel": "parent",
          "href": "%CMR-ENDPOINT%/concepts/C1200000009-PROV1.stac"
        },
        {
          "rel": "collection",
          "href": "%CMR-ENDPOINT%/concepts/C1200000009-PROV1.stac"
        },
        {
          "rel": "root",
          "href": "%CMR-ENDPOINT%/"
        },
        {
          "rel": "via",
          "href": "%CMR-ENDPOINT%/concepts/G1200000011-PROV1.json"
        },
        {
          "rel": "via",
          "href": "%CMR-ENDPOINT%/concepts/G1200000011-PROV1.umm_json"
        }
      ],
      "assets": {
        "metadata": {
          "type": "application/xml",
          "href": "%CMR-ENDPOINT%/concepts/G1200000011-PROV1.xml"
        }
      }
    }
  ],
  "links": [
    {
      "rel": "self",
      "href": "%CMR-ENDPOINT%/granules.stac?collection-concept-id=C1200000009-PROV1&page_size=1&page_num=2"
    },
    {
      "rel": "root",
      "href": "%CMR-ENDPOINT%/"
    },
    {
      "rel": "prev",
      "method": "GET",
      "href": "%CMR-ENDPOINT%/granules.stac?collection-concept-id=C1200000009-PROV1&page_size=1&page_num=1"
    },
    {
      "rel": "next",
      "method": "GET",
      "href": "%CMR-ENDPOINT%/granules.stac?collection-concept-id=C1200000009-PROV1&page_size=1&page_num=3"
    }
  ],
  "context": {
    "limit": 1000000,
    "returned": 1,
    "matched": 3
  }
}
```

__Granule Retrieval in STAC Format Response Example via POST__

```json
{
  "type" : "FeatureCollection",
  "stac_version" : "1.0.0",
  "numberMatched" : 3,
  "numberReturned" : 1,
  "features" : [ {
    "properties" : {
      "datetime" : "2011-02-01T12:00:00.000Z",
      "start_datetime" : "2011-02-01T12:00:00.000Z",
      "end_datetime" : "2011-02-11T12:00:00.000Z",
      "eo:cloud_cover" : 20.0
    },
    "stac_extensions" : [ "https://stac-extensions.github.io/eo/v1.0.0/schema.json" ],
    "collection" : "C1200000009-PROV1",
    "id" : "G1200000011-PROV1",
    "geometry" : {
      "type" : "Polygon",
      "coordinates" : [ [ [ 10.0, 0.0 ], [ 20.0, 0.0 ], [ 20.0, 30.0 ], [ 10.0, 30.0 ], [ 10.0, 0.0 ] ] ]
    },
    "bbox" : [ 10.0, 0.0, 20.0, 30.0 ],
    "links" : [ {
      "rel" : "self",
      "href" : "http://localhost:3003/concepts/G1200000011-PROV1.stac"
    }, {
      "rel" : "parent",
      "href" : "http://localhost:3003/concepts/C1200000009-PROV1.stac"
    }, {
      "rel" : "collection",
      "href" : "http://localhost:3003/concepts/C1200000009-PROV1.stac"
    }, {
      "rel" : "root",
      "href" : "http://localhost:3003/"
    }, {
      "rel" : "via",
      "href" : "http://localhost:3003/concepts/G1200000011-PROV1.json"
    }, {
      "rel" : "via",
      "href" : "http://localhost:3003/concepts/G1200000011-PROV1.umm_json"
    } ],
    "stac_version" : "1.0.0",
    "type" : "Feature",
    "assets" : {
      "metadata" : {
        "href" : "http://localhost:3003/concepts/G1200000011-PROV1.xml",
        "type" : "application/xml"
      }
    }
  } ],
  "links" : [ {
    "rel" : "self",
    "href" : "http://localhost:3003/granules.stac?collection_concept_id=C1200000009-PROV1&pretty=true&page_num=2"
  }, {
    "rel" : "root",
    "href" : "http://localhost:3003/"
  }, {
    "rel" : "prev",
    "body" : {
      "collection_concept_id" : "C1200000009-PROV1",
      "page_num" : "1",
      "page_size" : "1",
      "pretty" : "true",
      "provider" : "PROV1"
    },
    "method" : "POST",
    "merge" : true,
    "href" : "http://localhost:3003/granules.stac"
  }, {
    "rel" : "next",
    "body" : {
      "collection_concept_id" : "C1200000009-PROV1",
      "page_num" : "3",
      "page_size" : "1",
      "pretty" : "true",
      "provider" : "PROV1"
    },
    "method" : "POST",
    "merge" : true,
    "href" : "http://localhost:3003/granules.stac"
  } ],
  "context" : {
    "returned" : 1,
    "limit" : 1000000,
    "matched" : 3
  }
}
```
#### <a name="xml-reference"></a> XML

The XML response format is used for returning references to search results. It consists of the following fields:

|   Field    |                    Description                     |
| ---------- | -------------------------------------------------- |
| hits       | the number of results matching the search query    |
| took       | time in milliseconds it took to perform the search |
| references | identifying information about each search result    |

The `references` field may contain multiple `reference` entries, each consisting of the following fields:

|    Field    |                                                   Description                                                   |
| ----------- | --------------------------------------------------------------------------------------------------------------- |
| name        | the provider's unique identifier for the item. This is Granule UR for granules and Entry Title for collections. |
| id          | the CMR identifier for the result                                                                               |
| location    | the URL at which the full metadata for the result can be retrieved                                              |
| revision-id | the internal CMR version number for the result                                                                  |

__Example__

```xml
<?xml version="1.0" encoding="UTF-8"?>
<results>
   <hits>1</hits>
   <took>9</took>
   <references>
      <reference>
         <name>dataset-id</name>
         <id>C1200000000-PROV1</id>
         <location>%CMR-ENDPOINT%/concepts/C1200000000-PROV1</location>
         <revision-id>1</revision-id>
      </reference>
   </references>
</results>
```

By passing `echo_compatible=true` in the URL parameters the output can be forced to a format that is compatible with the ECHO search response:

__Example__

```xml
<?xml version="1.0" encoding="UTF-8"?>
<references type="array">
   <reference>
      <name>SMAP Collection Dataset ID</name>
      <id>C1200000000-PROV1</id>
      <location>%CMR-ENDPOINT%/concepts/C1200000000-PROV1</location>
   </reference>
</references>
```

### <a name="temporal-range-searches"></a> Temporal Range searches

A couple of parameters used in search expect a date range as input. For example, the parameter "temporal" used in collection and granule searches and the parameter "equator_crossing_longitude" used in granule searches both accept date ranges. All these parameters expect temporal ranges in the same format. The temporal ranges can be specified as a pair of date-time values separated by comma(,). Exactly one of the two bounds of the interval can be omitted. In addition to comma separated values, one can also specify temporal ranges as [ISO 8601 time intervals](https://en.wikipedia.org/?title=ISO_8601#Time_intervals). Some examples of valid temporal range values are:

`2000-01-01T10:00:00Z,2010-03-10T12:00:00Z` - matches data between `2000-01-01T10:00:00Z` and `2010-03-10T12:00:00Z`
`,2010-03-10T12:00:00Z` - matches data before `2010-03-10T12:00:00Z`
`2000-01-01T10:00:00Z,` - matches data after `2010-03-10T12:00:00Z`
`2000-01-01T10:00:00Z/2010-03-10T12:00:00Z` - matches data between `2000-01-01T10:00:00Z` and `2010-03-10T12:00:00Z`
`2000-01-01T10:00:00Z/` - matches data after `2010-03-10T12:00:00Z`
`/2010-03-10T12:00:00Z` - matches data before `2010-03-10T12:00:00Z`
`2000-01-01T10:00:00Z/P10Y2M10DT2H` - matches data between `2000-01-01T10:00:00Z` and a date 10 years 2 months 10 days and 2 hours after that or `2010-03-11T02:00:00Z`
`P1Y2M10DT2H30M/2008-05-11T15:30:00Z` - matches data between `2008-07-11T16:30:00Z` and a date 1 year 2 months 10 days 2 hours and 30 minutes before that or `2007-05-01T14:00:00Z`.
`2000-01-01T00:00:00.000Z,2023-01-31T23:59:59.999Z,1,31` - matches data between the Julian days `1` to `31` from `2000-01-01T00:00:00.000Z` to `2023-01-31T23:59:59.999Z`.

Note: ISO 8601 does not allow open-ended time intervals but the CMR API does allow specification of intervals which are open ended on one side. For example, `2000-01-01T10:00:00Z/` and `/2000-01-01T10:00:00Z` are valid ranges.

### <a name="autocomplete-facets"></a> Facet Autocompletion

Auto-completion assistance for building queries. This functionality may be used to help build queries. The facet autocomplete functionality does not search for collections directly. Instead it will return suggestions of facets to help narrow a search by providing a list of available facets to construct a CMR collections search.

    curl "%CMR-ENDPOINT%/autocomplete?q=<term>[&type\[\]=<type1>[&type\[\]=<type2>]"

Collection facet autocompletion results are paged. See [Paging Details](#paging-details) for more information on how to page through autocomplete search results.

#### Autocompletion of Science Keywords
In the case of science keywords, the `fields` property may be used to determine the hierarchy of the term. The structure of the `fields` field is a colon (`:`) separated list in the following sequence:

 * topic
 * term
 * variable-level-1
 * variable-level-2
 * variable-level-3
 * detailed-variable

There may be gaps within the structure where no associated value exists.

Example With variable-level-1 as the base term
```
{ :score 2.329206,
  :type "science_keywords",
  :value "Solar Irradiance",
  :fields "Sun-Earth Interactions:Solar Activity:Solar Irradiance"
}
```

Example with detailed-variable as the base term, note the extra colons preserving the structure
```
{ :score 1.234588,
  :type "science_keywords",
  :value "Coronal Mass Ejection",
  :fields "Sun-Earth Interactions:Solar Activity::::Coronal Mass Ejection
}
```

#### Autocompletion of Platforms
In the case of platforms, the `fields` property may be used to determine the hierarchy of the term. The structure of the `fields` field is a colon (`:`) separated list in the following sequence:

 * basis
 * category
 * sub-category
 * short-name

There may be gaps within the structure where no associated value exists.

Example With short-name as the base term
```
{ :score 2.329206,
  :type "platforms",
  :value "AEROS-1",
  :fields "Space-based Platforms:Earth Observation Satellites:Aeros:AEROS-1"
}
```

Example with short-name as the base term, but the sub-category is missing. Note the extra colons preserving the structure
```
{ :score 1.234588,
  :type "platforms",
  :value "Terra",
  :fields "Space-based Platforms:Earth Observation Satellites::Terra
}
```

#### Autocomplete Parameters
  * `q` The string on which to search. The term is case insensitive.
  * `type[]` Optional list of types to include in the search. This may be any number of valid facet types.

__Example Query__

     curl "%CMR-ENDPOINT%/autocomplete?q=ice"

__Example Result__
```
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
CMR-Hits: 15

{
  "feed": {
    "entry": [
      {
        "score": 9.115073,
        "type": "instrument",
        "fields": "ICE AUGERS",
        "value": "ICE AUGERS"
      },
      {
        "score": 9.115073,
        "type": "instrument",
        "fields": "ICECUBE",
        "value": "ICECUBE"
      },
      {
        "score": 9.021176,
        "type": "platforms",
        "fields": "Space-based Platforms:Earth Observation Satellites:Ice, Cloud and Land Elevation Satellite (ICESat):ICESat-2",
        "value": "ICESat-2"
      },
      {
        "score": 8.921176,
        "type": "science_keywords",
        "fields": "Atmosphere:Sun-Earth Interactions:Cloud Cover:Ice Reflectivity",
        "value": "Ice Reflectivity"
      }
    ]
  }
}
```

__Example Query__

     curl "%CMR-ENDPOINT%/autocomplete?q=ice&type[]=platform&type[]=project"

__Example Result with Type Filter__

```
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
CMR-Hits: 3

{
  "feed": {
    "entry": [
      {
        "score": 9.013778,
        "type": "platforms",
        "value": "ICESat-2",
        "fields": "Space-based Platforms:Earth Observation Satellites:Ice, Cloud and Land Elevation Satellite (ICESat):ICESat-2"
      },
      {
        "score": 8.921176,
        "type": "platforms",
        "value": "Sea Ice Mass Balance Station",
        "fields": "Water-based Platforms:Fixed Platforms:Surface:Sea Ice Mass Balance Station"
      },
      {
        "score": 8.921176,
        "type": "project",
        "value": "ICEYE"
      }
    ]
  }
}
```

### <a name="collection-search-by-parameters"></a> Collection Search Examples

#### <a name="find-all-collections"></a> Find all collections

    curl "%CMR-ENDPOINT%/collections"

Collection search results are paged. See [Paging Details](#paging-details) for more information on how to page through collection search results.

#### <a name="c-concept-id"></a> Find collections by concept id

A CMR concept id is in the format `<concept-type-prefix> <unique-number> "-" <provider-id>`

  * `concept-type-prefix` is a single capital letter prefix indicating the concept type. "C" is used for collections
  * `unique-number` is a single number assigned by the CMR during ingest.
  * `provider-id` is the short name for the provider. i.e. "LPDAAC\_ECS"

Example: `C123456-LPDAAC_ECS`

    curl "%CMR-ENDPOINT%/collections?concept_id\[\]=C123456-LPDAAC_ECS"

#### <a name="c-doi-value"></a> Find collections by doi value

  Find a collection matching a collection doi value. Note more than one doi value may be supplied.

    curl "%CMR-ENDPOINT%/collections?doi\[\]=doiValue"

#### <a name="c-echo-collection-id"></a> Find collections by echo collection id

  Find a collection matching a echo collection id. Note more than one echo collection id may be supplied.

     curl "%CMR-ENDPOINT%/collections?echo_collection_id\[\]=C1000000001-CMR_PROV2"

#### <a name="c-provider-short-name"></a> Find collections by provider short name

This searches for collections whose provider matches the given provider short names. This supports `ignore_case` option, but not the `pattern` option.

    curl "%CMR-ENDPOINT%/collections?provider_short_name\[\]=SHORT_5&options\[provider_short_name\]\[ignore_case\]=true"

#### <a name="c-entry-title"></a> Find collections by entry title

Find collections matching 'entry_title' param value such as DatasetId%204

    curl "%CMR-ENDPOINT%/collections?entry_title\[\]=DatasetId%204"

Find collections matching 'dataset_id' param (which is an alias for entry title) value

    curl "%CMR-ENDPOINT%/collections?dataset_id\[\]=DatasetId%204"

with multiple dataset ids

    curl "%CMR-ENDPOINT%/collections?entry_title\[\]=DatasetId%204&entry_title\[\]=DatasetId%205"

with a entry title case insensitively

    curl "%CMR-ENDPOINT%/collections?entry_title\[\]=datasetId%204&options\[entry_title\]\[ignore_case\]=true"

with a entry title pattern

    curl "%CMR-ENDPOINT%/collections?entry_title\[\]=DatasetId*&options\[entry_title\]\[pattern\]=true"

#### <a name="c-entry-id"></a> Find collections by entry id

Find collections matching 'entry_id' param value such as SHORT_V5

    curl "%CMR-ENDPOINT%/collections?entry_id\[\]=SHORT_V5"

#### <a name="c-archive-center"></a> Find collections by archive center

This supports `pattern` and `ignore_case`.

Find collections matching 'archive_center' param value

    curl "%CMR-ENDPOINT%/collections?archive_center\[\]=LARC"
    curl "%CMR-ENDPOINT%/collections?archive_center=Sedac+AC"

Find collections matching any of the 'archive_center' param values

     curl "%CMR-ENDPOINT%/collections?archive_center\[\]=Larc&archive_center\[\]=SEDAC"

#### <a name="c-data-center"></a> Find collections by data center

This supports `pattern`, `and`, and `ignore_case`.

Find collections matching 'data_center' param value

    curl "%CMR-ENDPOINT%/collections?data_center\[\]=LARC"
    curl "%CMR-ENDPOINT%/collections?data_center=Sedac+AC"

Find collections matching any of the 'data_center' param values

     curl "%CMR-ENDPOINT%/collections?data_center\[\]=Larc&data_center\[\]=SEDAC"

#### <a name="c-temporal"></a> Find collections with temporal

The temporal datetime has to be in yyyy-MM-ddTHH:mm:ssZ format.

    curl "%CMR-ENDPOINT%/collections?temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z,30,60&temporal\[\]=2000-01-01T10:00:00Z,,30&temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z"

The first two values of the parameter together define the temporal bounds. See under [Temporal Range searches](#temporal-range-searches) for different ways of specifying the temporal bounds including ISO 8601.

For temporal range search, the default is inclusive on the range boundaries. This can be changed by specifying `exclude_boundary` option with `options[temporal][exclude_boundary]=true`. This option has no impact on periodic temporal searches.

The collection's temporal range or the temporal range of the granules in the collection can be searched. `options[temporal][limit_to_granules]=true` will indicate that the temporal search should find collections based on the minimum and maximum values of each collection's granules' temporal range. If a collection does not have any granules it will search the collection's temporal range.

If a temporal range search is performed, the search results will be sorted by the temporal overlap across all ranges provided, with usage score being the tie-breaker. If a keyword search is performed in conjunction with the temporal range search, search results are first sorted by relevancy score, then by temporal overlap, then usage score. If a keyword search is used in conjunction with usage-score sort key, the usage-score will be used instead of relevancy score.

#### <a name="c-project"></a> Find collections by project

Note: An alias for the parameter 'project' is 'campaign'. As such 'campaign' can be used in place of 'project'.

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'project' param value

     curl "%CMR-ENDPOINT%/collections?project\[\]=ESI"

Find collections matching any of the 'project' param values

     curl "%CMR-ENDPOINT%/collections?project\[\]=ESI&project\[\]=EVI&project\[\]=EPI"

Find collections that match all of the 'project' param values

     curl "%CMR-ENDPOINT%/collections?project\[\]=ESI&project\[\]=EVI&project\[\]=EPI&options\[project\]\[and\]=true"

#### <a name="c-consortium"></a> Find collections by consortium

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'consortium' param value

     curl "%CMR-ENDPOINT%/collections?consortium\[\]=CWIC"

Find collections matching any of the 'consortium' param values

     curl "%CMR-ENDPOINT%/collections?consortium\[\]=CWIC&consortium\[\]=FEDEO&consortium\[\]=CEOS"

Find collections that match all of the 'consortium' param values

     curl "%CMR-ENDPOINT%/collections?consortium\[\]=CWIC&consortium\[\]=FEDEO&consortium\[\]=CEOS&options\[consortium\]\[and\]=true"

#### <a name="c-updated-since"></a> Find collections by updated_since

  Find collections which have revision date starting at or after 'updated_since' param value

     curl "%CMR-ENDPOINT%/collections?updated_since=2014-05-08T20:06:38.331Z"

#### <a name="c-created-at"></a> Find collections by created_at

 This supports option `and`.

 Find collections which were created within the ranges of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/collections?created_at\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z&created_at\[\]=2015-01-01T10:00:00Z,"

#### <a name="c-with-new-granules"></a> Find collections with new granules

  This supports option `and`.

  Find collections containing granules added within the range of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/collections?has_granules_created_at\[\]=2015-01-01T10:00:00Z,"

#### <a name="c-with-revised-granules"></a> Find collections with granules revised inside of a given range

  This supports option `and`.

  Find collections containing granules created or updated within the range of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/collections?has_granules_revised_at\[\]=2015-01-01T10:00:00Z,"

#### <a name="c-revision-date"></a> Find collections by revision_date

  This supports option `and`.

  Find collections which have revision date within the ranges of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/collections?revision_date\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z&revision_date\[\]=2015-01-01T10:00:00Z,"

#### <a name="c-processing-level-id"></a> Find collections by processing\_level\_id

This supports `pattern` and `ignore_case`.

Find collections matching 'processing_level_id'

     curl "%CMR-ENDPOINT%/collections?processing_level_id\[\]=1B"

Find collections matching any of the 'processing\_level\_id' param values

     curl "%CMR-ENDPOINT%/collections?processing_level_id\[\]=1B&processing_level_id\[\]=2B"

The alias 'processing_level' also works for searching by processing level id.

#### <a name="c-platform"></a> Find collections by platform

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'platform' param value

     curl "%CMR-ENDPOINT%/collections?platform\[\]=1B"

Find collections matching any of the 'platform' param values

     curl "%CMR-ENDPOINT%/collections?platform\[\]=1B&platform\[\]=2B"

#### <a name="c-instrument"></a> Find collections by instrument

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'instrument' param value

     curl "%CMR-ENDPOINT%/collections?instrument\[\]=1B"

Find collections matching any of the 'instrument' param values

     curl "%CMR-ENDPOINT%/collections?instrument\[\]=1B&instrument\[\]=2B"

#### <a name="c-sensor"></a> Find collections by sensor.

Sensor search is deprecated and should be replaced with instrument. Sensors are now child instruments on an instrument.

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'sensor' param value

     curl "%CMR-ENDPOINT%/collections?sensor\[\]=1B"

Find collections matching any of the 'sensor' param values

     curl "%CMR-ENDPOINT%/collections?sensor\[\]=1B&sensor\[\]=2B"

#### <a name="c-spatial-keyword"></a> Find collections by spatial\_keyword

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'spatial_keyword' param value

     curl "%CMR-ENDPOINT%/collections?spatial_keyword\[\]=DC"

Find collections matching any of the 'spatial_keyword' param values

     curl "%CMR-ENDPOINT%/collections?spatial_keyword\[\]=DC&spatial_keyword\[\]=LA"

#### <a name="c-science-keywords"></a> Find collections by science_keywords

This supports option _or_. Subfields of science_keywords search parameter include: category, topic, term, variable-level-1, variable-level-2, variable-level-3 and detailed-variable.

Find collections matching 'science_keywords' param value

     curl "%CMR-ENDPOINT%/collections?science_keywords\[0\]\[category\]=Cat1"

Find collections matching multiple 'science_keywords' param values, default is :and

     curl "%CMR-ENDPOINT%/collections?science_keywords\[0\]\[category\]=Cat1&science_keywords\[0\]\[topic\]=Topic1&science_keywords\[1\]\[category\]=Cat2"

#### <a name="c-twod-coordinate-system"></a> Find collections by two\_d\_coordinate\_system\_name

This supports searching by name of the two-dimensional tiling system for the collection. These are the valid values for two_d_coordinate_system aka TilingIdentificationSystem: `CALIPSO`, `MISR`, `MODIS Tile EASE`, `MODIS Tile SIN`, `WELD Alaska Tile`, `WELD CONUS Tile`, `WRS-1`, `WRS-2` and `Military Grid Reference System`.

This search parameter supports pattern. two\_d\_coordinate\_system\[name\] param is an alias of two\_d\_coordinate\_system\_name, but it does not support pattern.

  Find collections matching 'two\_d\_coordinate\_system\_name' param value

    curl "%CMR-ENDPOINT%/collections?two_d_coordinate_system_name\[\]=CALIPSO"

  Find collections matching any of the 'two\_d\_coordinate\_system\_name' param values

    curl "%CMR-ENDPOINT%/collections?two_d_coordinate_system_name\[\]=CALIPSO&two_d_coordinate_system_name\[\]=MISR"

#### <a name="c-collection-data-type"></a> Find collections by collection\_data\_type

Supports ignore_case and the following aliases for "NEAR\_REAL\_TIME": "near\_real\_time", "nrt", "NRT", "near real time", "near-real time", "near-real-time", "near real-time".

  Find collections matching 'collection\_data\_type' param value

     curl "%CMR-ENDPOINT%/collections?collection_data_type\[\]=NEAR_REAL_TIME"

  Find collections matching any of the 'collection\_data\_type' param values

     curl "%CMR-ENDPOINT%/collections?collection_data_type\[\]=NEAR_REAL_TIME&collection_data_type\[\]=OTHER"

#### <a name="c-granule-data-format"></a> Find collections by format of data in granules

   Find collections matching 'granule_data_format' param value

    curl "%CMR-ENDPOINT%/collections?granule_data_format=NetCDF"

#### <a name="c-online-only"></a> Find collections by online_only

Find collections matching 'online_only' param value, online_only is a legacy parameter and is an alias of downloadable.

    curl "%CMR-ENDPOINT%/collections?online_only=true"

#### <a name="c-downloadable"></a> Find collections by downloadable

A collection is downloadable when it contains at least one RelatedURL that is a DistributionURL of type GETDATA.

    curl "%CMR-ENDPOINT%/collections?downloadable=true"

#### <a name="c-browse-only"></a> Find collections by browse_only

`browse_only` is a legacy alias of `browsable`. They return the same search results.

    curl "%CMR-ENDPOINT%/collections?browse_only=true"

#### <a name="c-browsable"></a> Find collections by browsable

A collection is browsable when it contains at least one RelatedURL with a VisualizationURL URLContentType.

    curl "%CMR-ENDPOINT%/collections?browsable=true"

#### <a name="c-keyword"></a> Find collections by keyword (free text) search

Keyword searches are case insensitive and support wild cards ? and *, in which '\*' matches zero or more characters and '?' matches any single character. There is a limit of 30 wild cards allowed in keyword searches. Within 30 wild cards, there's also limit on the max keyword
string length. The longer the max keyword string length, the less number of keywords with wild cards allowed.

The following searches on "alpha", "beta" and "g?mma" individually and returns the collections that contain all these individual words
in the keyword fields that are indexed. Note: these words don't have to exist in the same keyword field, but they have to exist as a
space (or one of special character delimiter CMR uses) delimited word.

    curl "%CMR-ENDPOINT%/collections?keyword=alpha%20beta%20g?mma"

We also support keyword phrase search. The following searches on "alpha beta g?mma" as a phrase and returns the collections with
one or more indexed keyword field values that contain the phrase.

    curl "%CMR-ENDPOINT%/collections?keyword=\"alpha%20beta%20g?mma\""

Note: Currently we only support either keyword, or single keyword phrase search. We don't support mix of keyword and keyword phrase search and we don't support multiple keyword phrase search. These searches like the following will be rejected with error: <error>keyword phrase mixed with keyword, or another keyword-phrase are not supported. keyword phrase has to be enclosed by two escaped double quotes.</error>

   curl "%CMR-ENDPOINT%/collections?keyword=\"phrase%20one\"%20\"phrase%20two\"" (multiple phrase case)
   curl "%CMR-ENDPOINT%/collections?keyword=\"phrase%20one\"%20\word2" (mix of phrase and word case)
   curl "%CMR-ENDPOINT%/collections?keyword=\"phrase%20one" (missing one \" case)

Also \" is reserved for phrase boundary. For literal double quotes, use \\\". For example, to search for 'alpha "beta" g?mma' phrase, do the following:

    curl "%CMR-ENDPOINT%/collections?keyword=\"alpha%20\\\"beta\\\"%20g?mma\""

To search on 'alpha', '"beta"', 'g?mma' individually, do the following:

    curl "%CMR-ENDPOINT%/collections?keyword=alpha%20\\\"beta\\\"%20g?mma"

The following fields are indexed for keyword and keyword phrase search:

* Concept ID
* DOI value
* Provider ID
* Entry ID
* Entry title
* Data type
* Short name
* Summary
* Version ID
* Version description
* Processing level ID
* Science keywords
* Ancillary keywords
* Directory Names
* Archive centers
* Additional attribute names, data types, values, and descriptions
* detailed locations
* Spatial keywords
* Temporal keywords
* ISO Topic Categories
* Project short and long names
* Platform short and long names
* Instrument short names, long names, and techniques
* Sensor short names, long names, and techniques
* Characteristic names, descriptions and values
* TwoD coordinate system names
* Author

#### <a name="c-provider"></a> Find collections by provider

This parameter supports `pattern` and `ignore_case`.

Find collections matching 'provider' param value

    curl "%CMR-ENDPOINT%/collections?provider=ASF"

Find collections matching any of the 'provider' param values

    curl "%CMR-ENDPOINT%/collections?provider=ASF&provider=SEDAC"

#### <a name="c-native-id"></a> Find collections by native_id

This parameter supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'native_id' param value

    curl "%CMR-ENDPOINT%/collections?native_id=nativeid1"

Find collections matching any of the 'native_id' param values

    curl "%CMR-ENDPOINT%/collections?native_id[]=nativeid1&native_id[]=nativeid2"

#### <a name="c-short-name"></a> Find collections by short name

This parameter supports `pattern`, `ignore_case` and option `and`.

Find collections matching any of the 'short\_name' param values

    curl "%CMR-ENDPOINT%/collections?short_name=DEM_100M&short_name=MINIMAL"

Find collections matching 'short\_name' param value with a pattern

    curl "%CMR-ENDPOINT%/collections?short_name=D*&options[short_name][pattern]=true"

#### <a name="c-version"></a> Find collections by version

This parameter supports `pattern`, `ignore_case` and option `and`.

Find collections matching the given 'short\_name' and 'version' param values

    curl "%CMR-ENDPOINT%/collections?short_name=DEM_100M&version=1"

Find collections matching the given 'short\_name' and any of the 'version' param values

    curl "%CMR-ENDPOINT%/collections?short_name=dem_100m&version=1&version=2"

#### <a name="c-tag-parameters"></a> Find collections by tag parameters

Collections can be found by searching for associated tags. The following tag parameters are supported.

* tag_key
  supports options: `pattern`
* tag_originator_id
  supports options: `pattern`
* tag_data
  supports options: `pattern`

`exclude` parameter can be used with tag_key to exclude any collections that are associated with the specified tag key from the search result.

Find collections matching tag key.

    curl "%CMR-ENDPOINT%/collections?tag_key=org.ceos.wgiss.cwic.quality"

Find collections with exclude tag key.

    curl "%CMR-ENDPOINT%/collections?exclude\[tag_key\]=gov.nasa.earthdata.search.cwic"

Find collections with tag_data in the form of tag_data[tag_key]=tag_value. It finds collections match on both tag_key and tag_value, which is the string data that is associated with the collection during tag association.

    curl "%CMR-ENDPOINT%/collections?tag_data[org.ceos.wgiss.cwic.quality]=foo"

#### <a name="c-variable-parameters"></a> Find collections by variable parameters

Collections can be found by searching for associated variables. The following variable parameters are supported.

* variable_name
  supports options: `pattern`, `ignore_case` and `and`
* variable_native_id
  supports options: `pattern`, `ignore_case` and `and`
* variable_concept_id
  supports options: `and`

Find collections matching variable name.

    curl "%CMR-ENDPOINT%/collections?variable_name=totcldh2ostderr"

Find collections matching variable native id.

    curl "%CMR-ENDPOINT%/collections?variable_native_id\[\]=var1&variable_native_id\[\]=var2"

Find collections matching variable concept id.

    curl "%CMR-ENDPOINT%/collections?variable_concept_id\[\]=V100000-PROV1"

#### <a name="c-variables"></a> Find collections by hierarchical variables

This supports option _or_.

Find collections matching 'variables-h' param value

     curl "%CMR-ENDPOINT%/collections?variables-h\[0\]\[measurement\]=M1"

Find collections matching multiple 'variables-h' param values, default is :and

     curl "%CMR-ENDPOINT%/collections?variables-h\[0\]\[measurement\]=M1&variables-h\[0\]\[variable\]=Var1&variables-h\[1\]\[measurement\]=M2"

#### <a name="c-service-parameters"></a> Find collections by service parameters

Collections can be found by searching for associated services. The following service parameters are supported.

* service_name
  supports options: `pattern`, `ignore_case` and `and`
* service_type
  supports options: `pattern` and `ignore_case`
* service_concept_id
  supports options: `and`

Find collections matching service name.

    curl "%CMR-ENDPOINT%/collections?service_name=AtlasNorth"

Find collections matching service type. In this example, find all collections matching service type of Harmony or OPeNDAP.

    curl "%CMR-ENDPOINT%/collections?service_type\[\]=Harmony&service_type\[\]=OPeNDAP"

Find collections matching service concept id.

    curl "%CMR-ENDPOINT%/collections?service_concept_id\[\]=S100000-PROV1&service_concept_id\[\]=S12345-PROV1"

#### <a name="c-tool-parameters"></a> Find collections by tool parameters

Collections can be found by searching for associated tools. The following tool parameters are supported.

* tool_name
  supports options: `pattern`, `ignore_case` and `and`
* tool_type
  supports options: `pattern` and `ignore_case`
* tool_concept_id
  supports options: `and`

Find collections matching tool name.

    curl "%CMR-ENDPOINT%/collections?tool_name=NASA_GISS_Panoply"

Find collections matching tool type. In this example, find all collections matching tool type of Downloadable Tool or Web User Interface.

    curl "%CMR-ENDPOINT%/collections?tool_type\[\]=Downloadable%20Tool&tool_type\[\]=Web%20User%20Interface"

Find collections matching tool concept id.

    curl "%CMR-ENDPOINT%/collections?tool_concept_id\[\]=TL100000-PROV1&tool_concept_id\[\]=TL12345-PROV1"

#### <a name="c-spatial"></a> Find collections by Spatial

##### <a name="c-polygon"></a> Polygon

Polygon points are provided in counter-clockwise order. The last point should match the first point to close the polygon. The values are listed comma separated in longitude latitude order, i.e. lon1, lat1, lon2, lat2, lon3, lat3, and so on.

The polygon parameter could be either "polygon", for single polygon search:

   curl "%CMR-ENDPOINT%/collections?polygon=10,10,30,10,30,20,10,20,10,10"

or "polygon[]", for single or multiple polygon search. It supports the and/or option as shown below. Default option is "and", i.e. it will match both the first polygon and the second polygon.

    curl "%CMR-ENDPOINT%/collections?polygon[]=10,10,30,10,30,20,10,20,10,10"

    curl "%CMR-ENDPOINT%/collections?polygon[]=10,10,30,10,30,20,10,20,10,10&polygon[]=11,11,31,11,31,21,11,21,11,11"

    curl "%CMR-ENDPOINT%/collections?polygon[]=10,10,30,10,30,20,10,20,10,10&polygon[]=11,11,31,11,31,21,11,21,11,11&options[polygon][or]=true"

Note: if you use "polygon" for multiple polygon search, it won't work because only the last polygon parameter will take effect.

##### <a name="c-bounding-box"></a> Bounding Box

Bounding boxes define an area on the earth aligned with longitude and latitude. The Bounding box parameters must be 4 comma-separated numbers: lower left longitude, lower left latitude, upper right longitude, upper right latitude.
This parameter supports the and/or option as shown below.

    curl "%CMR-ENDPOINT%/collections?bounding_box[]=-10,-5,10,5"

    curl "%CMR-ENDPOINT%/collections?bounding_box[]=-10,-5,10,5&bounding_box[]=-11,-6,11,6&options[bounding_box][or]=true"

##### <a name="c-point"></a> Point

Search using a point involves using a pair of values representing the point coordinates as parameters. The first value is the longitude and second value is the latitude. This parameter supports the and/or option as shown below.

    curl "%CMR-ENDPOINT%/collections?point=100,20"

    curl "%CMR-ENDPOINT%/collections?point=100,20&point=80,20&options[point][or]=true"

##### <a name="c-line"></a> Line

Lines are provided as a list of comma separated values representing coordinates of points along the line. The coordinates are listed in the format lon1, lat1, lon2, lat2, lon3, lat3, and so on. This parameter supports the and/or option as shown below.

    curl "%CMR-ENDPOINT%/collections?line[]=-0.37,-14.07,4.75,1.27,25.13,-15.51"

    curl "%CMR-ENDPOINT%/collections?line[]=-0.37,-14.07,4.75,1.27,25.13,-15.51&line[]=-1.37,-15.07,5.75,2.27,26.13,-16.51&options[line][or]=true"

##### <a name="c-circle"></a> Circle

Circle defines a circle area on the earth with a center point and a radius. The center parameters must be 3 comma-separated numbers: longitude of the center point, latitude of the center point, radius of the circle in meters. The circle center cannot be on North or South pole. The radius of the circle must be between 10 and 6,000,000.
This parameter supports the and/or option as shown below.

    curl "%CMR-ENDPOINT%/collections?circle[]=-87.629717,41.878112,1000"

    curl "%CMR-ENDPOINT%/collections?circle[]=-87.629717,41.878112,1000&circle[]=-75,41.878112,1000&options[circle][or]=true"

Note: A query could consist of multiple spatial parameters of different types, two bounding boxes and a polygon for example. If multiple spatial parameters are present, all the parameters irrespective of their type are ANDed in a query. So, if a query contains two bounding boxes and a polygon for example, it will return only those collections which intersect both the bounding boxes and the polygon.

#### <a name="c-shapefile"></a> Find collections by shapefile

A shapefile can be uploaded with a query to restrict results to those that overlap the geometry in the shapefile. Note that unlike the spatial parameters, geometry in the shapefile is ORed together, not ANDed. So if a collection overlaps _any_ of the geometry in the shapefile it will match. Note also that the `shapefile` parameter supports shapefiles containing polygons with holes.

Currently the only supported shapefile formats are ESRI, KML, and GeoJSON. For ESRI all the sub-files (*.shp, *.shx, etc.) must be uploaded in a single zip file.

The following limits apply to uploaded shapefiles:
* Shapefiles are limited in size to 1,000,000 bytes.
* Shapefiles are limited to 500 features
* Shapefiles are limited to 5000 points.
* Shapefile geometries with precision greater than 7 significant digits should ensure their points are at least 1 meter apart.

Regarding polygon ring winding, ESRI shapefiles **must** follow the ESRI standard, i.e., exterior (boundary) rings are clockwise, and holes are counter-clockwise. GeoJSON **must** follow the RFC7946 specification, i.e., exterior rings are counterclockwise, and holes are clockwise. KML **must** follow the KML 2.2 specification, i.e., _all_ polygon rings are counter-clockwise.

Shapefile upload is only supported using POST with `multipart/form-data` and the mime type for the shapefile must be given as `application/shapefile+zip`, `application/geo+json`, or `application/vnd.google-earth.kml+xml`.

Examples:

**ESRI Shapefile**

    curl -XPOST "%CMR-ENDPOINT%/collections" -F "shapefile=@box.zip;type=application/shapefile+zip" -F "provider=PROV1"

**GeoJSON**

    curl -XPOST "%CMR-ENDPOINT%/collections" -F "shapefile=@box.geojson;type=application/geo+json" -F "provider=PROV1"

**KML**

    curl -XPOST "%CMR-ENDPOINT%/collections" -F "shapefile=@box.kml;type=application/vnd.google-earth.kml+xml" -F "provider=PROV1"

Internally a WGS 84 Coordinate Reference System (CRS) is used. The system will attempt to transform shapefile geometry that uses a different CRS, but this is not guaranteed to work and the request will be rejected if a suitable transformation is not found.

**NOTE:** This is an experimental feature and may not be enabled in all environments.

#### <a name="c-shapefile-simplification"></a> Simplifying shapefiles during collection search
Shapefiles are limited to 5000 points by default. A user using a shapefile with more points than the CMR supported limit can use the `simplify-shapefile` parameter to request that the CMR try to simplify (reduce the number of points) the shapefile so that it is under the limit.

Example:

    curl -XPOST "%CMR-ENDPOINT%/collections" -F "simplify-shapefile=true"  -F "shapefile=@africa.zip;type=application/shapefile+zip" -F "provider=PROV1"

Note that the simplification process attempts to preserve topology, i.e., the relationship between polygon outer boundaries and holes. The process uses the [Douglas-Peucker algorithm](https://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm) and as such may result in geometries with less coverage than the original shapefile and potentially a loss of matching results.

The simplification may fail if the process cannot reduce the number of points in the file to below the limit. Also the simplification only reduces the number of points in the file, so a shapefile will still fail if the file size is too large or there are too many features.

#### <a name="c-additional-attribute"></a> Find collections by additional attribute

Find an additional attribute with name "PERCENTAGE" only

    curl "%CMR-ENDPOINT%/collections?attribute\[\]=PERCENTAGE"

Find an additional attribute with name "PERCENTAGE" of type float with value 25.5

    curl "%CMR-ENDPOINT%/collections?attribute\[\]=float,PERCENTAGE,25.5"

Find an additional attribute with name "PERCENTAGE" of type float in range 25.5 - 30.

    curl "%CMR-ENDPOINT%/collections?attribute\[\]=float,PERCENTAGE,25.5,30"

Find an additional attribute with name "PERCENTAGE" of type float with min value 25.5.

    curl "%CMR-ENDPOINT%/collections?attribute\[\]=float,PERCENTAGE,25.5,"

Find an additional attribute with name "PERCENTAGE" of type float with max value 30.

    curl "%CMR-ENDPOINT%/collections?attribute\[\]=float,PERCENTAGE,,30"

Find an additional attribute with name "X\Y\Z" with value 7.

    curl "%CMR-ENDPOINT%/collections?attribute\[\]=float,X\Y\Z,7"

Find an additional attribute with name "MISSION_NAME" with value "Big Island, HI".

    curl "%CMR-ENDPOINT%/collections?attribute\[\]=string,MISSION_NAME,Big Island\, HI"

Multiple attributes can be provided. The default is for collections to match all the attribute parameters. This can be changed by specifying `or` option with `options[attribute][or]=true`.

For additional attribute range search, the default is inclusive on the range boundaries. This can be changed by specifying `exclude_boundary` option with `options[attribute][exclude_boundary]=true`.

#### <a name="c-author"></a> Find collections by author

This parameter supports `pattern`, `ignore_case` and option `and`.

Find collections matching the given 'author' values

    curl "%CMR-ENDPOINT%/collections?author=*JPL*&options[author][pattern]=true"

#### <a name="c-has-granules"></a> Find collections with or without granules

When `has_granules` is set to "true" or "false", results will be restricted to collections with or without granules, respectively.

    curl "%CMR-ENDPOINT%/collections?has_granules=true"

#### <a name="c-has-granules-or-cwic"></a> Find collections with or without granules, or the collection with CWIC consortium.

The `has_granules_or_cwic` parameter can be set to "true" or "false". When true, the results will be restricted to collections with granules or with CWIC consortium. When false, will return any collections without granules.

    curl "%CMR-ENDPOINT%/collections?has_granules_or_cwic=true"

**Note:** this parameter will soon be retired in favor of a replacement parameter found below, `have_granules_or_opensearch`.

#### <a name="c-has-granules-or-opensearch"></a> Find collections with or without granules, or the collection is tagged with the configured OpenSearch tag.

The `has_granules_or_opensearch` parameter can be set to "true" or "false". When true, the results will be restricted to collections with granules or with any of the configured OpenSearch consortiums, which are CWIC,FEDEO,GEOSS,CEOS and EOSDIS. When false, will return any collections without granules.

    curl "%CMR-ENDPOINT%/collections?has_granules_or_opensearch=true"

#### <a name="c-has-opendap-url"></a> Find collections with or without an OPeNDAP service RelatedURL.

    curl "%CMR-ENDPOINT%/collections?has_opendap_url=true"

#### <a name="c-cloud-hosted"></a> Find collections with data that is hosted in the cloud.

The `cloud_hosted` parameter can be set to "true" or "false". When true, the results will be restricted to collections that have a `DirectDistributionInformation` element or have been tagged with `gov.nasa.earthdatacloud.s3`.

    curl "%CMR-ENDPOINT%/collections?cloud_hosted=true"

#### <a name="c-standard-product"></a> Find collections that are standard products.

The `standard_product` parameter can be set to "true" or "false". When true, the results will be restricted to collections that have `StandardProduct` element being true or collections that don't have `StandardProduct` element set and have been tagged with `gov.nasa.eosdis.standardproduct`.

    curl "%CMR-ENDPOINT%/collections?standard_product=true"

#### <a name="sorting-collection-results"></a> Sorting Collection Results

Collection results are sorted by ascending entry title by default when a search does not result in a score.

If a keyword search is performed then the search results will be sorted by:

  * Relevance Score (descending), binned to the nearest 0.2. For example a score of 0.75 and 0.85 will be considered equal for sorting purposes.
  * Temporal Overlap (descending), if one or more temporal ranges are provided.
  * EMS Community Usage Score (descending), binned to the nearest 400. For example, usage of 400 and 500 will be considered equal for sorting purposes. The usage score comes from EMS metrics which contain access counts of the collections by short name and version. The metrics are ingested into the CMR.
  * Collection End Date (descending), with ongoing collections defaulting to today.
  * Humanized processing level Id (descending)

If a temporal range search is performed, the search results will be sorted by temporal overlap percentage over all ranges provided.

One or more sort keys can be specified using the `sort_key[]` parameter. The order used impacts searching. Fields can be prepended with a `-` to sort in descending order. Ascending order is the default but `+` (Note: `+` must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Collection Sort Keys

  * `entry_title`
  * `dataset_id` - alias for entry_title
  * `short_name`
  * `entry_id`
  * `start_date`
  * `end_date`
  * `platform`
  * `instrument`
  * `sensor`
  * `provider`
  * `revision_date`
  * `score` - document relevance score, defaults to descending. See [Document Scoring](#document-scoring).
  * `has_granules` - Sorts collections by whether they have granules or not. Collections with granules are sorted before collections without granules.
  * `has_granules_or_cwic` - Sorts collections by whether they have granules or CWIC consortium. Collections with granules or CWIC consortium are sorted before collections without granules or a CWIC consortium.
  * `usage_score` - Sorts collection by usage. The usage score comes from the EMS metrics, which are ingested into the CMR.
  * `ongoing` - Sorts collection by fuzzy collection end-date in relation to ongoing-days configured. Any end-date after today, minus the configured ongoing-days (30 by default), is considered ongoing. Any end-date before that is not ongoing.

Examples of sorting by start_date in descending(Most recent data first) and ascending orders(Note: the `+` must be escaped with %2B):

    curl "%CMR-ENDPOINT%/collections?sort_key\[\]=-start_date"
    curl "%CMR-ENDPOINT%/collections?sort_key\[\]=%2Bstart_date"

#### <a name="retrieving-all-revisions-of-a-collection"></a> Retrieving All Revisions of a Collection

In addition to retrieving the latest revision for a collection parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisions, by passing in `all_revisions=true` with the URL parameters. The reference and UMM JSON response formats are supported for all revision searches. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true".

    curl "%CMR-ENDPOINT%/collections?provider=PROV1&all_revisions=true&pretty=true"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>3</hits>
        <took>5</took>
        <references>
            <reference>
                <name>et1</name>
                <id>C1200000000-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/C1200000000-PROV1/3</location>
                <revision-id>3</revision-id>
            </reference>
            <reference>
                <name>et1</name>
                <id>C1200000000-PROV1</id>
                <revision-id>2</revision-id>
                <deleted>true</deleted>
            </reference>
            <reference>
                <name>et1</name>
                <id>C1200000000-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/C1200000000-PROV1/1</location>
                <revision-id>1</revision-id>
            </reference>
        </references>
    </results>
```

### <a name="granule-search-by-parameters"></a> Granule Search By Parameters
**Note:** The CMR does not permit queries across all granules in all collections in order to provide fast search responses. Granule queries must target a subset of the collections in the CMR using a condition like provider, provider_id, concept_id, collection_concept_id, short_name, version or entry_title.

#### <a name="find-all-granules"></a> Find all granules for a collection.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%"


Granule search results are paged. See [Paging Details](#paging-details) for more information on how to page through granule search results.

#### <a name="g-granule-ur"></a> Find granules with a granule-ur

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&granule_ur\[\]=DummyGranuleUR"

#### <a name="g-producer-granule-id"></a> Find granules with a producer granule id

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&producer_granule_id\[\]=DummyID"

#### <a name="g-granule-ur-or-producer-granule-id"></a> Find granules matching either granule ur or producer granule id

This condition is encapsulated in a single parameter called readable_granule_name

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&readable_granule_name\[\]=DummyID"

#### <a name="g-online-only"></a> Find granules by online_only

The online_only parameter is a legacy parameter and is an alias of downloadable.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&online_only=true"

#### <a name="g-downloadable"></a> Find granules by downloadable

A granule is downloadable when it contains at least one RelatedURL of type GETDATA.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&downloadable=true"

#### <a name="g-browsable"></a> Find granules by browsable

A granule is browsable when it contains at least one RelatedURL of type GET RELATED VISUALIZATION.

    curl "%CMR-ENDPOINT%/granules?browsable=true&provider=PROV1"

#### <a name="g-additional-attribute"></a> Find granules by additional attribute

Find an additional attribute with name "PERCENTAGE" only

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&attribute\[\]=PERCENTAGE"

Find an additional attribute with name "PERCENTAGE" of type float with value 25.5

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&attribute\[\]=float,PERCENTAGE,25.5"

Find an additional attribute with name "PERCENTAGE" of type float in range 25.5 - 30.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&attribute\[\]=float,PERCENTAGE,25.5,30"

Find an additional attribute with name "PERCENTAGE" of type float with min value 25.5.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&attribute\[\]=float,PERCENTAGE,25.5,"

Find an additional attribute with name "PERCENTAGE" of type float with max value 30.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&attribute\[\]=float,PERCENTAGE,,30"

Find an additional attribute with name "X,Y,Z" with value 7.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&attribute\[\]=float,X\,Y\,Z,7"

Find an additional attribute with name "X\Y\Z" with value 7.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&attribute\[\]=float,X\Y\Z,7"

Find an additional attribute with name "MISSION_NAME" with value "Big Island, HI".

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&attribute\[\]=string,MISSION_NAME,Big Island\, HI"

Multiple attributes can be provided. The default is for granules to match all the attribute parameters. This can be changed by specifying `or` option with `options[attribute][or]=true`.

For additional attribute range search, the default is inclusive on the range boundaries. This can be changed by specifying `exclude_boundary` option with `options[attribute][exclude_boundary]=true`.

For granule additional attributes search, the default is searching for the attributes included in the collection this granule belongs to as well. This can be changed by specifying `exclude_collection` option with `options[attribute][exclude_collection]=true`.

#### <a name="g-spatial"></a> Find granules by Spatial
The parameters used for searching granules by spatial are the same as the spatial parameters used in collections searches. (See under "Find collections by Spatial" for more details.)
Note: When querying a granule which has multiple types of spatial features in the granule metadata (i.e. a Polygon and a Bounding Box), the granule will be returned if the spatial query matches at least one of the spatial types on the given granule (i.e. matches the granule's Polygon OR Bounding Box).

##### <a name="g-polygon"></a> Polygon
Polygon points are provided in counter-clockwise order. The last point should match the first point to close the polygon. The values are listed comma separated in longitude latitude order, i.e. lon1, lat1, lon2, lat2, lon3, lat3, and so on.

The polygon parameter could be either "polygon", for single polygon search:

   curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&polygon=10,10,30,10,30,20,10,20,10,10"

or "polygon[]", for single or multiple polygon search. It supports the and/or option as shown below. Default option is "and", i.e. it will match both the first polygon and the second polygon.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&polygon[]=10,10,30,10,30,20,10,20,10,10"

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&polygon[]=10,10,30,10,30,20,10,20,10,10&polygon[]=11,11,31,11,31,21,11,21,11,11"

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&polygon[]=10,10,30,10,30,20,10,20,10,10&polygon[]=11,11,31,11,31,21,11,21,11,11&options[polygon][or]=true"

Note: if you use "polygon" for multiple polygon search, it won't work because only the last polygon parameter will take effect.

##### <a name="g-bounding-box"></a> Bounding Box

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&bounding_box=-10,-5,10,5"

##### <a name="g-point"></a> Point

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&point=100,20"

##### <a name="g-line"></a> Line

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&line=-0.37,-14.07,4.75,1.27,25.13,-15.51"

##### <a name="g-circle"></a> Circle

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&circle=-87.629717,41.878112,1000"

#### <a name="g-shapefile"></a> Find granules by shapefile

As with collections, a shapefile can be uploaded to find granules that overlap the shapefile's geometry. See [Find collections by shapefile](#c-shapefile) for more details.

    curl -XPOST "%CMR-ENDPOINT%/granules" -F "shapefile=@box.zip;type=application/shapefile+zip" -F "provider=PROV1"

**NOTE**: This is an experimental feature and may not be enabled in all environments.

#### <a name="g-shapefile-simplification"></a> Simplifying shapefiles during granule search

As with collections, an uploaded shapefile can be simplified by setting the `simplify-shapefile` parameter to `true`. See [Simplifying shapefiles during collection search](#c-shapefile-simplification) for more details.

    curl -XPOST "%CMR-ENDPOINT%/granules" -F "simplify-shapefile=true" -F "shapefile=@africa.zip;type=application/shapefile+zip" -F "provider=PROV1"

#### <a name="g-orbit-number"></a> Find granules by orbit number

  Find granules with an orbit number of 10

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&orbit_number=10"

Find granules with an orbit number in a range of 0.5 to 1.5

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&orbit_number=0.5,1.5"

#### <a name="g-orbit-equator-crossing-longitude"></a> Find granules by orbit equator crossing longitude

Find granules with an exact equator crossing longitude of 90

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&equator_crossing_longitude=90"

Find granules with an orbit equator crossing longitude in the range of 0 to 10

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&equator_crossing_longitude=0,10"

Find granules with an equator crossing longitude in the range from 170 to -170
  (across the anti-meridian)

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&equator_crossing_longitude=170,-170"

#### <a name="g-orbit-equator-crossing-date"></a> Find granules by orbit equator crossing date

Find granules with an orbit equator crossing date in the range of 2000-01-01T10:00:00Z to 2010-03-10T12:00:00Z

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&equator_crossing_date=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z"

The time interval in equator crossing date range searches can be specified in different ways including ISO 8601. See under [Temporal Range searches](#temporal-range-searches).

#### <a name="g-updated-since"></a> Find granules by updated_since

Find granules which have revision date starting at or after 'updated_since' param value

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&updated_since=2014-05-08T20:12:35Z"

#### <a name="g-revision-date"></a> Find granules by revision_date

This supports option `and`.

Find granules which have revision date within the ranges of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&revision_date\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z&revision_date\[\]=2015-01-01T10:00:00Z,"

#### <a name="g-created-at"></a> Find granules by created_at

 This supports option `and`.

 Find granules which were created within the ranges of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&created_at\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z&created_at\[\]=2015-01-01T10:00:00Z,"

#### <a name="g-production-date"></a> Find granules by production_date

This supports option `and`.

Find granules which have production date within the ranges of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&production_date\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z&production_date\[\]=2015-01-01T10:00:00Z,"

#### <a name="g-cloud-cover"></a> Find granules by cloud_cover

Find granules with just the min cloud cover value set to 0.2

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&cloud_cover=0.2,"

Find granules with just the max cloud cover value set to 30

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&cloud_cover=,30"

Find granules with cloud cover numeric range set to min: -70.0 max: 120.0

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&cloud_cover=-70.0,120.0"

#### <a name="g-platform"></a> Find granules by platform

This supports `pattern`, `ignore_case`, `exclude_collection` and option `and`. The default behavior is that granules without platform values inherit their parent collection's platform.   This can be changed by specifying `exclude_collection` option with `options[platform][exclude_collection]=true`.

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&platform\[\]=1B"

#### <a name="g-instrument"></a> Find granules by instrument

This supports `pattern`, `ignore_case`, `exclude_collection` and option `and`. The default behavior is that granules without instrument values inherit their parent collection's instrument.   This can be changed by specifying `exclude_collection` option with `options[instrument][exclude_collection]=true`.

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&instrument\[\]=1B"

#### <a name="g-sensor"></a> Find granules by sensor param

This supports `pattern`, `ignore_case`, `exclude_collection` and option `and`. The default behavior is that granules without sensor values inherit their parent collection's sensor.   This can be changed by specifying `exclude_collection` option with `options[sensor][exclude_collection]=true`.

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&sensor\[\]=1B"

#### <a name="g-project"></a> Find granules by project

Note: An alias for the parameter 'project' is 'campaign'. As such 'campaign' can be used in place of 'project'.

This supports `pattern`, `ignore_case` and option `and`.

Find granules matching 'project' param value

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&project\[\]=2009_GR_NASA"

Find granules matching any of the 'project' param values

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&project\[\]=2009_GR_NASA&project\[\]=2013_GR_NASA"

Find granules matching the given pattern for the 'project' param value

```
curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&project\[\]=20??_GR_NASA&options\[project\]\[pattern\]=true"
```

Find granules that match all of the 'project' param values

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&project\[\]=2009_GR_NASA&project\[\]=2013_GR_NASA&options\[project\]\[and\]=true"

#### <a name="g-concept-id"></a> Find granules by concept id

Note: more than one may be supplied

Find granule by concept id

     curl "%CMR-ENDPOINT%/granules?provider=PROV1&concept_id\[\]=G1000000002-CMR_PROV1"

Find granule by echo granule id

     curl "%CMR-ENDPOINT%/granules?provider=PROV1&echo_granule_id\[\]=G1000000002-CMR_PROV1"

Find granules by parent concept id. `concept_id` or `collection_concept_id` can be used interchangeably.

     curl "%CMR-ENDPOINT%/granules?concept_id=%CMR-EXAMPLE-COLLECTION-ID%"
     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%"

Find granules by echo collection id

     curl "%CMR-ENDPOINT%/granules?echo_collection_id=%CMR-EXAMPLE-COLLECTION-ID%"

#### <a name="g-day-night-flag"></a> Find granules by day\_night\_flag param, supports pattern and ignore_case

```
curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&day_night_flag=night

curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&day_night_flag=day

curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&day_night=unspecified
```

#### <a name="g-twod-coordinate-system"></a> Find granules by two\_d\_coordinate\_system parameter.

Note: An alias for the parameter 'two_d_coordinate_system' is 'grid'. As such 'grid' can be used in place of 'two_d_coordinate_system'.

```
  curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&two_d_coordinate_system\[\]=wrs-1:5,10:8-10,0-10:8,12
```

The parameter expects a coordinate system name and a set of two-d coordinates. The two-d coordinates could be represented either by a single coordinate pair or a pair of coordinate ranges. ':' is used as the separator between the coordinate system name, single coordinate pairs and coordinate range pairs. The coordinates in the single coordinate pair are represented in the format "x,y". And the coordinates in the coordinate range pairs are represented in the format "x1-x2,y1-y2" where x1 and x2 are the bounds of the values for the first coordinate and y1 and y2, for the second coordinate. One can also use single values for each of the two ranges, say "x1" instead of "x1-x2", in which case the upper and lower bound are considered the same. In other words using "x1" for range is equivalent to using "x1-x1". A single query can consist of a combination of individual coordinate pairs and coordinate range pairs. For example, the query above indicates that the user wants to search for granules which have a two\_d\_coordinate\_system whose name is wrs-1 and whose two-d coordinates match(or fall within) at least one of the given pairs: a single coordinate pair (5,10), a range coordinate pair 8-10,0-10 and another single coordinate pair (8,12).

#### <a name="g-provider"></a> Find granules by provider

This parameter supports `pattern`, `ignore_case` and option `and`.

Find granules matching 'provider' param value

    curl "%CMR-ENDPOINT%/granules?provider=ASF"

Find granules matching any of the 'provider' param values

    curl "%CMR-ENDPOINT%/granules?provider=ASF&provider=SEDAC"

#### <a name="g-native-id"></a> Find granules by native_id

This parameter supports `pattern`, `ignore_case` and option `and`.

Find granules matching 'native_id' param value

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&native_id=nativeid1"

Find granules matching any of the 'native_id' param values

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&native_id[]=nativeid1&native_id[]=nativeid2"

#### <a name="g-short-name"></a> Find granules by short name

This parameter supports `pattern`, `ignore_case` and option `and`.

Find granules matching any of the 'short\_name' param values. The 'short\_name' here refers to the short name of the collections corresponding to the granules being searched for.

    curl "%CMR-ENDPOINT%/granules?short_name=DEM_100M&short_name=MINIMAL"

Find granules matching 'short\_name' param value with a pattern.

    curl "%CMR-ENDPOINT%/granules?short_name=D*&options[short_name][pattern]=true"

#### <a name="g-version"></a> Find granules by parent collection version

This parameter supports `pattern`, `ignore_case` and option `and`.

Find granules matching the 'short\_name' and 'version' param values. The 'short\_name' and 'version' here refers to the short name and version of the collections corresponding to the granules being searched for.

    curl "%CMR-ENDPOINT%/granules?short_name=DEM_100M&version=1"

Find granules matching the given 'short_name' and any of the 'version' param values

    curl "%CMR-ENDPOINT%/granules?short_name=DEM_100M&version=1&version=2"

#### <a name="g-entry-title"></a> Find granules by parent collection entry title

This parameter supports `pattern`, `ignore_case` and option `and`.

Find granules matching 'entry\_title' param value. The 'entry\_title' here refers to the entry title of the collections corresponding to the granules being searched for.

    curl "%CMR-ENDPOINT%/granules?entry_title=DatasetId%204"

See under "Find collections by entry title" for more examples of how to use this parameter.

#### <a name="g-entry-id"></a> Find granules by parent collection entry id

Find granules matching 'entry\_id' param value where 'entry\_id' refers to the granule's parent collection. 'entry\_id' is the concatenation of short name, an underscore, and version of the corresponding collection.

    curl "%CMR-ENDPOINT%/granules?entry_id\[\]=SHORT_V5"

Multiple collections may be specified.

    curl "%CMR-ENDPOINT%/granules?entry_id\[\]=MYCOLLECTION_V1&entry_id\[\]=OTHERCOLLECTION_V2"

See under "Find collections by entry id" for more examples of how to use this parameter.

#### <a name="g-temporal"></a> Find granules with temporal

The temporal datetime has to be in yyyy-MM-ddTHH:mm:ssZ format.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z,30,60&temporal\[\]=2000-01-01T10:00:00Z,,30&temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z"

The first two values of the parameter together define the temporal bounds. See under [Temporal Range searches](#temporal-range-searches) for different ways of specifying the temporal bounds including ISO 8601.

For temporal range search, the default is inclusive on the range boundaries. This can be changed by specifying `exclude_boundary` option with `options[temporal][exclude_boundary]=true`. This option has no impact on periodic temporal searches.

#### <a name="g-cycle"></a> Find granules by cycle

Cycle is part of the track information of the granule. Track information is used to allow a user to search for granules whose spatial extent is based on an orbital cycle, pass, and tile mapping. Cycle must be a positive integer.

User can search granules by one or more cycles. e.g.

    curl -g "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&cycle[]=1&cycle[]=2"

User can only search granules by exactly one cycle value when there are passes parameters in the search.

#### <a name="g-passes"></a> Find granules by passes

Passes is part of the track information of the granule as specified in [UMM-G Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/unified-metadata-model/browse/granule). Track information is used to allow a user to search for granules whose spatial extent is based on an orbital cycle, pass, and tile mapping. Cycles and passes must be positive integers, tiles are in the format of an integer followed by L, R or F. e.g. 2L.

User can search granules by pass and tiles in a nested object called passes. Multiple passes can be specified via different indexes to search granules. There must be one and only one cycle parameter value present in the search params when searching granules with passes. Each `passes` parameter must have one and only one `pass` value. Pass and tiles within a `passes` parameter are ANDed together. Multiple passes are ORed together by default, but can be AND together through the AND options, i.e. `options[passes][AND]=true`. The following example searches for granules with orbit track info that has cycle 1, tiles cover 1L or 2F within pass 1, or 3R within pass 2.

    curl -g "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&cycle[]=1&passes[0][pass]=1&passes[0][tiles]=1L,2F&passes[1][pass]=2&passes[1][tiles]=3R"

The following example searches for granules with orbit track info that has cycle 1, tiles cover 1L and 2F within pass 1, and 3R within pass 2.

    curl -g "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&cycle[]=1&passes[0][pass]=1&passes[0][tiles]=1L&passes[1][pass]=1&passes[1][tiles]=2F&passes[2][pass]=2&passes[2][tiles]=3R&options[passes][AND]=true"

#### <a name="g-exclude-by-id"></a> Exclude granules from elastic results by echo granule id and concept ids.

Note: more than one id may be supplied in exclude param

Exclude granule by echo granule id

```
curl "%CMR-ENDPOINT%/granules?provider=PROV1&provider=PROV2&echo_granule_id\[\]=G1000000002-CMR_PROV1&echo_granule_id\[\]=G1000000003-CMR_PROV1&echo_granule_id\[\]=G1000000006-CMR_PROV2&exclude\[echo_granule_id\]\[\]=G1000000006-CMR_PROV2"

curl "%CMR-ENDPOINT%/granules?provider=PROV2&exclude\[echo_granule_id\]\[\]=G1000000006-CMR_PROV2&cloud_cover=-70,120"
```

Exclude granule by concept id

    curl "%CMR-ENDPOINT%/granules?provider=PROV1&provider=PROV2&echo_granule_id\[\]=G1000000002-CMR_PROV1&echo_granule_id\[\]=G1000000003-CMR_PROV1&echo_granule_id\[\]=G1000000006-CMR_PROV2&exclude\[concept_id\]\[\]=G1000000006-CMR_PROV2"

Exclude granule by parent concept id

    curl "%CMR-ENDPOINT%/granules?provider=PROV1&provider=PROV2&echo_granule_id\[\]=G1000000002-CMR_PROV1&echo_granule_id\[\]=G1000000003-CMR_PROV1&echo_granule_id\[\]=G1000000006-CMR_PROV2&exclude\[concept_id\]\[\]=C1000000001-CMR_PROV2"

#### <a name="sorting-granule-results"></a> Sorting Granule Results

Granule results are sorted by ascending provider and start date by default. One or more sort keys can be specified using the `sort_key[]` parameter. The order used impacts searching. Fields can be prepended with a `-` to sort in descending order. Ascending order is the default but `+`(Note: `+` must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Granule Sort Keys

  * `campaign` - alias for project
  * `entry_title`
  * `dataset_id` - alias for entry_title
  * `data_size`
  * `end_date`
  * `granule_ur`
  * `producer_granule_id`
  * `project`
  * `provider`
  * `readable_granule_name` - this sorts on a combination of `producer_granule_id` and `granule_ur`. If a `producer_granule_id` is present, that value is used. Otherwise, the `granule_ur` is used.
  * `short_name`
  * `start_date`
  * `version`
  * `platform`
  * `instrument`
  * `sensor`
  * `day_night_flag`
  * `online_only`
  * `browsable` (legacy key browse_only is supported as well)
  * `cloud_cover`
  * `revision_date`

Examples of sorting by start_date in descending(Most recent data first) and ascending orders(Note: the `+` must be escaped with %2B):

    curl "%CMR-ENDPOINT%/granules/sort_key\[\]=-start_date"
    curl "%CMR-ENDPOINT%/granules/sort_key\[\]=%2Bstart_date"



### <a name="retrieving-concepts-by-concept-id-and-revision-id"></a> Retrieve concept with a given concept-id or concept-id & revision-id

This allows retrieving the metadata for a single concept. This is only supported for collections, granules, variables, services, tools and subscriptions. If no format is specified the native format of the metadata (and the native version, if it exists) will be returned.

By concept id

    curl -i  "%CMR-ENDPOINT%/concepts/:concept-id"

By concept id and revision id

    curl -i "%CMR-ENDPOINT%/concepts/:concept-id/:revision-id"

Plain examples, with and without revision ids:

    curl -i "%CMR-ENDPOINT%/concepts/G100000-PROV1"
    curl -i "%CMR-ENDPOINT%/concepts/C100000-PROV1/1"
    curl -i "%CMR-ENDPOINT%/concepts/V100000-PROV1"
    curl -i "%CMR-ENDPOINT%/concepts/V100000-PROV1/1"
    curl -i "%CMR-ENDPOINT%/concepts/V100000-PROV1/2"

File extension examples:

    curl -i "%CMR-ENDPOINT%/concepts/G100000-PROV1.iso"
    curl -i "%CMR-ENDPOINT%/concepts/G100000-PROV1.json"
    curl -i "%CMR-ENDPOINT%/concepts/G100000-PROV1/2.echo10"
    curl -i "%CMR-ENDPOINT%/concepts/V100000-PROV1.umm_json"
    curl -i "%CMR-ENDPOINT%/concepts/V100000-PROV1/2.umm_json"
    curl -i "%CMR-ENDPOINT%/concepts/V100000-PROV1/2.umm_json_v1_9"

MIME-type examples:

    curl -i -H 'Accept: application/xml' \
        "%CMR-ENDPOINT%/concepts/G100000-PROV1"
    curl -i -H 'Accept: application/metadata+xml' \
        "%CMR-ENDPOINT%/concepts/G100000-PROV1"
    curl -i -H "Accept: application/vnd.nasa.cmr.umm+json;version=1.9" \
        "%CMR-ENDPOINT%/concepts/V100000-PROV1"

Note that attempting to retrieve a revision that is a tombstone is an error and will return a 400 status code.

The following extensions and MIME types are supported by the `/concepts/` resource for collection and granule concept types:

  * `html`      "text/html" (Collections only)
  * `json`      "application/json"
  * `xml`       "application/xml" (same as .native)
  * `native`    "application/metadata+xml"
  * `echo10`    "application/echo10+xml"
  * `iso`       "application/iso19115+xml"
  * `iso19115`  "application/iso19115+xml"
  * `dif`       "application/dif+xml"
  * `dif10`     "application/dif10+xml"
  * `atom`      "application/atom+xml"
  * `umm_json`  "application/vnd.nasa.cmr.umm+json"
  * `stac`      "application/json; profile=stac-catalogue"

`atom` and `json` formats are only supported for retrieval of the latest collection/granule revisions (i.e. without specifying a particular revision).

`stac` format is only supported for retrieval of the latest granule revisions (i.e. without specifying a particular revision).

The following extensions and MIME types are supported by the `/concepts/` resource for the variable, service, tool  and subscription concept types:

  * `umm_json`  "application/vnd.nasa.cmr.umm+json"


### <a name="search-with-post"></a> Search with POST

Search collections or granules with query parameters encoded form in POST request body.

    curl -i -XPOST %CMR-ENDPOINT%/collections -d "dataset_id[]=Example%20DatasetId&dataset_id[]=Dataset2"

### <a name="search-response-as-granule-timeline"></a> Search Response as Granule Timeline

Granule timeline queries allow clients to find time intervals with continuous granule coverage per collection. The intervals are listed per collection and contain the number of granules within each interval. A timeline search can be performed by sending a `GET` request with query parameters or a `POST` request with query parameters form encoded in request body to the `granules/timeline` route. The utility of this feature for clients is in building interactive timelines. Clients need to display on the timeline where there is granule data and where there is none.

It supports all normal granule parameters. It requires the following parameters.

  * `start_date` - The start date of the timeline intervals to search from.
  * `end_date` - The end date of to search from.
  * `interval` - The interval granularity. This can be one of year, month, day, hour, minute, or second. At least one granule found within the interval time will be considered coverage for that interval.
  * `concept_id` - Specifies a collection concept id to search for. It is recommended that the timeline search be limited to a few collections for good performance.

The response format is in JSON. Intervals are returned as tuples containing three numbers like `[949363200,965088000,4]`. The two numbers are the start and stop date of the interval represented by the number of seconds since the epoch. The third number is the number of granules within that interval.

#### Example Request:

    curl -i "%CMR-ENDPOINT%/granules/timeline?concept_id=C1-PROV1&start_date=2000-01-01T00:00:00Z&end_date=2002-02-01T00:00:00.000Z&interval=month"

#### Example Response

```
[{"concept-id":"C1200000000-PROV1","intervals":[[949363200,965088000,4],[967766400,970358400,1],[973036800,986083200,3],[991353600,1072915200,3]]}]
```

### <a name="retrieve-provider-holdings"></a> Retrieve Provider Holdings

Provider holdings can be retrieved as XML or JSON. It will show all CMR providers, collections and granule counts regardless of the user's ACL access.

All provider holdings

    curl "%CMR-ENDPOINT%/provider_holdings.xml"

Provider holdings for a list of providers

    curl "%CMR-ENDPOINT%/provider_holdings.json?provider-id\[\]=PROV1&provider-id\[\]=PROV2"

### <a name="search-with-json-query"></a> Search with JSON Query

Search for collections or granules with JSON in a POST request body. The JSON must conform to the schema
that is defined in `%CMR-ENDPOINT%/site/CollectionJSONQueryLanguage.json` or `%CMR-ENDPOINT%/site/GranuleJSONQueryLanguage.json`
for collections and granules respectively. Only collection and granule search are supported.

    curl -XPOST -H "Content-Type: application/json" %CMR-ENDPOINT%/collections
    -d '{"condition": { "and": [{ "not": { "or": [{ "provider": "TEST" },
                                                  { "and": [{ "project": "test-project",
                                                              "platform": {"short_name": "mars-satellite"}}]}]}},
                                { "bounding_box": [-45,15,0,25],
                                  "science_keywords": { "category": "EARTH SCIENCE" }}]}}'

    curl -XPOST -H "Content-Type: application/json" %CMR-ENDPOINT%/granules
    -d '{"condition": { "and": [{ "not": { "or": [{ "provider": "TEST" },
                                                  { "and": [{ "project": "test-project",
                                                              "platform": {"short_name": "mars-satellite"}}]}]}},
                                { "bounding_box": [-45,15,0,25],
                                  "short_name": "Short_name 1"}]}}'

### <a name="search-with-aql"></a> Search with AQL

Search collections or granules with AQL in POST request body. The AQL must conform to the schema
that is defined in `%CMR-ENDPOINT%/site/IIMSAQLQueryLanguage.xsd`.

    curl -i -XPOST -H "Content-Type: application/xml" %CMR-ENDPOINT%/concepts/search -d '<?xml version="1.0" encoding="UTF-8"?>
    <query><for value="collections"/><dataCenterId><all/></dataCenterId>
    <where><collectionCondition><shortName><value>S1</value></shortName></collectionCondition></where></query>'

### <a name="document-scoring"></a> Document Scoring

Collection search results are scored when any of the following parameters are searched:

* keyword
* platform
* instrument
* sensor
* two_d_coordinate_system_name
* science_keywords
* project
* processing_level_id
* data_center
* archive_center

Any terms found in the those parameters are used to score results across other fields in the search results. A term is a contiguous set of characters not containing whitespace. A series of filters are executed against each document. Each of these has an associated boost value. The boost values of all the filters that match a given document are multiplied together to get the final document score.

The terms are separated between keywords found in the keywords field and additional terms found in the fields listed above.

The filters are case insensitive, support wild-cards * and ?, and are given below:

1. All keyword terms are contained in the long-name field OR one of the keyword terms exactly matches the short-name field OR one of the additional terms is contained in the short-name or long-name - weight 1.4
2. The keyword term field is a single string that exactly matches the entry-id field OR one of the additional terms is contained in the entry-id - weight 1.4
3. All keyword terms are contained in the Project/long-name field OR one of the keyword terms exactly matches the Project/short-name field OR one of the additional terms is contained in the Project/short-name or Project/long-name - weight 1.3
4. All keyword terms are contained in the Platform/long-name field OR one of the terms exactly matches the Platform/short-name field OR one of the additional terms is contained in the Platform/short-name or Platform/long-name - weight 1.3
5. All keyword terms are contained in the Platform/Instrument/long-name field OR one of the keyword terms exactly matches the Platform/Instrument/short-name field OR one of the additional terms is contained in the Platform/Instrument/short-name or Platform/Instrument/long-name - weight 1.2
6. All keyword terms are contained in the Platform/Instrument/Sensor/long-name field OR one of the keyword terms exactly matches the Platform/Instrument/Sensor/short-name field OR one of the additional terms is contained in the Platform/Instrument/Sensor/short-name or Platform/Instrument/Sensor/long-name - weight 1.2
7. The keyword term field is a single string that exactly matches the science-keyword field OR an additional term is contained in the science-keyword field - weight 1.2
8. The keyword term field is a single string that exactly matches the spatial-keyword field OR an additional term is contained in the spatial-keyword field - weight 1.1
9. The keyword term field is a single string that exactly matches the temporal-keyword field OR an additional term is contained in the temporal-keyword field - weight 1.1

### <a name="facets"></a> Facets

Facets are counts of unique values from fields in items matching search results. Facets are supported with collection or granule search results and are enabled with the `include_facets` parameter. There are three different types of facet responses. There are flat facets (collection only), hierarchical facets (collection only), and version 2 facets.

Flat and hierarchical facets are supported on all collection search response formats except for the opendata response format. When `echo_compatible=true` parameter is also present, the facets are returned in the catalog-rest search_facet style in XML or JSON format.

Several fields including science keywords, data centers, platforms, instruments, and location keywords can be represented as either flat or hierarchical fields. By default facets are returned in a flat format showing counts for each nested field separately. In order to retrieve hierarchical facets pass in the parameter `hierarchical_facets=true`.

#### <a name="facets-v2-response-format"></a> Version 2 Facets Response Format

Version 2 facets are enabled by setting the `include_facets=v2` parameter in either collection or granule search requests in the JSON format. In order to request faceting on granule searches, the search must be limited in scope to a single collection (e.g. by specifying a single concept ID in the collection_concept_id parameter). The max number of values in each v2 facet can be set by using facets_size parameter (i.e. facets_size[platforms]=10, facets_size[instrument]=20. Default size is 50.). facets_size is only supported for collection v2 facet search. The same fields apply in the v2 facets as for the flat facets with the addition of horizontal range facets and latency facets. When calling the CMR with a query the V2 facets are returned. These facets include the apply field described in more detail a few paragraphs below that includes the search parameter and values that need to be sent back to the CMR.

##### Specifying facet fields

Hierarchical Facet requests include any or all parts of the hierarchical structure using the `&parameter[set][subfield]=value` notation where:

* **set**: Field group number denoting related hierarchical subfields where all subfields for one facet use the same number. Values start with 0.
* **subfield**: Field name in the hierarchical facet as defined by KMS. ie: Platforms uses Basis, Category, Sub_Category, Short_Name
* **value**: facet value. ie Platform Basis has a `Air-based Platforms` value.

Example: `science_keywords_h[0][topic]=Oceans`

Example curl calls:

    %CMR-ENDPOINT%/collections.json?include_facets=v2&hierarchical_facets=true&science_keywords_h%5B0%5D%5Btopic%5D=Oceans

##### Responses

With version 2 facets the CMR makes no guarantee of which facets will be present, whether the facets returned are hierarchical or flat in nature, how many values will be returned for each field, or that the same facets will be returned from release to release. The rules for processing v2 facets are as follows.

The response will contain a field, "facets" containing the root node of the facets tree structure. Every node in the tree structure contains the following minimal structure:

```
var treeNode = {
  "title": "Example",         // A human-readable representation of the node
  "type": "group|filter|..."  // The type of node represented
}
```

Currently, the filter response type defines two node types: "group" and "filter". More may be added in the future, and clients must be built to ignore unknown node types.

##### Group Nodes

Group nodes act as containers for similar data. Depending on context, this data may, for instance, be all facet parameters (the root facet node), top-level facets, or children of existing facets. Group nodes further have a

```
var groupNode = { // Inherits treeNode fields
  "applied": true,            // true if the filter represented by this node (see Filter Nodes) or any of its descendants has been applied to the current query
  "has_children": true,       // true if the tree node has child nodes, false otherwise
  "children": [               // List of child nodes, provided at the discretion of the CMR (see below)
  ]
}
```

##### Children

In order to avoid sending unnecessary information, the CMR may in the future opt to not return children for group nodes that have facets, returning only the first few levels of the facet tree. It will, however, allow clients to appropriately display incomplete information and query the full tree as necessary.

##### Relevance

By default, clients should assume that the CMR may limit facet results to only include the most relevant child nodes in facet responses. For instance, if there are hundreds of science keywords at a particular depth, the CMR may choose to only return those that have a substantial number of results. When filtering children, the CMR makes no guarantees about the specific quantities or values of facets returned, only that applied filters attempt to surface the choices that typical users are most likely to find beneficial.

##### Filter Nodes

Filter nodes are group nodes that supply a single query condition indicated by a string (the node title). They further have a field, "applied," which indicates if the query value has already been applied to the current query.

```
var filterNode = { // Inherits groupNode fields
  "count": 1234,                                                          // Count of results matching the filter
  "links": {
    "apply": "%CMR-ENDPOINT%/collections?instrument[]=MODIS", // A URL containing the filter node applied to the current query. Returned only if the node is not applied.
    "remove": "%CMR-ENDPOINT%/collections"                    // A URL containing the filter node removed from the current query. Returned only if the node is applied.
  },
}
```
Note that while two potential queries are listed in "links", in practice only one would be returned based on whether the node is currently applied.

##### Collection Example
The following example is a sample response for a query using the query parameters API which has the "instruments=ASTER" filter applied as well as a page size of 10 applied by the client. The example is hand-constructed for example purposes only. Real-world queries would typically be more complex, counts would be different, and the facet tree would be much larger.

```
{
  "facets": {
    "title": "Browse Collections",
    "type": "group",
    "applied": true, // NOTE: true because the tree does have a descendant node that has been applied
    "has_children": true,
    "children": [
      {
        "title": "Instruments",
        "type": "group",
        "applied": true,
        "has_children": true,
        "children": [
          {
            "title": "MODIS",
            "type": "filter",
            "applied": false,
            "count": 200,
            "links": { "apply": "https://example.com/search/collections?page_size=10&instruments[]=ASTER&instruments[]=MODIS" },
            "has_children": false
          },
          {
            "title": "ASTER",
            "type": "filter",
            "applied": true,
            "count": 2000,
            "links": { "remove": "https://example.com/search/collections?page_size=10" }, // NOTE: Differing response for an applied filter
            "has_children": false
          }
        ]
      },
      {
        "title": "Keywords",
        "type": "group",
        "applied": false,
        "has_children": true,
        "children": [
          {
            "title": "EARTH SCIENCE",
            "type": "filter",
            "applied": false,
            "count": 1500,
            "links": { "apply": "https://example.com/search/collections?page_size=10&instruments[]=ASTER&science_keywords[0][category_keyword]=EARTH%20SCIENCE" },
            "has_children": true,
            "children": [ // NOTE: This is an example of how the CMR may handle children at different levels of a hierarchy.
              {           //       For usability reasons, this should only be done if absolutely necessary, preferring to just collapse the hierarchy when possible
                "title": "Topics",
                "type": "group",
                "applied": false,
                "has_children": true,
                "children": [
                  {
                    "title": "OCEANS",
                    "type": "filter",
                    "applied": false,
                    "count": 500,
                    "links": { "apply": "https://example.com/search/collections?page_size=10&instruments[]=ASTER&science_keywords[0][category_keyword]=EARTH%20SCIENCE&science_keywords[0][topic]=OCEANS" },
                    "has_children": true // NOTE: The node has children, but the CMR has opted not to return them
                  }
                ]
              },
              {
                "title": "Variables",
                "type": "group",
                "applied": false,
                "has_children": true,
                "children": [
                  {
                    "title": "WOLVES",
                    "type": "filter",
                    "applied": false,
                    "count": 2,
                    "links": { "apply": "https://example.com/search/collections?page_size=10&instruments[]=ASTER&science_keywords[0][detailed_variable]=WOLVES" },
                    "has_children": false
                  }
                ]
              }
            ]
          }
        ]
      }
    ]
  }
}
```

##### Granule Example
```
"facets" : {
      "title" : "Browse Granules",
      "has_children" : true,
      "children" : [ {
        "title" : "Temporal",
        "has_children" : true,
        "children" : [ {
          "title" : "Year",
          "has_children" : true,
          "children" : [ {
            "title" : "2004",
            "count" : 50
          }, {
            "title" : "2003",
            "count" : 2
          } ]
        } ]
      } ]
    }
```

#### <a name="facets-in-xml-responses"></a> Collection Facets in XML Responses

Facets in XML search response formats will be formatted like the following examples. The exception is ATOM XML which is the same except the tags are in the echo namespace.

##### <a name="flat-xml-facets"></a> Flat XML Facets

```
<facets>
  <facet field="data_center">
    <value count="28989">LARC</value>
    <value count="19965">GSFC</value>
  </facet>
  <facet field="archive_center">
    <value count="28989">LARC</value>
    <value count="19965">GSFC</value>
  </facet>
  <facet field="project">
    <value count="245">MANTIS</value>
    <value count="132">THUNDER</value>
    <value count="13">Mysterio</value>
  </facet>
  <facet field="platform">
    <value count="76">ASTER</value>
  </facet>
  <facet field="instrument">
    <value count="2">MODIS</value>
  </facet>
  <facet field="sensor">...</facet>
  <facet field="two_d_coordinate_system_name">...</facet>
  <facet field="processing_level_id">...</facet>
  <facet field="category">...</facet>
  <facet field="topic">...</facet>
  <facet field="term">...</facet>
  <facet field="variable_level_1">...</facet>
  <facet field="variable_level_2">...</facet>
  <facet field="variable_level_3">...</facet>
  <facet field="detailed_variable">...</facet>
</facets>
```

##### <a name="hierarchical-xml-facets"></a> Hierarchical XML Facets

Fields that are not hierarchical are returned in the same format as the flat response, but hierarchical fields are returned in a nested structure. Fields which are returned hierarchically include platforms, instruments, data centers, archive centers, and science keywords.

```
<facets>
  <facet field="project"/>
  ...
  <facet field="science_keywords">
    <facet field="category">
      <value-count-maps>
        <value-count-map>
          <value count="31550">EARTH SCIENCE</value>
          <facet field="topic">
            <value-count-maps>
              <value-count-map>
                <value count="8166">ATMOSPHERE</value>
                <facet field="term">
                  <value-count-maps>
                    <value-count-map>
                      <value count="785">AEROSOLS</value>
                    </value-count-map>
                  </value-count-maps>
                </facet>
              </value-count-map>
              <value-count-map>
                <value count="10269">OCEANS</value>
                <facet field="term">
                  <value-count-maps>
                    <value-count-map>
                      <value count="293">AQUATIC SCIENCES</value>
                    </value-count-map>
                  </value-count-maps>
                </facet>
              </value-count-map>
            </value-count-maps>
          </facet>
        </value-count-map>
      </value-count-maps>
    </facet>
  </facet>
</facets>
```

#### <a name="facets-in-json-responses"></a> Collection Facets in JSON Responses

Facets in JSON search response formats will be formatted like the following examples.

##### <a name="flat-json-facets"></a> Flat JSON facets

```
{
  "feed": {
    "entry": [...],
    "facets": [{
      "field": "data_center",
      "value-counts": [
        ["LARC", 28989],
        ["GSFC", 19965]
      ]
    }, {
      "field": "archive_center",
      "value-counts": [
        ["LARC", 28989],
        ["GSFC", 19965]
      ]
    }, {
      "field": "project",
      "value-counts": [
        ["MANTIS", 245],
        ["THUNDER", 132],
        ["Mysterio", 13]
      ]
    }, {
      "field": "platform",
      "value-counts": [["ASTER", 76]]
    }, {
      "field": "instrument",
      "value-counts": [["MODIS", 2]]
    }, {
      "field": "sensor",
      "value-counts": [...]
    }, {
      "field": "two_d_coordinate_system_name",
      "value-counts": [...]
    }, {
      "field": "processing_level_id",
      "value-counts": [...]
    }, {
      "field": "category",
      "value-counts": [...]
    }, {
      "field": "topic",
      "value-counts": [...]
    }, {
      "field": "term",
      "value-counts": [...]
    }, {
      "field": "variable_level_1",
      "value-counts": [...]
    }, {
      "field": "variable_level_2",
      "value-counts": [...]
    }, {
      "field": "variable_level_3",
      "value-counts": [...]
    }, {
      "field": "detailed_variable",
      "value-counts": [...]
    }]
  }
}
```

##### <a name="hierarchical-json-facets"></a> Hierarchical JSON facets

Fields that are not hierarchical are returned in the same format as the flat response, but hierarchical fields are returned in a nested structure.

```
    "facets" : [ {
      "field" : "project",
      "value-counts" : [ ]
    ...
    }, {
      "field" : "science_keywords",
      "subfields" : [ "category" ],
      "category" : [ {
        "value" : "EARTH SCIENCE",
        "count" : 31550,
        "subfields" : [ "topic" ],
        "topic" : [ {
          "value" : "ATMOSPHERE",
          "count" : 8166,
          "subfields" : [ "term" ],
          "term" : [ {
            "value" : "AEROSOLS",
            "count" : 785 } ]
          }, {
          "value" : "OCEANS",
          "count" : 10269,
          "subfields" : [ "term" ],
          "term" : [ {
            "value" : "AQUATIC SCIENCES",
            "count" : 293
          } ]
        } ]
      } ]
    } ]
```

### <a name="humanizers"></a> Humanizers

Humanizers define the rules that are used by CMR to provide humanized values for various facet fields and also support other features like improved relevancy of faceted terms. The rules are defined in JSON. Operators with Admin privilege can update the humanizer instructions through the update humanizer API.

#### <a name="updating-humanizers"></a> Updating Humanizers

Humanizers can be updated with a JSON representation of the humanizer rules to `%CMR-ENDPOINT%/humanizers` along with a valid EDL bearer token or Launchpad token. The response will contain a concept id and revision id identifying the set of humanizer instructions.

```
curl -XPUT -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/humanizers -d \
'[{"type": "trim_whitespace", "field": "platform", "order": -100},
  {"type": "alias", "field": "platform", "source_value": "AM-1", "replacement_value": "Terra", "reportable": true, "order": 0}]'

HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 48

{"concept_id":"H1200000000-CMR","revision_id":2}
```

#### <a name="retrieving-humanizers"></a> Retrieving Humanizers

The humanizers can be retrieved by sending a GET request to `%CMR-ENDPOINT%/humanizers`.

```
curl -i %CMR-ENDPOINT%/humanizers?pretty=true

HTTP/1.1 200 OK
Content-Length: 224
Content-Type: application/json; charset=UTF-8

[ {
  "type" : "trim_whitespace",
  "field" : "platform",
  "order" : -100
}, {
  "type" : "alias",
  "field" : "platform",
  "source_value" : "AM-1",
  "replacement_value" : "Terra",
  "reportable" : true,
  "order" : 0
} ]
```

#### <a name="facets-humanizers-report"></a> Humanizers Report

The humanizers report provides a list of fields that have been humanized in CSV format. The report format is: provider, concept id, product short name, product version, original field value, humanized field value.

    curl "%CMR-ENDPOINT%/humanizers/report"

Note that this report is currently generated every 24 hours with the expectation that this more than satisfies weekly usage needs.

An administrator with system object INGEST\_MANAGEMENT\_ACL update permission can force the report to be regenerated by passing in a query parameter `regenerate=true`.

### <a name="search-for-tiles"></a> Search for Tiles

Tiles are geographic regions formed by splitting the world into rectangular regions in a projected coordinate system such as Sinusoidal Projection based off an Authalic Sphere. CMR supports searching of tiles which fall within a geographic region defined by a given input geometry. Currently, only tiles in MODIS Integerized Sinusoidal Grid(click [here](https://lpdaac.usgs.gov/products/modis_products_table/modis_overview) for more details on the grid) can be searched. The input geometry could be either a minimum bounding rectangle or one of point, line or polygon in spherical coordinates. The input coordinates are to be supplied in the same format as in granule and collection spatial searches (See under "Find granules by Spatial").

A query could consist of multiple spatial parameters, two points and a bounding box for example. All the spatial parameters are ORed in a query meaning a query will return all the tiles which intersect at-least one of the given geometries.

Here are some examples:
Find the tiles which intersect a polygon.

    curl -i "%CMR-ENDPOINT%/tiles?polygon=10,10,30,10,30,20,10,20,10,10"

Find the tiles which intersect a bounding rectangle.

    curl -i "%CMR-ENDPOINT%/tiles?bounding_box=-10,-5,10,5"

Find the tile which contains a point.

    curl -i "%CMR-ENDPOINT%/tiles?point=-84.2625,36.013"

Find all the tiles which a line intersects.

    curl -i "%CMR-ENDPOINT%/tiles?line=1,1,10,5,15,9"

The output of these requests is a list of tuples containing tile coordinates, e.g: [[16,8],[16,9],[17,8],[17,9]], in the JSON format. The first value in each tuple is the horizontal grid coordinate(h), i.e. along east-west and the second value is the vertical grid coordinate(v), i.e. along north-south.

### <a name="retrieve-controlled-keywords"></a> Retrieve Controlled Keywords

The keyword endpoint is used to retrieve the full list of keywords for each of the controlled vocabulary fields. The controlled vocabulary is cached within CMR, but the actual source is the GCMD Keyword Management System (KMS). Users of this endpoint are interested in knowing what the CMR considers as the current controlled vocabulary, since it is the cached CMR values that will eventually be enforced on CMR ingest.

The keywords are returned in a hierarchical JSON format. The response format is such that the caller does not need to know the hierarchy, but it can be inferred from the results. Keywords are not guaranteed to have values for every subfield in the hierarchy, so the response will indicate the next subfield below the current field in the hierarchy which has a value. It is possible for the keywords to have multiple potential subfields below it for different keywords with the same value for the current field in the hierarchy. When this occurs the subfields property will include each of the subfields.

Supported keywords include `platforms`, `instruments`, `projects`, `temporal_keywords`, `location_keywords`, `science_keywords`, `archive_centers`, `data_centers`, `granule-data-format`, `mime-type` and `measurement-name`. The endpoint also supports `providers` which is an alias to `data_centers` and `spatial_keywords` which is an alias to `location_keywords`.

    curl -i "%CMR-ENDPOINT%/keywords/instruments?pretty=true"

__Example Response__

```
{
  "category" : [ {
    "value" : "Earth Remote Sensing Instruments",
    "subfields" : [ "class" ],
    "class" : [ {
      "value" : "Active Remote Sensing",
      "subfields" : [ "type" ],
      "type" : [ {
        "value" : "Altimeters",
        "subfields" : [ "subtype" ],
        "subtype" : [ {
          "value" : "Lidar/Laser Altimeters",
          "subfields" : [ "short_name" ],
          "short_name" : [ {
            "value" : "ATM",
            "subfields" : [ "long_name" ],
            "long_name" : [ {
              "value" : "Airborne Topographic Mapper",
              "uuid" : "c2428a35-a87c-4ec7-aefd-13ff410b3271"
            } ]
          }, {
            "value" : "LVIS",
            "subfields" : [ "long_name" ],
            "long_name" : [ {
              "value" : "Land, Vegetation, and Ice Sensor",
              "uuid" : "aa338429-35e6-4ee2-821f-0eac81802689"
            } ]
          } ]
        } ]
      } ]
    }, {
      "value" : "Passive Remote Sensing",
      "subfields" : [ "type" ],
      "type" : [ {
        "value" : "Spectrometers/Radiometers",
        "subfields" : [ "subtype" ],
        "subtype" : [ {
          "value" : "Imaging Spectrometers/Radiometers",
          "subfields" : [ "short_name" ],
          "short_name" : [ {
            "value" : "SMAP L-BAND RADIOMETER",
            "subfields" : [ "long_name" ],
            "long_name" : [ {
              "value" : "SMAP L-Band Radiometer",
              "uuid" : "fee5e9e1-10f1-4f14-94bc-c287f8e2c209"
            } ]
          } ]
        } ]
      } ]
    } ]
  }, {
    "value" : "In Situ/Laboratory Instruments",
    "subfields" : [ "class" ],
    "class" : [ {
      "value" : "Chemical Meters/Analyzers",
      "subfields" : [ "short_name" ],
      "short_name" : [ {
        "value" : "ADS",
        "subfields" : [ "long_name" ],
        "long_name" : [ {
          "value" : "Automated DNA Sequencer",
          "uuid" : "554a3c73-3b48-43ea-bf5b-8b98bc2b11bc"
        } ]
      } ]
    } ]
  } ]
}
```

Note: Search parameter filtering are not supported - requests are rejected when there exist parameters other than pretty=true.

    curl -i "%CMR-ENDPOINT%/keywords/instruments?platform=TRIMM&pretty=true"

```
{
  "errors" : [ "Search parameter filters are not supported: [{:platform \"TRIMM\"}]" ]
}
```

### <a name="deleted-collections"></a> Find collections that have been deleted after a given date

To support metadata harvesting, a harvesting client can search CMR for collections that are deleted after a given date. The only search parameter supported is `revision_date` and its format is slightly different from the `revision_date` parameter in regular collection search in that only one revision date can be provided and it can only be a starting date, not a date range. The only supported result format is xml references. The response is the references to the highest non-tombstone collection revisions of the collections that are deleted. e.g.

The following search will return the last revision of the collections that are deleted since 01/20/2017.

    curl -i "%CMR-ENDPOINT%/deleted-collections?revision_date=2017-01-20T00:00:00Z&pretty=true"

__Example Response__

```
HTTP/1.1 200 OK
Content-Type: application/xml; charset=UTF-8
CMR-Hits: 3

<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>3</hits>
    <took>10</took>
    <references>
        <reference>
            <name>Dataset1</name>
            <id>C1200000001-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/C1200000001-PROV1/4</location>
            <revision-id>4</revision-id>
        </reference>
        <reference>
            <name>Dataset2</name>
            <id>C1200000002-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/C1200000002-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
        <reference>
            <name>Dataset3</name>
            <id>C1200000003-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/C1200000003-PROV1/8</location>
            <revision-id>8</revision-id>
        </reference>
    </references>
</results>
```

### <a name="deleted-granules"></a> Find granules that have been deleted after a given date

To support metadata harvesting, a harvesting client can search CMR for granules that are deleted after a given date. The only search parameter supported is `revision_date` and its format is slightly different from the `revision_date` parameter in regular granule search in that only one revision date can be provided and it can only be a starting date, not a date range. The only supported result format is json. The revision_date is limited to 1 year in the past.

Additionally, deleted granules search can be filtered by the provider parameter, which takes the provider id, and parent_collection_id which takes the granule's parent collection concept id.

The following search will return the concept-id, parent-collection-id, granule-ur, revision-date, and provider-id of the granules that are deleted since 01/20/2017.

    curl -i "%CMR-ENDPOINT%/deleted-granules.json?revision_date=2017-01-20T00:00:00Z&pretty=true"

__Example Response__

```
HTTP/1.1 200 OK
Date: Thu, 07 Sep 2017 18:52:04 GMT
Content-Type: ; charset=utf-8
Access-Control-Expose-Headers: CMR-Hits, CMR-Request-Id
Access-Control-Allow-Origin: *
CMR-Hits: 4
CMR-Request-Id: 03da8f3d-57b3-4ce8-bbc0-29970a4a8b30
Content-Length: 653
Server: Jetty(9.2.10.v20150310)

[{"granule-ur":"ur2","revision-date":"2017-09-07T18:51:39+0000","parent-collection-id":"C1200000009-PROV1","concept-id":"G2-PROV1","provider-id":"PROV1"},{"granule-ur":"ur1","revision-date":"2017-09-07T18:51:39.123Z","parent-collection-id":"C1200000009-PROV1","concept-id":"G1-PROV1","provider-id":"PROV1"},{"granule-ur":"ur3","revision-date":"2017-09-07T18:51:39.000Z","parent-collection-id":"C1200000010-PROV1","concept-id":"G3-PROV1","provider-id":"PROV1"},{"granule-ur":"ur4","revision-date":"2017-09-07T18:51:39.256Z","parent-collection-id":"C1200000011-PROV2","concept-id":"G4-PROV2","provider-id":"PROV2"}]
```

### <a name="tagging"></a> Tagging

Tagging allows arbitrary sets of collections to be grouped under a single namespaced value. The sets of collections can be recalled later when searching by tag fields.

Tags have the following fields:

* tag_key (REQUIRED): free text specifying the key of the tag. Tag key cannot contain `/` character. Tag key is case-insensitive, it is always saved in lower case. When it is specified as mixed case, CMR will convert it into lower case. It normally consists of the name of the organization or the project who created the tag followed by a dot and the name of the tag. For example, org.ceos.wgiss.cwic.quality. The maximum length for tag key is 1030 characters.
* description (OPTIONAL): a free text description of what this tag is and / or how it is used. The maximum length for description is 4000 characters.
* originator_id (REQUIRED): the Earthdata Login ID of the person who created the tag.

#### <a name="tag-access-control"></a> Tag Access Control

Access to tags is granted through the TAG_GROUP system object identity. Users can only create, update, or delete a tag if they are granted the appropriate permission. Associating and dissociating collections with a tag is considered an update.

#### <a name="creating-a-tag"></a> Creating a Tag

Tags are created by POSTing a JSON representation of a tag to `%CMR-ENDPOINT%/tags` along with a valid EDL bearer token. The user id of the user associated with the token will be used as the originator id. The response will contain a concept id identifying the tag along with the tag revision id.

```
curl -XPOST -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags -d \
'{
  "tag_key": "org.ceos.wgiss.cwic.quality",
  "description": "This is a sample tag."
 }'

HTTP/1.1 201 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 48

{"concept_id":"T1200000000-CMR","revision_id":1}
```

#### <a name="retrieving-a-tag"></a> Retrieving a Tag

A single tag can be retrieved by sending a GET request to `%CMR-ENDPOINT%/tags/<tag-key>` where `tag-key` is the tag key of the tag.

```
curl -i %CMR-ENDPOINT%/tags/org.ceos.wgiss.cwic.quality?pretty=true

HTTP/1.1 200 OK
Content-Length: 216
Content-Type: application/json;charset=ISO-8859-1

{
  "originator_id" : "mock-admin",
  "tag_key": "org.ceos.wgiss.cwic.quality",
  "description" : "This is a sample tag for indicating some data is high quality."
}
```

#### <a name="updating-a-tag"></a> Updating a Tag

Tags are updated by sending a PUT request with the JSON representation of a tag to `%CMR-ENDPOINT%/tags/<tag-key>` where `tag-key` is the tag key of the tag. The same rules apply when updating a tag as when creating it but in addition tag key and originator id cannot be modified. The response will contain the concept id along with the tag revision id.

```
curl -XPUT -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/org.ceos.wgiss.cwic.quality -d \
'{
  "tag_key": "org.ceos.wgiss.cwic.quality",
  "description": "This is a sample tag for indicating some data is high quality."
 }'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 48

{"concept_id":"T1200000000-CMR","revision_id":2}
```

#### <a name="deleting-a-tag"></a> Deleting a Tag

Tags are deleted by sending a DELETE request to `%CMR-ENDPOINT%/tags/<tag-key>` where `tag-key` is the tag key of the tag. Deleting a tag creates a tombstone that marks the tag as deleted. The concept id of the tag and the revision id of the tombstone are returned from a delete request. Deleting a tag dissociates all collections with the tag.

```
curl -XDELETE -i  -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/org.ceos.wgiss.cwic.quality

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 48

{"concept_id":"T1200000000-CMR","revision_id":3}
```


#### <a name="tag-association"></a> Tag Association

A tag can be associated with collections through either a JSON query or a list of collection concept revisions. 
Tag association by query only supports tagging the latest revision of collections. 
Tag association by collections supports tagging any specified collection revisions. 

Expected Response Status:
<ul>
<li>200 OK -- if all associations succeeded</li>
<li>207 MULTI-STATUS -- if some associations succeeded and some failed due to user error</li>
<li>400 BAD REQUEST -- if all associations failed due to user error</li>
</ul>

Expected Response Body:

The response body will consist of a list of tool association objects
Each association object will have:
<ul>
    <li>A `tagged_item` field</li>
    <ul>
        <li>The `tagged_item` field value has the collection concept id and the optional revision id that is used to identify the collection during tag association.</li>
    </ul>
    <li>Either a `tag_association` field with the tag association concept id and revision id when the tag association succeeded or an `errors` field with detailed error message when the tag association failed. </li>
</ul>

- IMPORTANT: The tag and the collections must exist before they can be associated together. 


Here is am example of a tag association request and its response:

```
curl -XPOST -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/org.ceos.wgiss.cwic.native_id/associations -d \
'[{"concept_id": "C1200000005-PROV1", "data": "Global Maps of Atmospheric Nitrogen Deposition, 2016"},
  {"concept_id": "C1200000006-PROV1", "data": "Global Maps of Atmospheric Nitrogen Deposition"}]'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 168

[
  {
    "tag_association":{
      "concept_id":"TA1200000009-CMR",
      "revision_id":1
    },
    "tagged_item":{
      "concept_id":"C1200000005-PROV1"
    }
  },
  {
    "tag_association":{
      "concept_id":"TA1200000009-CMR",
      "revision_id":1
    },
    "tagged_item":{
      "concept_id":"C1200000006-PROV1"
    }
  }
]
```

On occasions when tag association cannot be processed at all due to invalid input, tag association request will return failure status code 400 with the appropriate error message.

#### <a name="associating-collections-with-a-tag-by-query"></a> Associating Collections with a Tag by query

Tags can be associated with collections by POSTing a JSON query for collections to `%CMR-ENDPOINT%/tags/<tag-key>/associations/by_query` where `tag-key` is the tag key of the tag. 
All collections found will be _added_ to the current set of associated collections with a tag. 
Tag associations are maintained throughout the life of a collection. 
If a collection is deleted and re-added it will maintain its tags.

Expected Response Status:
- If query delivers no match, it will still return 200 OK with no associations made

```
curl -XPOST -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/edsc.in_modaps/associations/by_query -d \
'{
  "condition": {"provider": "PROV1"}
 }'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 168

[
  {
    "tag_association":{
      "concept_id":"TA1200000009-CMR",
      "revision_id":1
    },
    "tagged_item":{
      "concept_id":"C1200000000-PROV1"
    }
  },
  {
    "tag_association":{
      "concept_id":"TA1200000008-CMR",
      "revision_id":1
    },
    "tagged_item":{
      "concept_id":"C1200000001-PROV1"
    }
  }
]
```

#### <a name="associating-collections-with-a-tag-by-concept-ids"></a> Associating Collections with a Tag by collection concept ids and optional revision ids

Tags can be associated with collections by POSTing a JSON array of collection concept-ids and optional revision ids to `%CMR-ENDPOINT%/tags/<tag-key>/associations` where `tag-key` is the tag key of the tag. 
User can also provide arbitrary JSON data which is optional during tag association. 
The max length of JSON data used for tag association is 32KB. 
All referenced collections will be _added_ to the current set of associated collections with a tag. 
Tag associations are maintained throughout the life of a collection. 
If a collection is deleted and re-added it will maintain its tags. 
If a tag is already associated with a collection without revision, it cannot be associated with a specific revision of that collection again, and vice versa. 
Tags cannot be associated on tombstoned collection revisions.

Expected Response Status:
<ul>
    <li>200 OK -- if all associations succeeded</li>
    <li>207 MULTI-STATUS -- if some associations succeeded and some failed due to user error</li>
    <li>400 BAD REQUEST -- if all associations failed due to user error</li>
</ul>

```
curl -XPOST -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/gov.nasa.gcmd.review_status/associations -d \
'[{"concept_id": "C1200000005-PROV1", "revision_id": 2, "data": "APPROVED"},
  {"concept_id": "C1200000006-PROV1", "revision_id": 1, "data": "IN_REVIEW"},
  {"concept_id": "C1200000007-PROV1", "revision_id": 1, "data": "REVIEW_DISPUTED"}]'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 168

[
  {
    "tag_association":{
      "concept_id":"TA1200000008-CMR",
      "revision_id":1
    },
    "tagged_item":{
      "concept_id":"C1200000005-PROV1",
      "revision_id":2
    }
  },
  {
    "tag_association":{
      "concept_id":"TA1200000009-CMR",
      "revision_id":1
    },
    "tagged_item":{
      "concept_id":"C1200000006-PROV1",
      "revision_id":1
    }
  }
]
```

#### <a name="tag-dissociation"></a> Tag Dissociation

A tag can be dissociated from collections through either a JSON query or a list of collection concept revisions similar to tag association requests. 
Tag dissociation by query only supports tag dissociation of the latest revision of collections. 
Tag dissociation by collections supports tag dissociation from any specified collection revisions. 
The tag dissociation response looks the same as tag association response. 

Expected Response Status:
<ul>
    <li>200 OK -- if all dissociations succeeded</li>
    <li>207 MULTI-STATUS -- if some dissociations succeeded and some failed due to user error</li>
    <li>400 BAD REQUEST -- if all dissociations failed due to user error</li>
</ul>

Expected Response Body:

The response body will consist of a list of tool association objects
Each association object will have:
<ul>
    <li>A `tagged_item` field</li>
    <ul>
        <li>The `tagged_item` field is the collection concept id and the optional revision id that is used to identify the collection during tag dissociation.</li>
    </ul>
    <li>Either a `tag_association` field with the tag association concept id and revision id when the tag dissociation succeeded or an `errors` or `warnings` field with detailed message when the tag dissociation failed or inapplicable. </li>
</ul>

Here is a sample tag dissociation request and its response:

```
curl -XDELETE -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/edsc.in_modaps/associations -d \
'[{"concept_id": "C1200000005-PROV1"},
  {"concept_id": "C1200000006-PROV1"},
  {"concept_id": "C1200000007-PROV1"}]'

HTTP/1.1 207 MULTI-STATUS
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 168

[
  {
    "status": 200,
    "tag_association":{
      "concept_id":"TA1200000008-CMR",
      "revision_id":2
    },
    "tagged_item":{
      "concept_id":"C1200000005-PROV1"
    }
  },
  {
    "status": 200,
    "warnings":[
      "Tag [edsc.in_modaps] is not associated with collection [C1200000006-PROV1]."
    ],
    "tagged_item":{
      "concept_id":"C1200000006-PROV1"
    }
  },
  {
    "status": 400,
    "errors":[
      "Collection [C1200000007-PROV1] does not exist or is not visible."
    ],
    "tagged_item":{
      "concept_id":"C1200000007-PROV1"
    }
  }
]
```

#### <a name="dissociating-collections-with-a-tag-by-query"></a> Dissociating a Tag from Collections by query

Tags can be dissociated from collections by sending a DELETE request with a JSON query for collections to `%CMR-ENDPOINT%/tags/<tag-key>/associations/by_query` where `tag-key` is the tag key of the tag. All collections found in the query will be _removed_ from the current set of associated collections.

Expected Response Status:
- If query delivers no match, it will still return 200 OK with no associations made

```
curl -XDELETE -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/edsc.in_modaps/associations/by_query -d \
'{
  "condition": {"provider": "PROV1"}
 }'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 168

[
  {
    "tag_association":{
      "concept_id":"TA1200000007-CMR",
      "revision_id":2
    },
    "tagged_item":{
      "concept_id":"C1200000000-PROV1"
    }
  },
  {
    "tag_association":{
      "concept_id":"TA1200000008-CMR",
      "revision_id":2
    },
    "tagged_item":{
      "concept_id":"C1200000001-PROV1"
    }
  }
]
```

#### <a name="dissociating-collections-with-a-tag-by-concept-ids"></a> Dissociating a Tag from Collections by collection concept ids

Tags can be dissociated from collections by sending a DELETE request with a JSON array of collection concept-ids to 
`%CMR-ENDPOINT%/tags/<tag-key>/associations` where `tag-key` is the tag key of the tag.

Expected Response Status:
<ul>
    <li>200 OK -- if all associations succeeded</li>
    <li>207 MULTI-STATUS -- if some associations succeeded and some failed due to user error</li>
    <li>400 BAD REQUEST -- if all associations failed due to user error</li>
</ul>

```
curl -XDELETE -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/gov.nasa.gcmd.review_status/associations -d \
'[{"concept_id": "C1200000005-PROV1", "revision_id": 1},
  {"concept_id": "C1200000006-PROV1", "revision_id": 2}]'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 168

[
  {
    "warnings":[
      "Tag [gov.nasa.gcmd.review_status] is not associated with the specific collection concept revision concept id [C1200000005-PROV1] and revision id [1]."
    ],
    "tagged_item":{
      "concept_id":"C1200000005-PROV1",
      "revision_id":1
    }
  },
  {
    "tag_association":{
      "concept_id":"TA1200000008-CMR",
      "revision_id":2
    },
    "tagged_item":{
      "concept_id":"C1200000006-PROV1",
      "revision_id":2
    }
  }
]
```

#### <a name="searching-for-tags"></a> Searching for Tags

Tags can be searched for by sending a request to `%CMR-ENDPOINT%/tags`.

Tag search results are paged. See [Paging Details](#paging-details) for more information on how to page through tag search results.

##### Tag Search Parameters

The following parameters are supported when searching for tags.

##### Standard Parameters:

* page_size
* page_num
* pretty

##### Tag Matching Parameters

These parameters will match fields within a tag. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `tag_key[]=key1&tag_key[]=key2`.

* tag_key
  * options: pattern
* originator_id
  * options: pattern

##### Tag Search Response

The response is always returned in JSON and includes the following parts.

* hits - How many total tags were found.
* took - How long the search took in milliseconds
* items - a list of the current page of tags with the following fields
  * concept_id
  * revision_id
  * tag_key
  * description
  * originator_id - The id of the user that created the tag.

##### Tag Search Example

```
curl -g -i "%CMR-ENDPOINT%/tags?pretty=true&tag_key=org\\.ceos\\.*&options[tag_key][pattern]=true"

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 292

{
  "items" : [ {
    "concept_id" : "T1200000000-CMR",
    "revision_id" : 1,
    "tag_key" : "org.ceos.wgiss.cwic",
    "description" : "This is a sample tag.",
    "originator_id" : "mock-admin"
  } ],
  "took" : 5,
  "hits" : 1
}
```

### <a name="variable"></a> Variable

Variables are measurement variables belonging to collections/granules that are processable by services. Variable metadata is stored in the JSON format and conforms to [UMM-Var Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/unified-metadata-model/browse/variable) schema.

#### <a name="searching-for-variables"></a> Searching for Variables

Variables can be searched for by sending a request to `%CMR-ENDPOINT%/variables`. XML reference, JSON and UMM JSON response formats are supported for variables search.

Variable search results are paged. See [Paging Details](#paging-details) for more information on how to page through variable search results.

##### <a name="variable-search-params"></a> Variable Search Parameters

The following parameters are supported when searching for variables.

##### Standard Parameters
* page_size
* page_num
* pretty

##### Variable Matching Parameters

These parameters will match fields within a variable. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are ORed together.

* name
  options: pattern, ignore_case
* provider
  options: pattern, ignore_case
* native_id
  options: pattern, ignore_case
* concept_id
* measurement_identifiers
  options: ignore_case, or
* instance_format
  options: pattern
measurement_identifiers parameter is a nested parameter with subfields: contextmedium, object and quantity. Multiple measurement_identifiers can be specified via different indexes to search variables. The following example searches for variables that have at least one measurement_identifier with contextmedium of Med1, object of Object1 and quantity of Q1, and another measurement_identifier with contextmedium of Med2 and object of Obj2.

__Example__

````
curl -g "%CMR-ENDPOINT%/variables?measurement_identifiers\[0\]\[contextmedium\]=Med1&measurement_identifiers\[0\]\[object\]=Object1&measurement_identifiers\[0\]\[quantity\]=Q1&measurement_identifiers\[1\]\[contextmedium\]=med2&measurement_identifiers\[2\]\[object\]=Obj2"
````

The multiple measurement_identifiers are ANDed by default. User can specify `options[measurement-identifiers][or]=true` to make the measurement_identifiers ORed together.

* keyword (free text)
  keyword search is case insensitive and supports wild cards ? and *. There is a limit of 30 wild cards allowed in keyword searches. Within 30 wild cards, there's also limit on the max keyword string length. The longer the max keyword string length, the less number of keywords with wild cards allowed.

The following fields are indexed for keyword (free text) search:

* Variable name
* Variable long name
* Science keywords (category, detailed variable, term, topic, variables 1-3)
* Variable set names
* Associated collection concept ids
* Variable concept id
* InstanceInformation Format

##### <a name="variable-search-response"></a> Variable Search Response

##### XML Reference
The XML reference response format is used for returning references to search results. It consists of the following fields:

|   Field    |                    Description                     |
| ---------- | -------------------------------------------------- |
| hits       | the number of results matching the search query    |
| took       | time in milliseconds it took to perform the search |
| references | identifying information about each search result   |

The `references` field may contain multiple `reference` entries, each consisting of the following fields:

|    Field    |                                                   Description                                                   |
| ----------- | --------------------------------------------------------------------------------------------------------------- |
| name        | the value of the Name field in variable metadata.                                                               |
| id          | the CMR identifier for the result                                                                               |
| location    | the URL at which the full metadata for the result can be retrieved                                              |
| revision-id | the internal CMR version number for the result                                                                  |

__Example__

```
curl -i "%CMR-ENDPOINT%/variables?pretty=true&name=Variable1"
```

__Example Response__

```
HTTP/1.1 200 OK
Content-Type: application/xml; charset=UTF-8
Content-Length: 393

<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>17</took>
    <references>
        <reference>
            <name>Variable1</name>
            <id>V1200000007-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/V1200000007-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```
##### JSON
The JSON response includes the following fields.

* hits - How many total variables were found.
* took - How long the search took in milliseconds
* items - a list of the current page of variables with the following fields
  * concept_id
  * definition
  * revision_id
  * provider_id
  * native_id
  * name
  * long_name
  * science_keywords
  * associations (if applicable)
  * association_details(if applicable)
  * instance_information (if applicable)

__Example__

```
curl -g -i "%CMR-ENDPOINT%/variables.json?pretty=true&name=Var*&options[name][pattern]=true"
```

__Example Response__

```
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Content-Length: 292

{
  "hits" : 2,
  "took" : 2,
  "items" : [ {
    "concept_id" : "V1200000007-PROV1",
    "revision_id" : 3,
    "provider_id" : "PROV1",
    "native_id" : "var1",
    "name" : "Variable1",
    "long_name" : "A long UMM-Var name",
    "definition": "A definition for the variable",
    "science_keywords": [
                {
                    "Category": "sk-A",
                    "Topic": "sk-B",
                    "Term": "sk-C"
                }
            ]
  }, {
    "concept_id" : "V1200000008-PROV1",
    "revision_id" : 1,
    "provider_id" : "PROV1",
    "native_id" : "var2",
    "name" : "Variable2",
    "long_name" : "A long UMM-Var name",
    "definition": "A definition for the variable",
    "science_keywords": [
                {
                    "Category": "sk-A",
                    "Topic": "sk-B",
                    "Term": "sk-C"
                }
            ]
  } ]
}
```
__Example Response With Associations__

```
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Content-Length: 512

{
  "hits" : 1,
  "took" : 41,
  "items" : [ {
    "concept_id" : "V1200000022-PROV1",
    "revision_id" : 2,
    "provider_id" : "PROV1",
    "native_id" : "sample-variable",
    "name" : "methaneVar",
    "long_name" : "Total Methane",
    "definition": "A definition for the variable",
    "science_keywords": [
                {
                    "Category": "sk-A",
                    "Topic": "sk-B",
                    "Term": "sk-C"
                }
            ],
    "associations" : {
      "collections" : [ "C1200000021-PROV1" ]
    },
    "association_details" : {
      "collections" : [ {
        "data" : "\"This is some sample data association\"",
        "concept_id" : "C1200000021-PROV1"
      } ]
    }
  } ]
}
```

##### UMM JSON
The UMM JSON response contains meta-metadata of the variable, the UMM fields and the associations field if applicable. The associations field only applies when there are collections associated to the variable or other concepts generically associated to the variable.

__Example__

```
curl -g -i "%CMR-ENDPOINT%/variables.umm_json?name=Variable1234&pretty=true"
HTTP/1.1 200 OK
Content-Type: application/vnd.nasa.cmr.umm_results+json;version=1.1; charset=utf-8
Content-Length: 1177

{
  "hits" : 1,
  "took" : 14,
  "items" : [ {
    "meta" : {
      "revision-id" : 2,
      "deleted" : false,
      "format" : "application/vnd.nasa.cmr.umm+json",
      "provider-id" : "PROV1",
      "native-id" : "var1",
      "concept-id" : "V1200000009-PROV1",
      "revision-date" : "2017-08-14T20:12:43Z",
      "concept-type" : "variable"
    },
    "umm" : {
      "VariableType" : "SCIENCE_VARIABLE",
      "DataType" : "float32",
      "Offset" : 0.0,
      "ScienceKeywords" : [ {
        "Category" : "sk-A",
        "Topic" : "sk-B",
        "Term" : "sk-C"
      } ],
      "Scale" : 1.0,
      "FillValues" : [ {
        "Value" : -9999.0,
        "Type" : "Science"
      } ],
      "Sets" : [ {
        "Name" : "Data_Fields",
        "Type" : "Science",
        "Size" : 2,
        "Index" : 2
      } ],
      "Dimensions" : [ {
        "Name" : "Solution_3_Land",
        "Size" : 3
      } ],
      "Definition" : "Defines the variable",
      "Name" : "Variable1234",
      "Units" : "m",
      "LongName" : "A long UMM-Var name"
    },
    "associations" : {
      "collections" : [ {
        "concept-id" : "C1200000007-PROV1"
      } ]
    }
  } ]
}
```

##### <a name="retrieving-all-revisions-of-a-variable"></a> Retrieving All Revisions of a Variable

In addition to retrieving the latest revision for a variable parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisions, by passing in `all_revisions=true` with the URL parameters. The reference, JSON and UMM JSON response formats are supported for all revision searches. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true".

    curl "%CMR-ENDPOINT%/variables?concept_id=V1200000010-PROV1&all_revisions=true&pretty=true"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
      <hits>3</hits>
      <took>3</took>
      <references>
          <reference>
              <name>Variable1</name>
              <id>V1200000010-PROV1</id>
              <location>%CMR-ENDPOINT%/concepts/V1200000010-PROV1/3</location>
              <revision-id>3</revision-id>
          </reference>
          <reference>
              <name>Variable1</name>
              <id>V1200000010-PROV1</id>
              <deleted>true</deleted>
              <revision-id>2</revision-id>
          </reference>
          <reference>
              <name>Variable1</name>
              <id>V1200000010-PROV1</id>
              <location>%CMR-ENDPOINT%/concepts/V1200000010-PROV1/1</location>
              <revision-id>1</revision-id>
          </reference>
      </references>
  </results>
```

##### <a name="sorting-variable-results"></a> Sorting Variable Results

By default, variable results are sorted by name, then provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

###### Valid Variable Sort Keys
  * `name`
  * `long_name`
  * `provider`
  * `revision_date`

Examples of sorting by long_name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

    curl "%CMR-ENDPOINT%/variables?sort_key\[\]=-long_name"
    curl "%CMR-ENDPOINT%/variables?sort_key\[\]=%2Blong_name"

#### <a name="variable-access-control"></a> Variable Access Control

Access to variable and variable association is granted through the provider via the INGEST_MANAGEMENT_ACL. Users can only create, update, or delete a variable if they are granted the appropriate permission. Associating and dissociating collections with a variable is considered an update.

### <a name="service"></a> Service

A service enables data to be accessed via a universal resource locator, and has options to enable a variety of transformations to be performed on the data, e.g. spatial, temporal, variable subsetting, reprojection or reformatting. Service metadata is in JSON format and conforms to [UMM-S Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/unified-metadata-model/browse/service).

#### <a name="searching-for-services"></a> Searching for Services

Services can be searched for by sending a request to `%CMR-ENDPOINT%/services`. XML reference, JSON, and UMM JSON response formats are supported for services search.

Service search results are paged. See [Paging Details](#paging-details) for more information on how to page through service search results.

##### <a name="service-search-params"></a> Service Search Parameters

The following parameters are supported when searching for services.

##### Standard Parameters
* page_size
* page_num
* pretty

##### Service Matching Parameters

These parameters will match fields within a service. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are ORed together.

* name
  * options: pattern, ignore_case
* type
  * options: pattern, ignore_case
* provider
  * options: pattern, ignore_case
* native_id
  * options: pattern, ignore_case
* concept_id
* keyword (free text)
  * keyword search is case insensitive and supports wild cards ? and *. There is a limit of 30 wild cards allowed in keyword searches. Within 30 wild cards, there's also limit on the max keyword string length. The longer the max keyword string length, the less number of keywords with wild cards allowed.

The following fields are indexed for keyword (free text) search:

* Service name
* Service long name
* Service type
* Service version
* Service keywords (category, term specific term, topic)
* Service organizations (short and long names, roles)
* Contact persons (first names, contacts last names, roles)
* Contact groups (group name, roles)
* URL (description, URL value)
* Ancillary keywords

##### <a name="service-search-response"></a> Service Search Response

##### XML Reference
The XML reference response format is used for returning references to search results. It consists of the following fields:

|   Field    |                    Description                     |
| ---------- | -------------------------------------------------- |
| hits       | the number of results matching the search query    |
| took       | time in milliseconds it took to perform the search |
| references | identifying information about each search result   |

The `references` field may contain multiple `reference` entries, each consisting of the following fields:

|    Field    |                                                   Description                                                   |
| ----------- | --------------------------------------------------------------------------------------------------------------- |
| name        | the value of the Name field in service metadata.                                                                |
| id          | the CMR identifier for the result                                                                               |
| location    | the URL at which the full metadata for the result can be retrieved                                              |
| revision-id | the internal CMR version number for the result                                                                  |

__Example__

```
curl -i "%CMR-ENDPOINT%/services?name=Service1&pretty=true"

HTTP/1.1 200 OK
Content-Type: application/xml; charset=UTF-8
Content-Length: 393

<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>17</took>
    <references>
        <reference>
            <name>Service1</name>
            <id>S1200000007-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/S1200000007-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```
##### JSON
The JSON response includes the following fields.

* hits - How many total variables were found.
* took - How long the search took in milliseconds
* items - a list of the current page of services with the following fields
  * concept_id
  * revision_id
  * provider_id
  * native_id
  * name
  * long_name
  * associations - a map of the concept ids of concepts that are associated with the service.
  * association-details - a map of the concept ids, optional revision ids, and optional data of concepts that are associated with the service.

__Example__

```
curl -g -i "%CMR-ENDPOINT%/services.json?pretty=true"

HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Content-Length: 944

{
  "hits" : 4,
  "took" : 2,
  "items" : [ {
    "concept_id" : "S1200000012-PROV2",
    "revision_id" : 1,
    "provider_id" : "PROV2",
    "native_id" : "svc3",
    "name" : "a sub for service2",
    "long_name" : "OPeNDAP Service for AIRS Level-3 retrieval products"
  }, {
    "concept_id" : "S1200000013-PROV2",
    "revision_id" : 1,
    "provider_id" : "PROV2",
    "native_id" : "serv4",
    "name" : "s.other",
    "long_name" : "OPeNDAP Service for AIRS Level-3 retrieval products"
  }, {
    "concept_id" : "S1200000010-PROV1",
    "revision_id" : 1,
    "provider_id" : "PROV1",
    "native_id" : "SVC1",
    "name" : "Service1",
    "long_name" : "OPeNDAP Service for AIRS Level-3 retrieval products"
  }, {
    "concept_id" : "S1200000011-PROV1",
    "revision_id" : 1,
    "provider_id" : "PROV1",
    "native_id" : "svc2",
    "name" : "Service2",
    "long_name" : "OPeNDAP Service for AIRS Level-3 retrieval products",
    "associations": {
      "collections": ["C1200000007-PROV1"],
      "tools": ["TL1200000011-PROV1"]
    },
    "association-details": {
      "collections": [{"concept-id": "C1200000007-PROV1",
                       "data": {"formatting-type": "zarr",
                                "regridding-type": {"xyz": "zyx"}}}],
      "tools": [{"concept-id": "TL1200000011-PROV1"}]
    }
  } ]
}
```
##### UMM JSON
The UMM JSON response contains meta-metadata of the service and the UMM fields.

__Example__

```
curl -g -i "%CMR-ENDPOINT%/services.umm_json?name=NSIDC_AtlasNorth&pretty=true"
HTTP/1.1 200 OK
Content-Type: application/vnd.nasa.cmr.umm_results+json;version=1.1; charset=utf-8

{
  "hits": 1,
  "took": 14,
  "items": [
    {
      "meta": {
        "revision-id": 2,
        "deleted": false,
        "format": "application/vnd.nasa.cmr.umm+json",
        "provider-id": "PROV1",
        "native-id": "svc1",
        "concept-id": "S1200000009-PROV1",
        "revision-date": "2017-08-14T20:12:43Z",
        "concept-type": "service"
      },
      "umm": {
        "Name": "NSIDC_AtlasNorth",
        "Type": "WCS",
        "Version": "1.1.1",
        "Description": "Atlas of the Cryosphere: Northern Hemisphere",
        "OnlineResource": {
          "Linkage": "https://nsidc.org/cgi-bin/atlas_north",
          "Name": "NSIDC WCS Service for the Northern Hemisphere",
          "Description": "NSIDC WCS Service, atlas of the Cryosphere: Northern Hemisphere"
        },
        "ServiceOptions": {
          "SubsetType": [
            "Spatial",
            "Temporal",
            "Variable"
          ],
          "SupportedProjections": [
            "WGS 84 / UPS North (N,E)",
            "WGS84 - World Geodetic System 1984",
            "NSIDC EASE-Grid North",
            "Google Maps Global Mercator -- Spherical Mercator"
          ],
          "InterpolationType": [
            "Nearest Neighbor",
            "Bilinear Interpolation"
          ],
          "SupportedFormats": [
            "image/png",
            "image/vnd.wap.wbmp"
          ]
        },
        "Layer": [
          {
            "Name": "sea_ice_concentration_01"
          },
          {
            "Name": "sea_ice_concentration_02"
          },
          {
            "Name": "greenland_elevation"
          }
        ]
      }
    }
  ]
}
```

##### <a name="retrieving-all-revisions-of-a-service"></a> Retrieving All Revisions of a Service

In addition to retrieving the latest revision for a service parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisions, by passing in `all_revisions=true` with the URL parameters. The reference, JSON and UMM JSON response formats are supported for all revision searches. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true".

    curl "%CMR-ENDPOINT%/services?concept_id=S1200000010-PROV1&all_revisions=true&pretty=true"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>3</hits>
        <took>3</took>
        <references>
            <reference>
                <name>Service1</name>
                <id>S1200000010-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/S1200000010-PROV1/3</location>
                <revision-id>3</revision-id>
            </reference>
            <reference>
                <name>Service1</name>
                <id>S1200000010-PROV1</id>
                <deleted>true</deleted>
                <revision-id>2</revision-id>
            </reference>
            <reference>
                <name>Service1</name>
                <id>S1200000010-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/S1200000010-PROV1/1</location>
                <revision-id>1</revision-id>
            </reference>
        </references>
    </results>
```

##### <a name="sorting-service-results"></a> Sorting Service Results

By default, service results are sorted by name, then provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

###### Valid Service Sort Keys
  * `name`
  * `long_name`
  * `provider`
  * `revision_date`

Examples of sorting by provider id in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

    curl "%CMR-ENDPOINT%/services?sort_key\[\]=-provider"
    curl "%CMR-ENDPOINT%/services?sort_key\[\]=%2Bprovider"

#### <a name="service-access-control"></a> Service Access Control

Access to service and service association is granted through the provider via the INGEST_MANAGEMENT_ACL. Associating and dissociating collections with a service is considered an update.

#### <a name="service-association"></a> Service Association

A service identified by its concept id can be associated with collections through a list of collection concept revisions and an optional data payload in JSON format. 
The service association request normally returns status code 200 with a response that consists of a list of individual service association responses, one for each service association attempted to create. 

Expected Response Status:
<ul>
    <li>200 OK -- if all associations succeeded</li>
    <li>207 MULTI-STATUS -- if some associations succeeded and some failed due to user error</li>
    <li>400 BAD REQUEST -- if all associations failed due to user error</li>
</ul>

Expected Response Body:

The response body will consist of a list of service association objects
Each association object will have:
<ul>
    <li>An `associated_item` field</li>
        <ul>
            <li>The `associated_item` field value has the collection concept id and the optional revision id that is used to identify the collection during service association.</li>
        </ul>
    <li>Either a `service_association` field with the service association concept id and revision id when the service association succeeded or an `errors` field with detailed error message when the service association failed.</li>
</ul>

IMPORTANT: Service association requires that user has update permission on INGEST_MANAGEMENT_ACL for the collection's provider.

Here is a sample service association request and its response when collection C1200000005-PROV1 exists and C1200000006-PROV1 does not:
```
curl -XPOST -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/services/S1200000008-PROV1/associations -d \
'[{"concept_id": "C1200000005-PROV1", "data": {"order_option": "OO1200445588-PROV1"}},
  {"concept_id": "C1200000006-PROV1"}]'

HTTP/1.1 207 MULTI-STATUS
Content-Type: application/json; charset=UTF-8
Content-Length: 168

[
  {
    "status": 200,
    "service_association":{
      "concept_id":"SA1200000009-CMR",
      "revision_id":1
    },
    "associated_item":{
      "concept_id":"C1200000005-PROV1"
    }
  },
  {
    "status": 207,
    "errors":[
      "Collection [C1200000006-PROV1] does not exist or is not visible."
    ],
    "associated_item":{
      "concept_id":"C1200000006-PROV1"
    }
  }
]
```

#### <a name="service-dissociation"></a> Service Dissociation

A service identified by its concept id can be dissociated from collections through a list of collection concept revisions similar to service association requests.

Expected Response Status:
<ul>
    <li>200 OK -- if all dissociations succeeded</li>
    <li>207 MULTI-STATUS -- if some dissociations succeeded and some failed due to user error</li>
    <li>400 BAD REQUEST -- if all dissociations failed due to user error</li>
</ul>

IMPORTANT: Service dissociation requires that user has update permission on INGEST_MANAGEMENT_ACL for either the collection's provider, or the service's provider.

Service dissociation example where some dissociations succeed and others failed due to user error
```
curl -XDELETE -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/services/S1200000008-PROV1/associations -d \
'[{"concept_id": "C1200000005-PROV1"},
  {"concept_id": "C1200000006-PROV1"},
  {"concept_id": "C1200000007-PROV1"}]'

HTTP/1.1 207 MULTI-STATUS
Content-Type: application/json; charset=UTF-8
Content-Length: 168

[
  {
    "status": 200,
    "service_association":{
      "concept_id":"SA1200000009-CMR",
      "revision_id":2
    },
    "associated_item":{
      "concept_id":"C1200000005-PROV1"
    }
  },
  {
    "status": 200,
    "warnings":[
      "Service [S1200000008-PROV1] is not associated with collection [C1200000006-PROV1]."
    ],
    "associated_item":{
      "concept_id":"C1200000006-PROV1"
    }
  },
  {
    "status": 400,
    "errors":[
      "Collection [C1200000007-PROV1] does not exist or is not visible."
    ],
    "associated_item":{
      "concept_id":"C1200000007-PROV1"
    }
  },
  {
    "status": 400,
    "errors":[
      "User doesn't have update permission on INGEST_MANAGEMENT_ACL for provider of collection [C1200000008-PROV2] or provider of service/tool to delete the association."
    ],
    "associated_item":{
      "concept_id":"C1200000008-PROV2"
    }
  }
]
```

### <a name="tool"></a> Tool

UMM-T provides metadata to support the User Interface/User Experience (UI/UX)-driven approach to Tools. Specifically, when a user wants to know the tools available for a specific collection and makes selections via the Earthdata Search UI, options are presented showing what operating systems or languages are supported.
The UMM-T model in MMT enables the population of the tool options, either web user interface or downloadable tool options are surfaced in the UI to support these selections. Each UMM-T record contains metadata for tools and other information such as contact groups or contact persons, tool keywords, and supported inputs and outputs.

#### <a name="searching-for-tools"></a> Searching for Tools

Tools can be searched for by sending a request to `%CMR-ENDPOINT%/tools`. XML reference, JSON, and UMM JSON response formats are supported for tools search.

Tool search results are paged. See [Paging Details](#paging-details) for more information on how to page through tool search results.

##### <a name="tool-search-params"></a> Tool Search Parameters

The following parameters are supported when searching for tools.

##### Standard Parameters
* page_size
* page_num
* pretty

##### Tool Matching Parameters

These parameters will match fields within a tool. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are ORed together.

* name
  * options: pattern, ignore_case
* provider
  * options: pattern, ignore_case
* native_id
  * options: pattern, ignore_case
* concept_id
* keyword (free text)
  * keyword search is case insensitive and supports wild cards ? and *. There is a limit of 30 wild cards allowed in keyword searches. Within 30 wild cards, there's also limit on the max keyword string length. The longer the max keyword string length, the less number of keywords with wild cards allowed.

The following fields are indexed for keyword (free text) search:

* Tool name
* Tool long name
* Tool version
* Tool keywords (category, term, specific term, topic)
* Tool organizations (short and long names, roles, url-value)
* Contact persons (first names, contacts last names, roles)
* Contact groups (group name, roles)
* RelatedURL (description, subtype, type, URL, URL content type)
* URL
* Ancillary keywords

##### <a name="tool-search-response"></a> Tool Search Response

##### XML Reference
The XML reference response format is used for returning references to search results. It consists of the following fields:

|   Field    |                    Description                     |
| ---------- | -------------------------------------------------- |
| hits       | the number of results matching the search query    |
| took       | time in milliseconds it took to perform the search |
| references | identifying information about each search result   |

The `references` field may contain multiple `reference` entries, each consisting of the following fields:

|    Field    |                                                   Description                                                   |
| ----------- | --------------------------------------------------------------------------------------------------------------- |
| name        | the value of the Name field in tool metadata.                                                                |
| id          | the CMR identifier for the result                                                                               |
| location    | the URL at which the full metadata for the result can be retrieved                                              |
| revision-id | the internal CMR version number for the result                                                                  |

__Example__

```
curl -i "%CMR-ENDPOINT%/tools?name=someTool1&pretty=true"

HTTP/1.1 200 OK
Content-Type: application/xml; charset=UTF-8
Content-Length: 393

<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>5</took>
    <references>
        <reference>
            <name>someTool1</name>
            <id>TL1200000005-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/TL1200000005-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```
##### JSON
The JSON response includes the following fields.

* hits - How many total variables were found.
* took - How long the search took in milliseconds
* items - a list of the current page of tools with the following fields
  * concept_id
  * revision_id
  * provider_id
  * native_id
  * name
  * long_name

__Example__

```
curl -g -i "%CMR-ENDPOINT%/tools.json?pretty=true"

HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Content-Length: 944

{
  "hits" : 4,
  "took" : 2,
  "items" : [ {
    "concept_id" : "TL1200000005-PROV1",
    "revision_id" : 1,
    "provider_id" : "PROV1",
    "native_id" : "tool-1",
    "name" : "someToolName1",
    "long_name" : "someToolLongName1",
  }, {
    "concept_id" : "TL1200000006-PROV1",
    "revision_id" : 1,
    "provider_id" : "PROV1",
    "native_id" : "tool-2",
    "name" : "someToolName2",
    "long_name" : "someToolLongName2",
  } ]
}
```
##### UMM JSON
The UMM JSON response contains meta-metadata of the tool and the UMM fields.

__Example__

```
curl -g -i "%CMR-ENDPOINT%/tools.umm_json?name=NSIDC_AtlasNorth&pretty=true"
HTTP/1.1 200 OK
Content-Type: application/vnd.nasa.cmr.umm_results+json;version=1.0; charset=utf-8

{
  "hits" : 1,
  "took" : 21,
  "items" : [ {
    "meta" : {
      "native-id" : "tool-1",
      "provider-id" : "PROV1",
      "concept-type" : "tool",
      "concept-id" : "TL1200000005-PROV1",
      "revision-date" : "2020-04-01T19:52:44Z",
      "user-id" : "ECHO_SYS",
      "deleted" : false,
      "revision-id" : 1,
      "format" : "application/vnd.nasa.cmr.umm+json"
    },
    "umm" : {
      "Name": "USGS_TOOLS_LATLONG",
      "LongName": "WRS-2 Path/Row to Latitude/Longitude Converter",
      "Type":  "Downloadable Tool",
      "Version": "1.0"<
      "Description": "The USGS WRS-2 Path/Row to Latitude/Longitude Converter allows users to enter any Landsat path and row to get the nearest scene center latitude and longitude coordinates.",
      "URL": {"URLContentType": "DistributionURL"
              "Type": "DOWNLOAD SOFTWARE",
              "Description": "Access the WRS-2 Path/Row to Latitude/Longitude Converter.",
              "URLValue": "http://www.scp.byu.edu/software/slice_response/Xshape_temp.html"},
      "ToolKeywords": [{"ToolCategory": "EARTH SCIENCE SERVICES"<
                        "ToolTopic": "DATA MANAGEMENT/DATA HANDLING",
                        "ToolTerm": "DATA INTEROPERABILITY",
                        "ToolSpecificTerm": "DATA REFORMATTING"}],
      "Organizations": [{"Roles": ["SERVICE PROVIDER"],
                         "ShortName": "USGS/EROS",
                         "LongName": "US GEOLOGICAL SURVEY EARTH RESOURCE OBSERVATION AND SCIENCE (EROS) LANDSAT CUSTOMER SERVICES",
                         "URLValue": "http://www.usgs.gov"}],
      "MetadataSpecification": {"URL": "https://cdn.earthdata.nasa.gov/umm/tool/v1.0",
                                "Name": "UMM-T",
                                "Version": "1.0"}
    }
  } ]
}
```

##### <a name="retrieving-all-revisions-of-a-tool"></a> Retrieving All Revisions of a Tool

In addition to retrieving the latest revision for a tool parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisions, by passing in `all_revisions=true` with the URL parameters. The reference, JSON and UMM JSON response formats are supported for all revision searches. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true".

    curl "%CMR-ENDPOINT%/tools?concept_id=TL1200000005-PROV1&all_revisions=true&pretty=true"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>3</hits>
        <took>9</took>
        <references>
            <reference>
                <name>someTool1</name>
                <id>TL1200000005-PROV1</id>
                <deleted>true</deleted>
                <revision-id>3</revision-id>
            </reference>
            <reference>
                <name>someTool1</name>
                <id>TL1200000005-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/TL1200000005-PROV1/2</location>
                <revision-id>2</revision-id>
            </reference>
            <reference>
                <name>someTool1</name>
                <id>TL1200000005-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/TL1200000005-PROV1/1</location>
                <revision-id>1</revision-id>
            </reference>
        </references>
    </results>
```

##### <a name="sorting-tool-results"></a> Sorting Tool Results

By default, tool results are sorted by name, then provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

###### Valid Tool Sort Keys
  * `name`
  * `long_name`
  * `provider`
  * `revision_date`

Examples of sorting by name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

    curl "%CMR-ENDPOINT%/tools?sort_key\[\]=-name"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>2</hits>
        <took>6</took>
        <references>
            <reference>
                <name>someTool2</name>
                <id>TL1200000006-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/TL1200000006-PROV1/1</location>
                <revision-id>1</revision-id>
        </reference>
        <reference>
            <name>someTool1</name>
            <id>TL1200000005-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/TL1200000005-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```
    curl "%CMR-ENDPOINT%/tools?sort_key\[\]=%2Bname"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>2</hits>
        <took>6</took>
        <references>
            <reference>
                <name>someTool1</name>
                <id>TL1200000005-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/TL1200000005-PROV1/1</location>
                <revision-id>1</revision-id>
        </reference>
        <reference>
            <name>someTool2</name>
            <id>TL1200000006-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/TL1200000006-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```
#### <a name="tool-access-control"></a> Tool Access Control

Access to tool is granted through the provider via the INGEST_MANAGEMENT_ACL.

#### <a name="tool-association"></a> Tool Association

A tool identified by its concept id can be associated with collections through a list of collection concept revisions. 

The tool association request normally returns status code 200 with a response that consists of a list of individual tool 
association responses, one for each tool association attempted to create. 

Expected Response Status:
<ul>
    <li>200 OK -- if all associations succeeded</li> 
    <li>207 MULTI-STATUS -- if some associations succeeded and some failed due to user error</li>
    <li>400 BAD REQUEST -- if all associations failed due to user error</li>
</ul>

Expected Response Body:

The response body will consist of a list of tool association objects
Each association object will have:
<ul>
    <li>An `associated_item` field</li>
        <ul>
            <li>The `associated_item` field value has the collection concept id and the optional revision id that is used to identify
            the collection during tool association.</li>
        </ul>
    <li>Either a `tool_association` field with the tool association concept id and revision id when the tool association succeeded OR an `errors` field with detailed error message when the tool association failed. </li>
</ul>

IMPORTANT: Tool association requires that user has update permission on INGEST_MANAGEMENT_ACL 
for the collection's provider. 

Here is an example of a tool association request and its response when collection C1200000005-PROV1 exists and C1200000006-PROV1 does not:

```
curl -XPOST -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tools/TL1200000008-PROV1/associations -d \
'[{"concept_id": "C1200000005-PROV1"},
  {"concept_id": "C1200000006-PROV1"}]'

HTTP/1.1 207 MULTI-STATUS
Content-Type: application/json; charset=UTF-8
Content-Length: 168

[
  {
    "tool_association":{
      "concept_id":"TLA1200000009-CMR",
      "revision_id":1
    },
    "associated_item":{
      "concept_id":"C1200000005-PROV1"
    },
    "status": 200
  },
  {
    "errors":[
      "Collection [C1200000006-PROV1] does not exist or is not visible."
    ],
    "associated_item":{
      "concept_id":"C1200000006-PROV1"
    },
    "status": 400
  },
  {
    "errors":[
      "User doesn't have update permission on INGEST_MANAGEMENT_ACL for provider of collection [C1200000007-PROV2] to make the association."
    ],
    "associated_item":{
      "concept_id":"C1200000007-PROV2"
    },
    "status": 400
  }
]
```

#### <a name="tool-dissociation"></a> Tool Dissociation

A tool identified by its concept id can be dissociated from collections through a list of collection concept revisions similar to tool association requests.

Expected Response Status:
<ul>
    <li>200 OK -- if all dissociations succeeded</li>
    <li>207 MULTI-STATUS -- if some dissociations succeeded and some failed due to user error</li>
    <li>400 BAD REQUEST -- if all dissociations failed due to user error</li>
</ul>

IMPORTANT: Tool dissociation requires that user has update permission on INGEST_MANAGEMENT_ACL for either the collection's provider, or the service's provider.

```
curl -XDELETE -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tools/TL1200000008-PROV1/associations -d \
'[{"concept_id": "C1200000005-PROV1"},
  {"concept_id": "C1200000006-PROV1"},
  {"concept_id": "C1200000007-PROV1"}]'

HTTP/1.1 207 MULTI-STATUS
Content-Type: application/json; charset=UTF-8
Content-Length: 168

[
  {
    "tool_association":{
      "concept_id":"TLA1200000009-CMR",
      "revision_id":2
    },
    "associated_item":{
      "concept_id":"C1200000005-PROV1"
    },
    "status": 200
  },
  {
    "warnings":[
      "Tool [TL1200000008-PROV1] is not associated with collection [C1200000006-PROV1]."
    ],
    "associated_item":{
      "concept_id":"C1200000006-PROV1"
    },
    "status": 400
  },
  {
    "errors":[
      "Collection [C1200000007-PROV1] does not exist or is not visible."
    ],
    "associated_item":{
      "concept_id":"C1200000007-PROV1"
    },
    "status": 400
  },
  {
    "errors":[
      "User doesn't have update permission on INGEST_MANAGEMENT_ACL for provider of collection [C1200000008-PROV2] or provider of service/tool to delete the association."
    ],
    "associated_item":{
      "concept_id":"C1200000008-PROV2"
    },
    "status": 400
  }
]
```

### <a name="subscription"></a> Subscription

A subscription allows a user to be notified when specific collections/granules are created, updated, or deleted. The collections/granules specified can be filtered by query conditions.

There are two kinds of subscriptions: Batch Notification and Near-Real-Time Notification

<ul>
    <li>Batch Notification subscription notification processing is executed periodically, to see if there are any collections/granules that are created/updated since the last time the subscription has been processed and will notify the subscription user with any matches. Notification of updates is via the email address associated with the SubscriberId's EarthData Login (URS). </li>
    <ul>
        <li>There are two types of batch process subscriptions (identified by the "Type" field of the subscription):</li>
        <ul>
            <li>collection subscription for users to be notified when collections are created/updated, or </li>
            <li>granule subscription for users to be notified when granules are created/update</li>
        </ul>
    </ul>
    <li>Near-Real-Time (NRT) Notification subscriptions are processed on ingest and are only for granules. When a user subscribes, notifications are sent out via the provided notification endpoint, such as an AWS SQS messaging queue.
</ul>

#### <a name="searching-for-subscriptions"></a> Searching for Subscriptions

Subscriptions can be searched for by sending a request to `%CMR-ENDPOINT%/subscriptions`. XML reference, JSON, and UMM JSON response formats are supported for subscriptions search.

Subscription search results are paged. See [Paging Details](#paging-details) for more information on how to page through subscription search results.

##### <a name="subscription-search-params"></a> Subscription Search Parameters

The following parameters are supported when searching for subscriptions.

##### Standard Parameters
* page_size
* page_num
* pretty

##### Subscription Matching Parameters

These parameters will match fields within a subscription. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are ORed together.


  * name
    * options: pattern, ignore_case
  * provider
    * options: pattern, ignore_case
  * native_id
    * options: pattern, ignore_case
  * concept_id
  * subscriber_id
  * collection_concept_id
  * type
##### <a name="subscription-search-response"></a> Subscription Search Response

##### XML Reference
The XML reference response format is used for returning references to search results. It consists of the following fields:

|   Field    |                    Description                     |
| ---------- | -------------------------------------------------- |
| hits       | the number of results matching the search query    |
| took       | time in milliseconds it took to perform the search |
| references | identifying information about each search result   |

The `references` field may contain multiple `reference` entries, each consisting of the following fields:

|    Field    |                                                   Description                                                   |
| ----------- | --------------------------------------------------------------------------------------------------------------- |
| name        | the value of the Name field in subscription metadata.                                                                |
| id          | the CMR identifier for the result                                                                               |
| location    | the URL at which the full metadata for the result can be retrieved                                              |
| revision-id | the internal CMR version number for the result                                                                  |

__Example__

```
curl -i "%CMR-ENDPOINT%/subscriptions?name=someSub1&pretty=true"

HTTP/1.1 200 OK
Content-Type: application/xml; charset=UTF-8
Content-Length: 393

<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>5</took>
    <references>
        <reference>
            <name>someSub1</name>
            <id>SUB1200000005-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/SUB1200000005-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```
##### JSON
The JSON response includes the following fields.

* hits - How many total variables were found.
* took - How long the search took in milliseconds
* items - a list of the current page of subscriptions with the following fields
  * concept_id
  * revision_id
  * provider_id
  * native_id
  * type
  * name
  * subscriber_id
  * collection_concept_id

__Example__

```
curl -g -i "%CMR-ENDPOINT%/subscriptions.json?pretty=true"

HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Content-Length: 944

{
  "hits" : 4,
  "took" : 2,
  "items" : [ {
    "concept_id" : "SUB1200000005-PROV1",
    "revision_id" : 1,
    "provider_id" : "PROV1",
    "native_id" : "subscription-1",
    "type" : "granule",
    "name" : "someSub1",
    "subscriber-id" : "someSubId1",
    "collection-concept-id" : "C1200000001-PROV1"
  }, {
    "concept_id" : "SUB1200000006-PROV1",
    "revision_id" : 1,
    "provider_id" : "PROV1",
    "native_id" : "subscription-2",
    "type" : "collection",
    "name" : "someSub2",
    "subscriber-id" : "someSubId2",
  } ]
}
```
##### UMM JSON
The UMM JSON response contains meta-metadata of the subscription and the UMM fields.

__Example__

```
curl -g -i "%CMR-ENDPOINT%/subscriptions.umm_json?name=NSIDC_AtlasNorth&pretty=true"
HTTP/1.1 200 OK
Content-Type: application/vnd.nasa.cmr.umm_results+json;version=1.0; charset=utf-8

{
  "hits" : 1,
  "took" : 21,
  "items" : [ {
    "meta" : {
      "native-id" : "subscription-1",
      "provider-id" : "PROV1",
      "concept-type" : "subscription",
      "concept-id" : "SUB1200000005-PROV1",
      "creation-date" : "2020-04-01T19:52:44Z",
      "revision-date" : "2020-04-01T19:52:44Z",
      "user-id" : "ECHO_SYS",
      "deleted" : false,
      "revision-id" : 1,
      "format" : "application/vnd.nasa.cmr.umm+json"
    },
    "umm" : {
      "Name" : "NSIDC_AtlasNorth",
      "SubscriberId" : "someSubId1",
      "EmailAddress" : "sxu@gmail.com",
      "CollectionConceptId" : "C1200000001-PROV1",
      "Query" : "polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78"
    }
  } ]
}
```

##### <a name="retrieving-all-revisions-of-a-subscription"></a> Retrieving All Revisions of a Subscription

In addition to retrieving the latest revision for a subscription parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisions, by passing in `all_revisions=true` with the URL parameters. The reference, JSON and UMM JSON response formats are supported for all revision searches. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true".

    curl "%CMR-ENDPOINT%/subscriptions?concept_id=SUB1200000005-PROV1&all_revisions=true&pretty=true"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>3</hits>
        <took>9</took>
        <references>
            <reference>
                <name>someSub1</name>
                <id>SUB1200000005-PROV1</id>
                <deleted>true</deleted>
                <revision-id>3</revision-id>
            </reference>
            <reference>
                <name>someSub1</name>
                <id>SUB1200000005-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/SUB1200000005-PROV1/2</location>
                <revision-id>2</revision-id>
            </reference>
            <reference>
                <name>someSub1</name>
                <id>SUB1200000005-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/SUB1200000005-PROV1/1</location>
                <revision-id>1</revision-id>
            </reference>
        </references>
    </results>
```

##### <a name="sorting-subscription-results"></a> Sorting Subscription Results

By default, subscription results are sorted by name, then provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

###### Valid Subscription Sort Keys
  * `name`
  * `provider`
  * `collection_concept_id`

Examples of sorting by name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

    curl "%CMR-ENDPOINT%/subscriptions?sort_key\[\]=-name"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>2</hits>
        <took>6</took>
        <references>
            <reference>
                <name>someSub2</name>
                <id>SUB1200000006-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/SUB1200000006-PROV1/1</location>
                <revision-id>1</revision-id>
        </reference>
        <reference>
            <name>someSub1</name>
            <id>SUB1200000005-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/SUB1200000005-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```
    curl "%CMR-ENDPOINT%/subscriptions?sort_key\[\]=%2Bname"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>2</hits>
        <took>6</took>
        <references>
            <reference>
                <name>someSub1</name>
                <id>SUB1200000005-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/SUB1200000005-PROV1/1</location>
                <revision-id>1</revision-id>
        </reference>
        <reference>
            <name>someSub2</name>
            <id>SUB1200000006-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/SUB1200000006-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```
#### <a name="subscription-access-control"></a> Subscription Access Control

Search permission for subscription is granted through the provider via the SUBSCRIPTION_MANAGEMENT ACL. In order to be able to search for a subscription for a given provider, read permission has to be granted to the user through SUBSCRIPTION_MANAGEMENT ACL for the provider.


%GENERIC-DOCS%

### <a name="community-usage-metrics"></a> Community Usage Metrics

Community usage metrics are metrics showing how many times a particular version of a collection has been accessed. Storing these metrics offers improved relevancy based on collection popularity. The metrics are obtained from the ESDIS Metrics System (EMS) and ingested into the system through this API.

#### <a name="updating-community-usage-metrics"></a> Updating Community Usage Metrics

Community usage metrics can be updated using the `%CMR-ENDPOINT%/community-usage-metrics` endpoint with a valid EDL bearer token or Launchpad token. The content is a CSV file obtained from the EMS. The 'Product', 'Version', and 'Hosts' columns are parsed from the CSV file and stored as 'short-name', 'version', and 'access-count' respectively in the CMR. Entries with the same Product (short-name) and Version will have the access count aggregated to form a total access count for that collection and version, stored as one entry in the CMR. The comprehensive parameter accepts a boolean value, true will cause a lookup verification on each line, false will try and short cut the lookup by checking first against the current metrics humanizer, defaults to false.

Note that when sending the data, use the --data-binary option so that the linebreaks in the CSV data are not removed. See the example below.

The response will contain a concept id and revision id identifying the set of community usage metrics.

```
curl -XPUT -i -H "Content-Type: text/csv" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/community-usage-metrics --data-binary <csv-file-location>

HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 48

{"concept_id":"H1200000000-CMR","revision_id":2}
```

#### <a name="retrieving-community-usage-metrics"></a> Retrieving Community Usage Metrics

The community usage metrics can be retrieved by sending a GET request to `%CMR-ENDPOINT%/community-usage-metrics`. The metrics are returned in JSON format.

```
curl -i %CMR-ENDPOINT%/community-usage-metrics?pretty=true

HTTP/1.1 200 OK
Content-Length: 224
Content-Type: application/json; charset=UTF-8

[ {
  "short-name" : "AMSR-L1A",
  "version" : "3",
  "access-count" : 100
}, {
  "short-name" : "MAPSS_MOD04_L2",
  "version" : "1",
  "access-count" : 85
} ]
```

### <a name="administrative-tasks"></a> Administrative Tasks

These tasks require an admin user token with the INGEST\_MANAGEMENT\_ACL with read or update
permission.

#### <a name="clear-the-cache"></a> Clear the cache

    curl -i -XPOST %CMR-ENDPOINT%/clear-cache

#### <a name="clear-scroll"></a> Clear scroll session

    curl -i -XPOST -H "Content-Type: application/json" %CMR-ENDPOINT%/clear-scroll -d '{ "scroll_id" : "xxxx"}'

It returns HTTP status code 204 when successful.

#### <a name="reset-the-application-to-the-initial-state"></a> Reset the application to the initial state

Every CMR application has a reset function to reset it back to it's initial state. Currently this only clears the cache so it is effectively the the same as the clear-cache endpoint.

    curl -i -XPOST %CMR-ENDPOINT%/reset

#### <a name="querying-caches"></a> Querying caches

Endpoints are provided for querying the contents of the various caches used by the application.
The following curl will return the list of caches:

    curl -i %CMR-ENDPOINT%/caches

The following curl will return the keys for a specific cache:

    curl -i %CMR-ENDPOINT%/caches/cache-name

This curl will return the value for a specific key in the named cache:

    curl -i %CMR-ENDPOINT%/caches/cache-name/cache-key

#### <a name="check-application-health"></a> Check application health

This will report the current health of the application. It checks all resources and services used by the application and reports their health in the response body in JSON format. For resources, the report includes an "ok?" status and a "problem" field if the resource is not OK. For services, the report includes an overall "ok?" status for the service and health reports for each of its dependencies. It returns HTTP status code 200 when the application is healthy, which means all its interfacing resources and services are healthy; or HTTP status code 503 when one of the resources or services is not healthy. It also takes pretty parameter for pretty printing the response.

    curl -i -XGET %CMR-ENDPOINT%/health?pretty=true

Example healthy response body:

```
{
  "echo" : {
    "ok?" : true
  },
  "internal-metadata-db" : {
    "ok?" : true,
    "dependencies" : {
      "oracle" : {
        "ok?" : true
      },
      "echo" : {
        "ok?" : true
      }
    }
  },
  "indexer" : {
    "ok?" : true,
    "dependencies" : {
      "elastic_search" : {
        "ok?" : true
      },
      "echo" : {
        "ok?" : true
      }
    }
  }
}
```

Example un-healthy response body:

```
{
  "echo" : {
    "ok?" : true
  },
  "internal-metadata-db" : {
    "ok?" : true,
    "dependencies" : {
      "oracle" : {
        "ok?" : true
      },
      "echo" : {
        "ok?" : true
      }
    }
  },
  "indexer" : {
    "ok?" : false,
    "dependencies" : {
      "elastic_search" : {
        "ok?" : false,
        "problem" : "Unable to get elasticsearch cluster health, caught exception: Connection refused"
        }
      },
      "echo" : {
        "ok?" : true
      }
    }
  }
}
```

### <a name="associate-any-concepts"></a> Associate any concepts

A new association API has been developed to achieve the goal of being able to associate a concept of any type, with or without revision, to one or more other concepts of any type, with or without revisions. The new association API also allows the associations to include an optional data payload, whose purpose is to describe the association itself. Associations which do not initially have an association data payload, may have it added through an association update.

#### <a name="associate-any-concepts"></a> Concept to concept association important notes

A concept can only be associated with another concept either with or without revisions, not both. A concept cannot be associated to itself, even with different revisions.

#### <a name="concept-associations"></a> Concept associations

A concept, with optional revision id, can be associated to one or more other concepts, with optional revision ids and data payloads.
When the revision id is not present, the latest revision is assumed. 

Expected Response Status:
- 200 OK -- if all associations succeeded
- 207 MULTI-STATUS -- if some associations succeeded and some failed due to user error
- 400 BAD REQUEST -- if all associations failed due to user error

In the following example, "3" is optional for S1200000006-PROV1 and "revision_id" is optional for TL1200000008-PROV1.

 __Example__

```
 curl -XPOST \
	-H "Authorization: Bearer XXXXX" \
	-H "Content-Type:application/json" \
   "%CMR-ENDPOINT%/associate/S1200000006-PROV1/3" \
   -d '[{"concept_id": "TL1200000008-PROV1", "revision_id": 1},
         {"concept_id": "V1200000005-PROV1", "data": {"field": "value", "another_field": {"XYZ": "ZYX"}}}]'
```

 __200 OK Example Response__

Note: when two concepts are associated, their concept ids are sorted first. The one that appears at the later position is considered the associated_item.

```
[
    {
        "generic_association": {
            "concept_id": "GA1200449894-CMR",
            "revision_id": 1
        },
        "associated_item": {
            "concept_id": "TL1200000008-PROV1"
        }
    },
    {
        "generic_association": {
            "concept_id": "GA1200449895-CMR",
            "revision_id": 1
        },
        "associated_item": {
            "concept_id": "V1200000005-PROV1"
        }
    }
]
```
#### <a name="associations-in-search-result"></a> Associations in the search result

Latest revisions of associations can be queried by searching for one of the associated concepts using either the JSON or UMM_JSON format. The "associations" field, contains a list of concept ids associated to the concept that was searched for grouped by concept-type. The "association-details" field contains concept ids, as well as association details like revision ids and data payloads if they were included for the particular association. In the example case, a service (S1200000010-PROV1) has been associated to a variable with association details(V1200000012-PROV1), as well as a tool (TL1200000014-PROV1) without any association details.

Note: If you update an association with new data payload information, a new revision of that association will be created. Old revisions of the association will be preserved in the database but won't show up in the search result of the associated concepts.

 __Example__

```
curl -H "CMR-Pretty:true" -H "Authorization Bearer: XXXXX" "%CMR-ENDPOINT%/services.json?concept_id=S1200000010-PROV1"

```

 __Example Response with associated concepts__

```
{
  "hits" : 1,
  "took" : 36,
  "items" : [ {
    "concept_id" : "S1200000010-PROV1",
    "revision_id" : 1,
    "provider_id" : "PROV1",
    "native_id" : "newservice",
    "name" : "NSIDC_ECS_EGI_API_SMAP_L1L2",
    "long_name" : "NSIDC DAAC Programmatic Access Service for Customized Data Access",
    "associations" : {
      "tools" : [ "TL1200000014-PROV1" ],
      "variables" : [ "V1200000012-PROV1" ]
    },
    "association_details" : {
      "tools" : [ {
        "concept_id" : "TL1200000014-PROV1"
      } ],
      "variables" : [ {
        "data" : {
          "field" : "value",
          "another_field" : {
            "XYZ" : "ZYX"
          }
        },
        "concept_id" : "V1200000012-PROV1"
      } ]
    }
  } ]
}
```

#### <a name="concept-dissociations"></a> Concept dissociations

A concept, with optional revision id, can be dissociated from one or more other concepts, with optional revision ids.

Expected Response Status:
- 200 OK -- if all dissociations succeeded
- 207 MULTI-STATUS -- if some dissociations succeeded and some failed due to user error
- 400 BAD REQUEST -- if all dissociations failed due to user error

 __Example__

```
curl -XDELETE \
	-H "CMR-Pretty:true" \
	-H "Authorization: Bearer XXXXX" \
	-H "Content-Type:application/json" \
  "%CMR-ENDPOINT%/associate/S1200000010-PROV1" \
  -d '[{"concept_id": "TL1200000014-PROV1"},
           {"concept_id": "V1200000012-PROV1"}]'
```

 __200 OK Example Response__

```
[ {
  "generic_association" : {
    "concept_id" : "GA1200000015-CMR",
    "revision_id" : 4
  },
  "associated_item" : {
    "concept_id" : "TL1200000014-PROV1"
  }
}, {
  "generic_association" : {
    "concept_id" : "GA1200000016-CMR",
    "revision_id" : 4
  },
  "associated_item" : {
    "concept_id" : "V1200000012-PROV1"
  }
} ]
```

### <a name="cmr-graphql"></a> CMR-Graphql
The CMR has a GraphQL API which can be queried with requests to [%GRAPHQL-ENDPOINT%](%GRAPHQL-ENDPOINT%). The GraphQL API provides an alternative query language for the CMR REST API. The [cmr-graphql](https://github.com/nasa/cmr-graphql) API can retrieve individually requested fields from resources. For example a request with a list of specified fields from a collection would return the values for those fields but, not any other fields on the usual collections response from CMR. The [cmr-graphql](https://github.com/nasa/cmr-graphql) can retrieve data from multiple resources from a single client request so in a single request, data from multiple resources such as the CMR REST API or the [cmr-graphDb](https://github.com/nasa/Common-Metadata-Repository/tree/master/graph-db) instance within a single client request.

### <a name="apollo-studio"></a> Navigating Apollo Studio
Navigating to [%GRAPHQL-ENDPOINT%](%GRAPHQL-ENDPOINT%) on the browser providers an interface through the Apollo Studio services on which the CMR API can be queried. The User interface providers autocomplete, concept schema definitions, and request/response handling using the GraphQL language.

### <a name="cmr-graphql-programmatic-requests"></a> Programmatic requests to CMR-Graphql

The [cmr-graphql](https://github.com/nasa/cmr-graphql) API can handle POST requests from external clients (In the GraphQL query language all requests must be POST requests). A 'Content-Type' with the value 'application/json' must be specified in the header of the request. The body must be valid JSON and should contain the request information you are looking for. The examples below use the `curl` tool. Integration with [cmr-graphql](https://github.com/nasa/cmr-graphql) will vary in implementation by individual programming languages and frameworks which may implement their own libraries or wrappers for GraphQL requests. The Apollo Studio providers, under the more info button (ellipsis button), a `Copy Operation to cURL` which can be used to copy an equivalent `curl` to the query in the interface.

 __Example of retrieving the count from the first page of collections__

```

curl -XPOST '%GRAPHQL-ENDPOINT%' \
-H 'Content-Type: application/json' \
-H 'Authorization: Bearer XXXXX' \
-d '{"query":"{ collections{ count}}"}'

```

__Response__

```
{
  "data": {
    "collections": {
      "count": 94455
    }
  }
}
```

 __Example of retrieving the concept-ids for services associated to a specified collection__

 ```
 curl -XPOST '%GRAPHQL-ENDPOINT%' \
    -H "content-type: application/json" \
    -H "Authorization: XXXXX" \
    -d '{
        "query": "query ($params: CollectionsInput) {collections(params: $params) {items {conceptId services {items {conceptId}}}}}",
        "variables": {
            "params": {
                "conceptId": "C1200000000-PROV1"
            }
        }
    }'
  ```

__Response__

```
{
  "data": {
    "collections": {
      "items": [
        {
          "conceptId": "C1200000000-PROV1",
          "services": {
            "items": [
              {
                "conceptId": "S1200000000-PROV1"
              }
            ]
          }
        }
      ]
    }
  }
}
```



 __Example: Retrieving variables associated to specified collection with a limit for variables returned__

 ```
 curl -XPOST '%GRAPHQL-ENDPOINT%' \
    -H "content-type: application/json" \
    -H "Authorization: XXXXX" \
    -d '{
    "query": "query ($params: CollectionsInput) {collections(params: $params){items{conceptId services{items{conceptId}}}}}",
    "variables": {
        "params": {
            "limit": 2,
            "conceptId": "C1200000000-PROV1"
        },
        "variablesParams2": {
            "limit": 2
        }
    }
}'
```

__Response__

```
{
  "data": {
    "collections": {
      "items": [
        {
          "variables": {
            "items": [
              {
                "conceptId": "V1200000000-PROV1"
              },
              {
                "conceptId": "V1200000001-PROV1"
              }
            ]
          }
        }
      ]
    }
  }
}
```

### <a name="cmr-graphql-addtional-information"></a> Additional Information
The [cmr-graphql](https://github.com/nasa/cmr-graphql) is maintained separately from the CMR REST API, it is expected that new fields added to CMR concepts will lag behind changes to the CMR REST API. Though these should be periodically updated to match the latest changes in the CMR API, requested changes can be made by adding an issue to the [cmr-graphql](https://github.com/nasa/cmr-graphql) repository on
``` https://github.com/nasa/cmr-graphql/issues```.

For additional information on the cmr-graphql as well as more example queries please visit
``` https://github.com/nasa/cmr-graphql#readme ```
