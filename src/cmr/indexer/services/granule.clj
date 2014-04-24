(ns cmr.indexer.services.granule
  "Contains functions to parse and convert granule concept"
  (:require [clojure.string :as s]
            [cmr.indexer.services.index-service :as idx]
            [cmr.umm.echo10.granule :as granule]
            [cmr.indexer.data.metadata-db :as mdb]
            [cmr.common.services.errors :as errors]))

(defmethod idx/parse-concept :granule
  [concept]
  (granule/parse-granule (:metadata concept)))

(defn- get-parent-collection
  [context parent-collection-id]
  (let [concept (mdb/get-latest-concept context parent-collection-id)]
    ;; Concept id associated with parsed data to use in error messages.
    (assoc (idx/parse-concept concept) :concept-id parent-collection-id)))

(comment
  (get-parent-collection {} "C1000000000-PROV1")


  )

(defn psa-ref->elastic-doc
  "Converts a PSA ref into the correct elastic document"
  [type psa-ref]
  (let [field-name (str (name type) "-value")]
    {:name (:name psa-ref)
     field-name (:values psa-ref)}))

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
        {:keys [granule-ur]} umm-granule
        parent-collection (get-parent-collection context parent-collection-id)]
    {:concept-id concept-id
     :collection-concept-id parent-collection-id
     :provider-id provider-id
     :provider-id.lowercase (s/lower-case provider-id)
     :granule-ur granule-ur
     :granule-ur.lowercase (s/lower-case granule-ur)
     :attributes (psa-refs->elastic-docs parent-collection umm-granule)}))




