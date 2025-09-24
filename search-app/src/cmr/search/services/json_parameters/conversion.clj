(ns cmr.search.services.json-parameters.conversion
  "Contains functions for parsing and converting JSON queries into query conditions"
  (:require
   [cheshire.core :as json]
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.elastic-utils.search.es-group-query-conditions :as gc]
   [cmr.elastic-utils.search.nested-field :as nf]
   [cmr.elastic-utils.search.es-params-converter :as common-params]
   [cmr.common.services.search.query-model :as cqm]
   [cmr.elastic-utils.search.es-query-to-elastic :as q2e]
   [cmr.common.date-time-parser :as parser]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.common.validations.json-schema :as js]
   [cmr.search.models.query :as qm]
   [cmr.search.services.messages.common-messages :as msg]
   [cmr.search.services.parameters.converters.range-facet :as range-facet]
   [cmr.search.services.parameters.parameter-validation :as pv]
   [cmr.search.validators.all-granule-validation :as all-granule-validation]
   [cmr.spatial.mbr :as mbr]
   [cmr.spatial.validation :as sv]
   [inflections.core :as inf]))

(def json-query-schema-collections
  "JSON Schema for querying for collections."
  (js/parse-json-schema-from-path "schema/CollectionJSONQueryLanguage.json"))

(def json-query-schema-granules
  "JSON Schema for querying for granules."
  (js/parse-json-schema-from-path "schema/GranuleJSONQueryLanguage.json"))

(def query-condition-name->condition-type-map-granules
  {:entry-title :string
   :provider :string
   :short-name :string
   :version :string
   :concept-id :string
   :platform :string
   :instrument :string
   :project :string
   :spatial-keyword :string
   :bounding-box :bounding-box
   :temporal :temporal
   :updated-since :updated-since
   :collection-concept-id :string

   ;; query constructs
   :or :or
   :and :and
   :not :not})

(def query-condition-name->condition-type-map-collections
  "A mapping of query condition names to the query condition type."
  {;; collection query params
   :entry-title :string
   :doi :string
   :native-id :string
   :entry-id :string
   :provider :string
   :short-name :string
   :version :string
   :processing-level-id :string
   :concept-id :string
   :platform :nested-condition  ;; keep supporting the old parameter
   :platforms :nested-condition ;; used for v2 facet apply
   :instrument :nested-condition
   :sensor :string
   :project :string
   :archive-center :nested-condition
   :data-center :nested-condition
   :spatial-keyword :string
   :location-keyword :nested-condition
   :two-d-coordinate-system-name :string
   :horizontal-data-resolution-range :range-facet
   :keyword :keyword
   :bounding-box :bounding-box
   :temporal :temporal
   :updated-since :updated-since
   :science-keywords :nested-condition
   :additional-attribute-name :additional-attribute
   :additional-attribute-value :additional-attribute
   :additional-attribute-range :additional-attribute
   :tag :nested-condition
   :identifier :string

   ;; query constructs
   :or :or
   :and :and
   :not :not})

(defn- concept-type->json-query-schema
  "Returns route to json query schema for given concept-type"
  [concept-type]
  (if (= :granule concept-type)
    json-query-schema-granules
    json-query-schema-collections))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validations

