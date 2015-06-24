(ns cmr.search.services.json-parameters.conversion
  "Contains functions for parsing and converting JSON queries into query conditions"
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [cmr.common.services.errors :as errors]
            [cmr.search.models.query :as qm]
            [cmr.search.models.group-query-conditions :as gc]
            [cmr.common.util :as util]
            [cmr.search.services.parameters.legacy-parameters :as lp]
            [cmr.common.concepts :as cc]
            [cmr.common.date-time-parser :as parser]
            [cmr.search.services.parameters.conversion :as pc]
            [cmr.search.services.parameters.parameter-validation :as pv]
            [cheshire.core :as json]
            [cmr.search.services.messages.common-messages :as msg]))

(def valid-conditions-for-concept-type
  "A mapping of concept-type to a list of valid conditions for that concept type"
  {:collection #{:or :and :not :provider :entry-id :entry-title}})

(def query-condition-name->condition-type-map
  "A mapping of query condition names to the query condition type."
  {:entry-title :string
   :entry-id :string
   :provider :string
   :or :or
   :and :and
   :not :not})

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
  [condition-name values]
  (gc/and-conds (map #(parse-json-condition-map %) values)))

;; Example {"or": [{"entry-title": "ET", "provider": "PROV1"}
;;                 {"revision-date": [null, "2015-04-01T00:00:00Z"]}]}
(defmethod parse-json-condition :or
  [condition-name values]
  (gc/or-conds (map #(parse-json-condition-map %) values)))

;; Example {"not": {"entry-title": "ET",
;;                  "provider": "PROV1",
;;                  "revision-date": [null, "2015-04-01T00:00:00Z"]}}
(defmethod parse-json-condition :not
  [condition-name value]
  (qm/negated-condition (parse-json-condition-map value)))

(defn- validate-json-conditions
  "Validates that the condition names in the query are valid"
  [concept-type json-query]
  (let [all-condition-names (util/get-keys-in json-query)
        valid-conditions (get valid-conditions-for-concept-type concept-type)
        invalid-conditions (set/difference all-condition-names valid-conditions)]
    (when (seq invalid-conditions)
      (errors/throw-service-error :bad-request
                                  (msg/invalid-json-condition-names-msg concept-type
                                                                        invalid-conditions)))))

(defn parse-json-query
  "Converts a JSON query string and query parameters into a query model."
  [concept-type params json-string]
  (let [params (pv/validate-standard-query-parameters concept-type params)
        json-query (util/map-keys->kebab-case (json/parse-string json-string true))]
    (validate-json-conditions concept-type json-query)
    (qm/query (assoc (pc/standard-params->query-attribs concept-type params)
                     :concept-type concept-type
                     :condition (parse-json-condition-map json-query)))))

