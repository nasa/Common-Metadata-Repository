(ns cmr.search.services.json-parameters.conversion
  "Contains functions for parsing and converting JSON queries into query conditions"
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [cmr.common.services.errors :as errors]
            [cmr.common.util :as util]
            [cmr.common.concepts :as cc]
            [cmr.common.date-time-parser :as parser]
            [cmr.common.validations.json-schema :as js]
            [cmr.search.models.query :as qm]
            [cmr.search.models.group-query-conditions :as gc]
            [cmr.search.services.parameters.legacy-parameters :as lp]
            [cmr.search.services.parameters.conversion :as pc]
            [cmr.search.services.parameters.parameter-validation :as pv]
            [cmr.search.services.messages.common-messages :as msg]
            [cmr.search.services.parameters.converters.science-keyword :as psk]))

(def json-query-schema
  "JSON Schema for querying for collections."
  (-> (io/resource "schema/JSONQueryLanguage.json")
      slurp
      js/json-string->JsonSchema))

(def query-condition-name->condition-type-map
  "A mapping of query condition names to the query condition type."
  {:entry-title :string
   :entry-id :string
   :provider :string
   :short-name :string
   :version :string
   :processing-level-id :string
   :concept-id :string
   :platform :string
   :instrument :string
   :sensor :string
   :project :string
   :archive-center :string
   :spatial-keyword :string
   :two-d-coordinate-system-name :string
   :keyword :keyword
   :or :or
   :and :and
   :not :not
   :science-keywords :science-keywords})

(defn- query-condition-name->condition-type
  "Returns the query condition type based on the given concept-type and param-name."
  [condition-name]
  (condition-name query-condition-name->condition-type-map))

(defn- case-sensitive-field?
  "Return true if the given field should be searched case-sensitive"
  ([field]
   (case-sensitive-field? field {}))
  ([field value-map]
   (or (contains? pc/always-case-sensitive-fields field)
       (= false (:ignore-case value-map)))))

(defmulti parse-json-condition
  "Converts a JSON query condition into a query model condition"
  (fn [condition-name value]
    (query-condition-name->condition-type condition-name)))

(defmethod parse-json-condition :default
  [condition-name value]
  (errors/internal-error!
    (format "Could not find parameter handler for [%s]" condition-name)))

(defmethod parse-json-condition :string
  [condition-name value]
  (if (map? value)
    (qm/string-condition condition-name
                         (:value value)
                         (case-sensitive-field? condition-name value)
                         (:pattern value))
    (qm/string-condition condition-name value (case-sensitive-field? condition-name) false)))

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

(defmethod parse-json-condition :and
  [_ values]
  (gc/and-conds (map #(parse-json-condition-map %) values)))

(defmethod parse-json-condition :or
  [_ values]
  (gc/or-conds (map #(parse-json-condition-map %) values)))

;; Example {"not": {"entry_title": "ET"}}
(defmethod parse-json-condition :not
  [_ value]
  (qm/negated-condition (parse-json-condition-map value)))

(defmethod parse-json-condition :science-keywords
  [condition-name value]
  (psk/parse-nested-science-keyword-condition value
                                              (case-sensitive-field? condition-name value)
                                              (:pattern value)))

(defn- concept-type-validation
  "Validates the provided concept type is valid for JSON query."
  [concept-type]
  (when-not (= :collection concept-type)
    (errors/throw-service-error :bad-request (msg/json-query-unsupported-msg concept-type))))

(defn- validate-json-query
  "Perform all validations against the provided JSON query."
  [concept-type json-query]
  (concept-type-validation concept-type)
  (js/validate-json json-query-schema json-query))

(defn parse-json-query
  "Converts a JSON query string and query parameters into a query model."
  [concept-type params json-string]
  (let [params (pv/validate-standard-query-parameters concept-type params)]
    (validate-json-query concept-type json-string)
    (qm/query (assoc (pc/standard-params->query-attribs concept-type params)
                     :concept-type concept-type
                     :condition (-> (json/parse-string json-string true)
                                    util/map-keys->kebab-case
                                    parse-json-condition-map)))))
