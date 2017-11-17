(ns cmr.indexer.data.concepts.service
  "Contains functions to parse and convert service and service association concepts."
  (:require
   [clojure.string :as string]
   [cmr.common.concepts :as concepts]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.indexer.data.concept-parser :as concept-parser]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.transmit.metadata-db :as mdb]))

(defmethod es/parsed-concept->elastic-doc :service
  [context concept parsed-concept]
  (let [{:keys [concept-id revision-id deleted provider-id native-id user-id
                revision-date format extra-fields]} concept
        {:keys [service-name]} extra-fields
        concept-seq-id (:sequence-number (concepts/parse-concept-id concept-id))]
    (if deleted
      ;; This is only called by re-indexing (bulk indexing)
      ;; Regular deleted variables would have gone through the index-service/delete-concept path.
      {:concept-id concept-id
       :revision-id revision-id
       :concept-seq-id concept-seq-id
       :deleted deleted
       :service-name service-name
       :service-name.lowercase (string/lower-case service-name)
       :provider-id provider-id
       :provider-id.lowercase (string/lower-case provider-id)
       :native-id native-id
       :native-id.lowercase (string/lower-case native-id)
       :user-id user-id
       :revision-date revision-date}
      {:concept-id concept-id
       :revision-id revision-id
       :concept-seq-id concept-seq-id
       :deleted deleted
       :service-name service-name
       :service-name.lowercase (string/lower-case service-name)
       :provider-id provider-id
       :provider-id.lowercase (string/lower-case provider-id)
       :native-id native-id
       :native-id.lowercase (string/lower-case native-id)
       :user-id user-id
       :revision-date revision-date
       :metadata-format (name (mt/format-key format))})))

(defn- service-association->service-concept
  "Returns the service concept and service association for the given service association."
  [context service-association]
  (let [{:keys [service-concept-id]} service-association
        service-concept (mdb/find-latest-concept
                         context
                         {:concept-id service-concept-id}
                         :service)]
    (when-not (:deleted service-concept)
      service-concept)))

(defn- has-formats?
  "Returns true if the given service has more than one SupportedFormats value."
  [context service-concept]
  (let [service (concept-parser/parse-concept context service-concept)
        supported-formats (get-in service [:ServiceOptions :SupportedFormats])]
    (> (count supported-formats) 1)))

(defn- has-spatial-subsetting?
  "Returns true if the given service has a defined SubsetType with one of its
  values being 'spatial'."
  [context service-concept]
  (let [service (concept-parser/parse-concept context service-concept)
        {{subset-type :SubsetType} :ServiceOptions} service]
    (and (seq subset-type)
         (contains? (set subset-type) "Spatial"))))

(defn- has-transforms?
  "Returns true if the given service has a defined SubsetType or InterpolationType,
  or multiple SupportedProjections values."
  [context service-concept]
  (let [service (concept-parser/parse-concept context service-concept)
        {service-options :ServiceOptions} service
        {subset-type :SubsetType
         interpolation-type :InterpolationType
         supported-projections :SupportedProjections} service-options]
    (or (seq subset-type)
        (seq interpolation-type)
        (> (count supported-projections) 1))))

(defn service-associations->elastic-doc
  "Converts the service association into the portion going in the collection elastic document."
  [context service-associations]
  (let [service-concepts (remove nil?
                                 (map #(service-association->service-concept context %)
                                      service-associations))]
    {:has-formats (boolean (some #(has-formats? context %) service-concepts))
     :has-spatial-subsetting (boolean (some #(has-spatial-subsetting? context %) service-concepts))
     :has-transforms (boolean (some #(has-transforms? context %) service-concepts))}))
