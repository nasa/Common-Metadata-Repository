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

### <a name="retrieve-provider-holdings"></a> Retrieve Provider Holdings

Provider holdings can be retrieved as XML or JSON. It will show all CMR providers, collections and granule counts regardless of the user's ACL access.

All provider holdings

    curl "%CMR-ENDPOINT%/provider_holdings.xml"

Provider holdings for a list of providers

    curl "%CMR-ENDPOINT%/provider_holdings.json?provider-id\[\]=PROV1&provider-id\[\]=PROV2"

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