(defn- concept-type-validation
  "Validates the provided concept type is valid for JSON query."
  [concept-type]
  (when-not (contains? #{:granule :collection} concept-type)
    (errors/throw-service-error :bad-request (msg/json-query-unsupported-msg concept-type))))

(defn- validate-json-query
  "Perform all validations against the provided JSON query."
  [concept-type json-query]
  (concept-type-validation concept-type)
  (when-not json-query
    (errors/throw-service-errors :invalid-data ["JSON query cannot be empty"]))
  (when-let [errors (seq (js/validate-json (concept-type->json-query-schema concept-type) json-query))]
    (errors/throw-service-errors :bad-request errors)))

(def ^:private tag-subfields
  "Defines a map of :tag to its subfields for nested condition."
  {:tag [:tag-key :originator-id]})

(defn- validate-nested-condition
  "Custom validation to make sure there is at least one subfield being searched on in a nested
  condition. JSON schema does not provide a mechanism for ensuring at least one of a subset of
  properties is present."
  [condition-name value]
  (when-not (seq (set/intersection
                   (set (keys value))
                   (set (concat (nf/get-subfield-names (inf/plural condition-name))
                                (tag-subfields condition-name)))))
    (errors/throw-service-error
      :bad-request (msg/invalid-nested-json-query-condition condition-name value))))

(defn- validate-temporal-condition
  "Custom validation to make sure there is at least one temporal condition other than
  exclude_boundary."
  [value]
  (when (empty? (dissoc value :exclude-boundary))
    (errors/throw-service-error
      :bad-request "Temporal condition with only exclude_boundary is invalid.")))

(defn- validate-all-granules
  "Validates json query for granules to prevent all granule queries."
  [concept-type query]
  (when (= :granule concept-type)
    (when-let [error (all-granule-validation/no-all-granules-for-json-query query)]
      (errors/throw-service-error
       :bad-request error))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- query-condition-name->condition-type
  "Returns the query condition type based on the given concept-type and condition-name."
  [concept-type condition-name]
  (if (= :granule concept-type)
    (get query-condition-name->condition-type-map-granules condition-name)
    (get query-condition-name->condition-type-map-collections condition-name)))

(defn- case-sensitive-field?
  "Return true if the given field should be searched case-sensitive"
  ([concept-type field]
   (case-sensitive-field? concept-type field {}))
  ([concept-type field value-map]
   (or (contains? (common-params/always-case-sensitive-fields concept-type) field)
       (= false (:ignore-case value-map)))))

(defn- parse-json-string-condition
  "Returns the parsed json string condition for the given name and value."
  [concept-type condition-name value]
  (if (map? value)
    (cqm/string-condition condition-name
                          (:value value)
                          (case-sensitive-field? concept-type condition-name value)
                          (:pattern value))
    (cqm/string-condition condition-name
                          value
                          (case-sensitive-field? concept-type condition-name)
                          false)))

(defmulti parse-json-condition
  "Converts a JSON query condition into a query model condition"
  (fn [concept-type condition-name value]
    (query-condition-name->condition-type concept-type condition-name)))

(defmethod parse-json-condition :default
  [concept-type condition-name value]
  (errors/internal-error!
    (format "Could not find parameter handler for [%s]" condition-name)))

(defmethod parse-json-condition :string
  [concept-type condition-name value]
  (parse-json-string-condition concept-type condition-name value))

(defmethod parse-json-condition :keyword
  [concept-type _ value]
  (cqm/text-condition :keyword (string/lower-case value)))

(defn- parse-json-condition-map
  "Parse a JSON condition map into the appropriate query conditions. Conditions within a map are
  implicitly and'ed together."
  [concept-type condition-map]
  (when-let [query-conditions (seq (for [[k v] condition-map]
                                     (parse-json-condition concept-type k v)))]
    (gc/and-conds query-conditions)))

(defmethod parse-json-condition :and
  [concept-type _ values]
  (gc/and-conds (map #(parse-json-condition-map concept-type %) values)))

(defmethod parse-json-condition :or
  [concept-type _ values]
  (gc/or-conds (map #(parse-json-condition-map concept-type %) values)))

;; Example {"not": {"entry_title": "ET"}}
(defmethod parse-json-condition :not
  [concept-type _ value]
  (cqm/negated-condition (parse-json-condition-map concept-type value)))

(defmethod parse-json-condition :nested-condition
  [concept-type condition-name value]
  (validate-nested-condition condition-name value)
  (let [elastic-field-name (-> condition-name
                               (inf/plural)
                               (q2e/query-field->elastic-field concept-type))]
    (nf/parse-nested-condition elastic-field-name
                               value
                               (case-sensitive-field? concept-type condition-name value)
                               (:pattern value))))

(defmethod parse-json-condition :bounding-box
  [concept-type _ value]
  (let [bounding-box (if (map? value)
                       (mbr/mbr (:west value) (:north value) (:east value) (:south value))
                       (let [[west south east north] value]
                         (mbr/mbr west north east south)))]
    (sv/validate bounding-box)
    (qm/->SpatialCondition bounding-box)))

(defmethod parse-json-condition :updated-since
  [concept-type _ value]
  (cqm/map->DateRangeCondition
    {:field :updated-since
     :start-date (parser/parse-datetime value)
     :end-date nil}))

(defmethod parse-json-condition :temporal
  [concept-type _ value]
  (validate-temporal-condition value)
  (let [{:keys [start-date end-date recurring-start-day recurring-end-day exclude-boundary]} value]
    (qm/map->TemporalCondition {:start-date (when-not (string/blank? start-date)
                                              (parser/parse-datetime start-date))
                                :end-date (when-not (string/blank? end-date)
                                            (parser/parse-datetime end-date))
                                :start-day recurring-start-day
                                :end-day recurring-end-day
                                :exclusive? exclude-boundary})))

;; Converts a JSON query string and query parameters into a range facet elastic search query.
(defmethod parse-json-condition :range-facet
  [concept-type condition-name value]
  (range-facet/range-facet->condition concept-type condition-name value))

(defn parse-json-query
  "Converts a JSON query string and query parameters into a query model."
  [concept-type params json-string]
  (let [params (pv/validate-standard-query-parameters concept-type params)]
    (validate-json-query concept-type json-string)
    (let [query (cqm/query (assoc (second (common-params/parse-query-level-params concept-type params))
                                  :concept-type concept-type
                                  :condition (->> (json/parse-string json-string true)
                                                  util/map-keys->kebab-case
                                                  :condition
                                                  (parse-json-condition-map concept-type))))]

      (validate-all-granules concept-type query)
      query)))
