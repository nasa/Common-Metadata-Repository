(ns cmr.search.services.messages.common-messages
  "Contains messages for reporting responses to the user"
  (require [clojure.string :as str]
           [camel-snake-kebab.core :as csk]
           [cmr.common.validations.core :as v]))

(defn invalid-aql
  [msg]
  (str "AQL Query Syntax Error: " msg))

(defn science-keyword-invalid-format-msg
  []
  (str "Parameter science_keywords is invalid, "
       "should be in the format of science_keywords[0/group number (if multiple groups are present)]"
       "[category/topic/term/variable_level_1/variable_level_2/variable_level_3/detailed_variable]."))

(defn invalid-exclude-param-msg
  "Creates a message saying supplied parameter(s) are not in exclude params set."
  [params-set]
  (format "Parameter(s) [%s] can not be used with exclude." (str/join ", " (map name params-set))))

(defn mixed-arity-parameter-msg
  "Creates a message saying the given parameter should not appear as both a single value and
  a multivalue."
  [param]
  (str "Parameter ["
       (csk/->snake_case_string param)
       "] may be either single valued or multivalued, but not both."))

(defn json-query-unsupported-msg
  "Creates a message indicating the JSON query searching is not supported for the given concept
  type."
  [concept-type]
  (format "Searching using JSON query conditions is not supported for %ss." (name concept-type)))

(defn invalid-nested-json-query-condition
  "Creates a message indicating the JSON query condition value for the given nested condition is
  invalid."
  [condition-name condition-value]
  (format "Invalid %s query condition %s. Must contain at least one subfield."
          (v/humanize-field condition-name)
          condition-value))

