(ns cmr.indexer.services.granule
  "Contains functions to parse and convert granule concept"
  (:require [clojure.string :as s]
            [clj-time.format :as f]
            [cmr.indexer.services.index-service :as idx]
            [cmr.umm.echo10.granule :as granule]
            [cmr.indexer.data.metadata-db :as mdb]
            [cmr.common.services.errors :as errors]
            [cmr.indexer.services.temporal :as temporal]
            [cmr.umm.echo10.collection.product-specific-attribute :as coll-psa]))

(defmethod idx/parse-concept :granule
  [concept]
  (granule/parse-granule (:metadata concept)))

(defn- get-parent-collection
  [context parent-collection-id]
  (let [concept (mdb/get-latest-concept context parent-collection-id)]
    ;; Concept id associated with parsed data to use in error messages.
    (assoc (idx/parse-concept concept) :concept-id parent-collection-id)))

(defmulti value->elastic-value
  "Converts a attribute value into the elastic value that should be transmitted"
  (fn [type value]
    type))

(defmethod value->elastic-value :default
  [type value]
  value)

(defmethod value->elastic-value :datetime
  [type value]
  (f/unparse (f/formatters :date-time) value))

(defmethod value->elastic-value :time
  [type value]
  ;; This relies on the fact that times are parsed into times on day 1970-01-01
  (f/unparse (f/formatters :date-time) value))

(defmethod value->elastic-value :date
  [type value]
  (f/unparse (f/formatters :date-time) value))

(defn psa-ref->elastic-doc
  "Converts a PSA ref into the correct elastic document"
  [type psa-ref]
  (let [field-name (str (name type) "-value")]
    {:name (:name psa-ref)
     field-name (map (comp #(value->elastic-value type %)
                           #(coll-psa/parse-value type %))
                     (:values psa-ref))}))

(defn psa-refs->elastic-docs
  "Converts the psa-refs into a list of elastic documents"
  [collection granule]
  (let [parent-type-map (into {} (for [psa (:product-specific-attributes collection)]
                                   [(:name psa) (:data-type psa)]))]
    (map (fn [psa-ref]
           (let [type (parent-type-map (:name psa-ref))]
             (when-not type
               (errors/internal-error! (format "Could not find parent attribute [%s] in collection [%s]"
                                               (:name psa-ref)
                                               (:concept-id collection))))
             (psa-ref->elastic-doc type psa-ref)))
         (:product-specific-attributes granule))))


(defmethod idx/concept->elastic-doc :granule
  [context concept umm-granule]
  (let [{:keys [concept-id extra-fields provider-id]} concept
        {:keys [parent-collection-id]} extra-fields
        parent-collection (get-parent-collection context parent-collection-id)
        {:keys [granule-ur temporal]} umm-granule
        start-date (temporal/start-date :granule temporal)
        end-date (temporal/end-date :granule temporal)]
    {:concept-id concept-id
     :collection-concept-id parent-collection-id
     :provider-id provider-id
     :provider-id.lowercase (s/lower-case provider-id)
     :granule-ur granule-ur
     :granule-ur.lowercase (s/lower-case granule-ur)
     :attributes (psa-refs->elastic-docs parent-collection umm-granule)
     :start-date (when start-date (f/unparse (f/formatters :date-time) start-date))
     :end-date (when end-date (f/unparse (f/formatters :date-time) end-date))}))
