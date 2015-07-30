# umm-spec-lib

The UMM Spec lib contains JSON schemas that defined the Unified Metadata Model. The UMM defines common data structures for representing metadata. The UMM Spec Lib also contains custom JSON files that define mappings between the XML metadata standards (ECHO10, DIF 9, DIF 10, ISO 199115 SMAP and ISO 199115 MENDS) and the UMM. The UMM is represented as Clojure records in the UMM Spec lib. The mappings provide a way for programmatic conversion between XML and UMM.

## Library Layout

Lists major parts of the library.

  * src/cmr/umm-spec/
    * models/ - Contains clojure namespaces that were generated from the JSON schemas
    * json_schema.clj - Contains code for loading JSON schemas.
    * record_generator.clj - Generates clojure records from json schemas. Must be manually done when the JSON schemas are updated.
    * xml_mappings.clj - Code for loading XML mappings.
    * xml_generation.clj - Contains functions for generating XML from UMM and mappings.
    * xml_parsing.clj - Contains functions for parsing XML into UMM records.
  * resources/
    * json-schemas/ - Contains the UMM JSON schemas
    * mappings/ - Contains files defining mappings between XML and UMM.


## Implementing mapping for a new field

Adding mappings for a new or existing field in the UMM should be done at the same time across all formats.

### 1. Obtain the XPaths and example XML samples.

Contact Erich Reiter (erich.e.reiter@nasa.gov) to obtain these.

### 2. Implement the UMM to XML mappings for each format.

The UMM to XML mappings are in files named `resources/mappings/<format>/umm-to-<format>-xml.json`

A field mapping is a tuple of element name and map with instructions on how to extract the value from the UMM record. A list of tuples is used when specifying a list of fields instead of a map because JSON maps can not be loaded in a way that maintains key order.

Example:

In this example "DataSetId" is the element name for the ECHO10 XML. The value will be extracted from the UMM Clojure records using an XPath. XPaths are used as a convenient way to specify the location for extracting a value from a Clojure record.

```
["DataSetId", {
  "type": "xpath",
  "value": "/UMM-C/EntryTitle"
}],
```

### 3. Implement the XML to UMM mappings for each format.

The UMM to XML mappings are in files named `resources/mappings/<format>/<format>-xml-to-umm.json`

Example:

This example shows that a field called "EntryTitle" in the collection record will be set with the value extracted from the ECHO10 XML at the XPath /Collection/DataSetId.

```
"EntryTitle": {
  "type": "xpath",
  "value": "/Collection/DataSetId"
},
```

## License

Copyright © 2015 NASA
