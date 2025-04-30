### <a name="citation-draft"></a> Citation Draft

Citation Drafts are draft records that inform users about the citations that are available in a collection when working with data files. Citation metadata is stored in the JSON format [UMM-Citation Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/otherschemas/browse/citation).

#### <a name="searching-for-citation-drafts"></a> Searching for Citation Drafts

Citation Drafts can be searched for by sending a request to `%CMR-ENDPOINT%/citation-drafts`. XML reference, JSON and UMM JSON response formats are supported for Citation Draft searches.

Citation Draft search results are paged. See [Paging Details](#paging-details) for more information on how to page through Citation Draft search results.

##### <a name="citation-draft-search-params"></a> Citation Draft Search Parameters

The following parameters are supported when searching for Citation Drafts.

##### Standard Parameters

* page\_size
* page\_num
* pretty

##### Citation Draft Matching Parameters

These parameters will match fields within a Citation Draft. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are bitwise `OR`ed together.

* name - options: pattern, ignore\_case
* provider - options: pattern, ignore\_case
* native\_id - options: pattern, ignore\_case
* concept\_id

```
    curl "%CMR-ENDPOINT%/citation-drafts?concept_id=CITD1200000000-PROV1"
```

##### <a name="citation-draft-search-response"></a> Citation Draft Search Response

##### XML Reference

The XML reference response format is used for returning references to search results. It consists of the following fields:

| Field      | Description                                        |
| ---------- | -------------------------------------------------- |
| hits       | the number of results matching the search query    |
| took       | time in milliseconds it took to perform the search |
| references | identifying information about each search result   |

The `references` field may contain multiple `reference` entries, each consisting of the following fields:

| Field       | Description                                                        |
| ----------- | ------------------------------------------------------------------ |
| name        | the value of the Name field in Citation Draft metadata.      |
| id          | the CMR identifier for the result                                  |
| location    | the URL at which the full metadata for the result can be retrieved |
| revision-id | the internal CMR version number for the result                     |

__Example__

```
    curl -H "Cmr-Pretty: true" \
        "%CMR-ENDPOINT%/citation-drafts.xml?name=citation-name"
```

__Sample response__

```
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>1</hits>
        <took>13</took>
        <references>
            <reference>
                <name>citation-name</name>
                <id>CITD1200000000-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/CITD1200000000-PROV1/4</location>
                <revision-id>4</revision-id>
            </reference>
        </references>
    </results>
```

##### JSON

The JSON response includes the following fields.

* hits - How many total records were found.
* took - How long the search took in milliseconds
* items - a list of the current page of records with the following fields
  * concept\_id
  * revision\_id
  * provider\_id
  * native\_id
  * name

__Example__

```
    curl -H "Cmr-Pretty: true" \
        "%CMR-ENDPOINT%/citation-drafts.json?name=citation-name"
```

__Sample response__

```
    {
        "hits": 1,
        "took": 10,
        "items": [
            {
                "concept_id": "CITD1200000000-PROV1",
                "revision\_id": 4,
                "provider\_id": "PROV-1",
                "native\_id": "sampleNative-Id",
                "name": "citation-name"
            }
        ]
    }
```

##### UMM JSON

The UMM JSON response contains meta-metadata of the Citation Draft, the UMM fields and the associations field if applicable. [To search over specific versions of UMM](#umm-json). 

__Example__

```
    curl -H "pretty=true" \
        "%CMR-ENDPOINT%/citation-drafts.umm_json?name=citation-name"
```

__Sample response__

```
    {
        "hits" : 1,
        "took" : 97,
        "items" : [ {
            "meta" : {
            "revision-id" : 2,
            "deleted" : false,
            "provider-id" : "PROV1",
            "user-id" : "ECHO_SYS",
            "native-id" : "test-draft",
            "concept-id" : "CITD1200000013-PROV1",
            "revision-date" : "2025-04-30T18:57:26.645Z",
            "concept-type" : "citation-draft"
            },
            "umm" : {
            "ResolutionAuthority" : "https://doi.org",
            "ScienceKeywords" : [ {
                "Category" : "EARTH SCIENCE",
                "Topic" : "ATMOSPHERE",
                "Term" : "AIR QUALITY",
                "VariableLevel1" : "NITROGEN DIOXIDE"
            }, {
                "Category" : "EARTH SCIENCE",
                "Topic" : "HUMAN DIMENSIONS",
                "Term" : "PUBLIC HEALTH",
                "VariableLevel1" : "ENVIRONMENTAL IMPACTS"
            } ],
            "RelatedIdentifiers" : [ {
                "RelationshipType" : "Cites",
                "RelatedIdentifierType" : "DOI",
                "RelatedIdentifier" : "10.5067/MODIS/MOD08_M3.061",
                "RelatedResolutionAuthority" : "https://doi.org"
            }, {
                "RelationshipType" : "Refers",
                "RelatedIdentifierType" : "DOI",
                "RelatedIdentifier" : "10.5067/MEASURES/AEROSOLS/DATA203",
                "RelatedResolutionAuthority" : "https://doi.org"
            } ],
            "Abstract" : "The global pandemic caused by the coronavirus disease 2019 (COVID-19) led to never-before-seen reductions in urban and industrial activities, along with associated emissions to the environment. This has created an unprecedented opportunity to study atmospheric composition in the absence of its usual drivers. We have combined surface-level nitrogen dioxide (NO2) observations from air quality monitoring stations across the globe with satellite measurements and machine learning techniques to analyze NO2 variations from the initial strict lockdowns through the restrictions that continued into fall 2020. Our analysis shows that the restrictions led to significant decreases in NO2 concentrations globally through 2020.",
            "Name" : "Citation-Name",
            "IdentifierType" : "DOI",
            "Identifier" : "10.1029/2021JD034797",
            "CitationMetadata" : {
                "Type" : "journal-article",
                "Volume" : "126",
                "Publisher" : "American Geophysical Union",
                "Number" : "20",
                "Title" : "Global Impact of COVID-19 Restrictions on the Atmospheric Concentrations of Nitrogen Dioxide and Ozone",
                "Container" : "Journal of Geophysical Research: Atmospheres",
                "Year" : 2021,
                "Author" : [ {
                "ORCID" : "0000-0003-2541-6634",
                "Given" : "Christoph A.",
                "Family" : "Keller",
                "Sequence" : "first"
                }, {
                "ORCID" : "0000-0002-6194-7454",
                "Given" : "K. Emma",
                "Family" : "Knowland",
                "Sequence" : "additional"
                } ],
                "Pages" : "e2021JD034797"
            },
            "MetadataSpecification" : {
                "URL" : "https://cdn.earthdata.nasa.gov/generics/citation/v1.0.0",
                "Name" : "Citation",
                "Version" : "1.0.0"
            }
            }
        } ]
    }
```

#### <a name="sorting-citation-draft-results"></a> Sorting Citation Draft Results

By default, Citation Draft results are sorted by name, then by provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Citation Sort Keys

* name
* provider
* revision_date

Examples of sorting by name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

```
    curl "%CMR-ENDPOINT%/citation-drafts?sort_key\[\]=-name"
    curl "%CMR-ENDPOINT%/citation-drafts?sort_key\[\]=%2Bname"
```