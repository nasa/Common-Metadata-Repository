(ns cmr.search.services.messages.common-messages
  "Contains messages for reporting responses to the user"
  (require [clojure.string :as str]
           [camel-snake-kebab.core :as csk]))

(defn invalid-aql
  [msg]
  (str "AQL Query Syntax Error: " msg))

(defn invalid-sort-key
  [sort-key type]
  (format "The sort key [%s] is not a valid field for sorting %ss." sort-key (name type)))

(defn science-keyword-invalid-format-msg
  []
  (str "Parameter science_keywords is invalid, "
       "should be in the format of science_keywords[0/group number (if multiple groups are present)]"
       "[category/topic/term/variable_level_1/variable_level_2/variable_level_3/detailed_variable]."))

(defn invalid-ignore-case-opt-setting-msg
  "Creates a message saying which parameters would not allow ignore case option setting."
  [params-set]
  (let [params (reduce (fn [params param] (conj params param)) '() (seq params-set))]
    (format "Ignore case option setting disallowed on these parameters: %s" params)))

(defn invalid-pattern-opt-setting-msg
  "Creates a message saying which parameters would not allow pattern option setting."
  [params-set]
  (let [params (reduce (fn [params param] (conj params param)) '() (seq params-set))]
    (format "Pattern option setting disallowed on these parameters: %s" params)))

(defn invalid-exclude-param-msg
  "Creates a message saying supplied parameter(s) are not in exclude params set."
  [params-set]
  (format "Parameter(s) [%s] can not be used with exclude." (str/join ", " (map name params-set))))

(defn invalid-or-opt-setting-msg
  "Creates a message saying which parameter is not allowed to use the 'or' option."
  [param]
  (format "'or' option is not valid for parameter [%s]" param))

(defn invalid-opt-for-param
  "Creates a message saying supplied option is not allowed for parameter."
  [param option]
  (str "Option [" (csk/->snake_case_string option)
       "] is not supported for param [" (csk/->snake_case_string param) "]"))

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
