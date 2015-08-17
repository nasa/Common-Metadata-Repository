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
            [cmr.search.services.parameters.converters.science-keyword :as psk]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.validation :as sv]))

(def json-query-schema
  "JSON Schema for querying for collections."
  (-> (io/resource "schema/JSONQueryLanguage.json")
      str
      js/parse-json-schema-from-uri))

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
   :bounding-box :bounding-box
   :temporal :temporal
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

(defn- validate-science-keywords
  "Custom validation to make sure there is at least one science keyword field being searched on.
  JSON schema does not provide a mechanism for ensuring at least one of a subset of properties is
  present."
  [value]
  (when-not (seq (set/intersection (set (keys value))
                                   (conj psk/science-keyword-fields :any)))
    (errors/throw-service-error :bad-request (msg/invalid-science-keyword-json-query value))))

(defn- validate-temporal
  "Custom validation to make sure there is at least one temporal condition other than exclude_boundary."
  [value]
  (when (empty? (dissoc value :exclude-boundary))
    (errors/throw-service-error
      :bad-request "Temporal condition with only exclude_boundary is invalid.")))

(defmethod parse-json-condition :science-keywords
  [condition-name value]
  (validate-science-keywords value)
  (psk/parse-nested-science-keyword-condition value
                                              (case-sensitive-field? condition-name value)
                                              (:pattern value)))

(defmethod parse-json-condition :bounding-box
  [condition-name value]
  (let [bounding-box (if (map? value)
                       (mbr/mbr (:west value) (:north value) (:east value) (:south value))
                       (let [[west south east north] value]
                         (mbr/mbr west north east south)))]
    (sv/validate bounding-box)
    (qm/->SpatialCondition bounding-box)))

(defmethod parse-json-condition :temporal
  [condition-name value]
  (validate-temporal value)
  (let [{:keys [start-date end-date recurring-start-day recurring-end-day exclude-boundary]} value]
    (qm/map->TemporalCondition {:start-date (when-not (str/blank? start-date) (parser/parse-datetime start-date))
                                :end-date (when-not (str/blank? end-date) (parser/parse-datetime end-date))
                                :start-day recurring-start-day
                                :end-day recurring-end-day
                                :exclusive? exclude-boundary})))

(defn- concept-type-validation
  "Validates the provided concept type is valid for JSON query."
  [concept-type]
  (when-not (= :collection concept-type)
    (errors/throw-service-error :bad-request (msg/json-query-unsupported-msg concept-type))))

(defn- validate-json-query
  "Perform all validations against the provided JSON query."
  [concept-type json-query]
  (concept-type-validation concept-type)
  (when-let [errors (seq (js/validate-json json-query-schema json-query))]
    (errors/throw-service-errors :bad-request errors)))

(defn parse-json-query
  "Converts a JSON query string and query parameters into a query model."
  [concept-type params json-string]
  (let [params (pv/validate-standard-query-parameters concept-type params)]
    (validate-json-query concept-type json-string)
    (qm/query (assoc (pc/standard-params->query-attribs concept-type params)
                     :concept-type concept-type
                     :condition (-> (json/parse-string json-string true)
                                    util/map-keys->kebab-case
                                    :condition
                                    parse-json-condition-map)))))
