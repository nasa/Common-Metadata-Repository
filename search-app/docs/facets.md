### <a name="facets"></a> Facets

Facets are counts of unique values from fields in items matching search results. Facets are supported with collection or granule search results and are enabled with the `include_facets` parameter. There are three different types of facet responses. There are flat facets (collection only), hierarchical facets (collection only), and version 2 facets.

Flat and hierarchical facets are supported on all collection search response formats except for the opendata response format. When `echo_compatible=true` parameter is also present, the facets are returned in the catalog-rest search_facet style in XML or JSON format.

Several fields including science keywords, data centers, platforms, instruments, and location keywords can be represented as either flat or hierarchical fields. By default facets are returned in a flat format showing counts for each nested field separately. In order to retrieve hierarchical facets pass in the parameter `hierarchical_facets=true`.

Note that any counts included in hierarchical facets should be considered approximate within a small margin of an error rather than an exact count. If an exact count is required following the link provided to apply that facet will perform a search that returns an exact hits count.

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