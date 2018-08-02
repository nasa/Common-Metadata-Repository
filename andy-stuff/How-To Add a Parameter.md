### Include External Libraries

One method of including external code libraries is adding a reference
to them in project.clj file.

### Necessary Files
| File                         | Path                                                     | Description      |
|:--------------------------   |:---------------------------------------------------------|------------------|
| 1 - parameter_validation.clj | common-app-lib-/src/cmr/common_app/services/search       | Lower-level functions supporting validating query parameters, used by other modules.   |
| 2 - conversion.clj           | search-app/src/cmr/search/services/parameters            | Functions for parsing and converting query parameters to query conditions. |
| 3 - parameter_validation.clj | search-app/src/cmr/search/services/parameters            | Top-level functions for validating query parameters |
| 4 - spatial.clj              | search-app/src/cmr/search/services/parameters/converters | Converters for spatial parameters |
| 5 - parameter_validation     | search-app/test/cmr/search/test/services                 | Mid-level validations of parameters |
| 6 - codec.clj                | spatial-lib/src/cmr/spatial                              | Makes the spatial areas URL encodeable as accepted on the Catalog REST API |
| 7 - wkt.clj                  | spatial-lib/src/cmr/spatial                              | I'm no sure yet. |
| 8 - codec.clj                | spatial-lib/test/cmr/spatial/test                        | Tests that URL encoding by decoding it? |


##### 1 - parameter_validation
common-app-lib-/src/cmr/common_app/services/search

Supporting functions validating query parameters, used by other modules. These functions are included in other modules
which will use the functions to help validate parameters. Functions do things like:
* Validates that no invalid parameter names in the options were supplied
* Returns the sort keys that are valid with the given concept type
* Validates the sort-key parameter if present
* A set of the valid parameter names for the given concept-type
* Returns a set of parameter names that are valid at the query level
* Validates that no invalid parameters were supplied
* Validates datetime range string is in the correct format

##### 2 - conversion.clj
search-app/src/cmr/search/services/parameters

This file contains functions for parsing and converting query parameters to query conditions.
Functions do things like:
* Mapping of parameter names in a collection query to the parameter name to use in the granule query.
* Convert tag param and value into query condition
* Converts parameters from a granule timeline request into a query.

##### 3 - parameter_validation.clj
search-app/src/cmr/search/services/parameters

The initial set of functions called to validate parameters. It includes the two other parameter validation files
as well as several other supporting files. This looks like were parameter validation begins. Functions include:
* Individual parameter validation. This is the largest function in the file.
* Temporal format validations that accept different parameters and then call the supporting functions in other files
* Validating science keywords
* Validating types, keys, years, months. dates, etc.

##### 4 - spatial.clj
search-app/src/cmr/search/services/parameters/converters

Contains parameter converters for spatial parameters. Converts the value which can be a single string
or a vector of strings into a sequence of strings irrespective if it was a point, line, polygon, etc.

##### 5 - parameter_validation.clj
search-app/test/cmr/search/test/services

Perform higher-level validations of parameters, like:
* Make sure the parameter is allowed for granules, collections, or both.
* Map individual parameters to generalized date validation, string validation, etc.
* Make sure start dates are less than end dates.
* Make sure dates are reasonable.

##### 6 - codec.clj
spatial-lib/src/cmr/spatial

Encodes the spatial area for inclusion in a URL. This is necessary because the DB is accessed
via a REST API.

##### 7 - wkt.clj (or whatever your parameter uses as a spatial type; e.g., polygon, point, line)
spatial-lib/src/cmr/spatial 

Not sure yet

##### 8 - codec.clj
spatial-lib/test/cmr/spatial/test

Tests that URL encoding by decoding it?
