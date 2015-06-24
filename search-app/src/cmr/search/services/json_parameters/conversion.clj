(ns cmr.search.services.json-parameters.conversion
  "Contains functions for parsing and converting JSON queries into query conditions"
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [cheshire.core :as json]
            [cmr.common.services.errors :as errors]
            [cmr.common.util :as util]
            [cmr.common.concepts :as cc]
            [cmr.common.date-time-parser :as parser]
            [cmr.search.models.query :as qm]
            [cmr.search.models.group-query-conditions :as gc]
            [cmr.search.services.parameters.legacy-parameters :as lp]
            [cmr.search.services.parameters.conversion :as pc]
            [cmr.search.services.parameters.parameter-validation :as pv]
            [cmr.search.services.messages.common-messages :as msg]
            [cmr.search.services.parameters.converters.science-keyword :as psk]))


(def valid-string-conditions
  "A set of the valid JSON Query string conditions"
  #{:provider :entry-id :entry-title :keyword})

(def valid-grouping-conditions
  "A set of the valid JSON Query grouping conditions"
  #{:and :or :not})

(def valid-conditions-for-concept-type
  "A mapping of concept-type to a list of valid conditions for that concept type"
  {:collection (set/union #{:science-keywords :any}
                          psk/science-keyword-fields
                          valid-string-conditions
                          valid-grouping-conditions)})

(def query-condition-name->condition-type-map
  "A mapping of query condition names to the query condition type."
  {:entry-title :string
   :entry-id :string
   :provider :string
   :keyword :keyword
   :or :or
   :and :and
   :not :not
   :science-keywords :science-keywords})

(defn- query-condition-name->condition-type
  "Returns the query condition type based on the given concept-type and param-name."
  [condition-name]
  (condition-name query-condition-name->condition-type-map))

(defmulti parse-json-condition
  "Converts a JSON query condition into a query model condition"
  (fn [condition-name value]
    (query-condition-name->condition-type condition-name)))

(defmethod parse-json-condition :default
  [condition-name value]
  (errors/internal-error!
    (format "Could not find parameter handler for [%s]"
            condition-name)))

(defmethod parse-json-condition :string
  [condition-name value]
  ;; TODO handle case sensitivity and wildcards
  (qm/string-condition condition-name value false false))

(defmethod parse-json-condition :keyword
  [_ value]
  (qm/text-condition :keyword (str/lower-case value)))

(defn- parse-json-condition-map
  "Parse a JSON condition map into the appropriate query conditions. Conditions within a map are
  implicitly and'ed together."
  [condition-map]
  (when-let [query-conditions (seq (for [[k v] condition-map]
                                     (parse-json-condition k v)))]
    (gc/and-conds query-conditions)))

;; Example {"and": [{"entry-title": "ET", "provider": "PROV1"}
;;                  {"revision-date": [null, "2015-04-01T00:00:00Z"]}]}
(defmethod parse-json-condition :and
  [_ values]
  (gc/and-conds (map #(parse-json-condition-map %) values)))

;; Example {"or": [{"entry-title": "ET", "provider": "PROV1"}
;;                 {"revision-date": [null, "2015-04-01T00:00:00Z"]}]}
(defmethod parse-json-condition :or
  [_ values]
  (gc/or-conds (map #(parse-json-condition-map %) values)))

;; Example {"not": {"entry-title": "ET",
;;                  "provider": "PROV1",
;;                  "revision-date": [null, "2015-04-01T00:00:00Z"]}}
(defmethod parse-json-condition :not
  [_ value]
  (qm/negated-condition (parse-json-condition-map value)))

(defn- validate-names-helper
  "Takes a list of valid names and a list of actual names. Throws an error using the provided
  error-msg-fn if there are any invalid names in the list of provided names."
  [valid-names actual-names error-msg-fn]
  (when-let [invalid-names (seq (set/difference (set actual-names) (set valid-names)))]
    (errors/throw-service-error :bad-request (error-msg-fn invalid-names))))

(defn- validate-science-keywords
  "Validate that all of the keys in the science keyword search condition map are valid."
  [science-keywords-map]
  (validate-names-helper (conj psk/science-keyword-fields :any)
                         (keys science-keywords-map)
                         msg/invalid-science-keyword-condition-msg))

(defmethod parse-json-condition :science-keywords
  [_ value]
  (validate-science-keywords value)
  ;; CMR-1765 extract case-sensitive and pattern
  (psk/parse-nested-science-keyword-condition value false false))

(defn- validate-json-conditions
  "Validates that the condition names in the query are valid"
  [concept-type json-query]
  (validate-names-helper (get valid-conditions-for-concept-type concept-type)
                         (util/get-keys-in json-query)
                         (partial msg/invalid-json-condition-names-msg concept-type)))

(defn parse-json-query
  "Converts a JSON query string and query parameters into a query model."
  [concept-type params json-string]
  (let [params (pv/validate-standard-query-parameters concept-type params)
        json-query (util/map-keys->kebab-case (json/parse-string json-string true))]
    (validate-json-conditions concept-type json-query)
    (qm/query (assoc (pc/standard-params->query-attribs concept-type params)
                     :concept-type concept-type
                     :condition (parse-json-condition-map json-query)))))
