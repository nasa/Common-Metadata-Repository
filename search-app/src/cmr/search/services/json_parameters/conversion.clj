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
            [cmr.search.services.parameters.converters.nested-field :as nf]
            [cmr.search.services.tagging.tag-related-item-condition :as tag-related]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.validation :as sv]
            [inflections.core :as inf]))

(def json-query-schema
  "JSON Schema for querying for collections."
  (-> (io/resource "schema/JSONQueryLanguage.json")
      str
      js/parse-json-schema-from-uri))

(def query-condition-name->condition-type-map
  "A mapping of query condition names to the query condition type."
  {;; collection query params
   :entry-title :string
   :entry-id :string
   :provider :string
   :short-name :string
   :version :string
   :processing-level-id :string
   :concept-id :string
   :platform :nested-condition
   :instrument :nested-condition
   :sensor :string
   :project :string
   :archive-center :nested-condition
   :spatial-keyword :string
   :two-d-coordinate-system-name :string
   :keyword :keyword
   :bounding-box :bounding-box
   :temporal :temporal
   :updated-since :updated-since
   :science-keywords :nested-condition
   :tag :tag

   ;; query constructs
   :or :or
   :and :and
   :not :not})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validations

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

(defn- validate-nested-condition
  "Custom validation to make sure there is at least one subfield being searched on in a nested
  condition. JSON schema does not provide a mechanism for ensuring at least one of a subset of
  properties is present."
  [condition-name value]
  (when-not (seq (set/intersection
                   (set (keys value))
                   (set (nf/get-subfield-names (inf/plural condition-name)))))
    (errors/throw-service-error
      :bad-request (msg/invalid-nested-json-query-condition condition-name value))))

(defn- validate-temporal-condition
  "Custom validation to make sure there is at least one temporal condition other than
  exclude_boundary."
  [value]
  (when (empty? (dissoc value :exclude-boundary))
    (errors/throw-service-error
      :bad-request "Temporal condition with only exclude_boundary is invalid.")))

(defn- validate-tag-condition
  "Custom validation to make sure that originator_id cannot have ignore_case options."
  [value]
  (when (get-in value [:originator-id :ignore-case])
    (errors/throw-service-error
      :bad-request "Option [ignore_case] is not supported for tag originator_id.")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defn- parse-json-string-condition
  "Returns the parsed json string condition for the given name and value."
  [condition-name value]
  (if (map? value)
    (qm/string-condition condition-name
                         (:value value)
                         (case-sensitive-field? condition-name value)
                         (:pattern value))
    (qm/string-condition condition-name value (case-sensitive-field? condition-name) false)))

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
  (parse-json-string-condition condition-name value))

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

(defmethod parse-json-condition :nested-condition
  [condition-name value]
  (validate-nested-condition condition-name value)
  (nf/parse-nested-condition (inf/plural condition-name) value
                             (case-sensitive-field? condition-name value)
                             (:pattern value)))

(defmethod parse-json-condition :bounding-box
  [_ value]
  (let [bounding-box (if (map? value)
                       (mbr/mbr (:west value) (:north value) (:east value) (:south value))
                       (let [[west south east north] value]
                         (mbr/mbr west north east south)))]
    (sv/validate bounding-box)
    (qm/->SpatialCondition bounding-box)))

(defmethod parse-json-condition :updated-since
  [_ value]
  (qm/map->DateRangeCondition
    {:field :updated-since
     :start-date (parser/parse-datetime value)
     :end-date nil}))

(defmethod parse-json-condition :temporal
  [_ value]
  (validate-temporal-condition value)
  (let [{:keys [start-date end-date recurring-start-day recurring-end-day exclude-boundary]} value]
    (qm/map->TemporalCondition {:start-date (when-not (str/blank? start-date)
                                              (parser/parse-datetime start-date))
                                :end-date (when-not (str/blank? end-date)
                                            (parser/parse-datetime end-date))
                                :start-day recurring-start-day
                                :end-day recurring-end-day
                                :exclusive? exclude-boundary})))

(defmethod parse-json-condition :tag
  [_ value]
  (validate-tag-condition value)
  (let [parse-tag-condition (fn [[cond-name cond-value]]
                              (tag-related/tag-related-item-query-condition
                                (parse-json-string-condition cond-name cond-value)))]
    (gc/and-conds (map parse-tag-condition value))))

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
