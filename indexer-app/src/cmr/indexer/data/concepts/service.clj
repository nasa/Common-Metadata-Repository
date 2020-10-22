(ns cmr.indexer.data.concepts.service
  "Contains functions to parse and convert service and service association concepts."
  (:require
   [clojure.string :as string]
   [cmr.common.concepts :as concepts]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.indexer.data.concept-parser :as concept-parser]
   [cmr.indexer.data.concepts.service-keyword-util :as service-keyword-util]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.transmit.metadata-db :as mdb]))

(defmethod es/parsed-concept->elastic-doc :service
  [context concept parsed-concept]
  (let [{:keys [concept-id revision-id deleted provider-id native-id user-id
                revision-date format extra-fields]} concept
        {:keys [service-name]} extra-fields
        long-name (:LongName parsed-concept)
        service-type (:Type parsed-concept)
        schema-keys [:LongName
                     :Name
                     :Type
                     :Version
                     :AncillaryKeywords
                     :ContactGroups
                     :ContactPersons
                     :URL
                     :ServiceKeywords
                     :ServiceOrganizations]
        keyword-values (service-keyword-util/concept-keys->keyword-text
                        parsed-concept schema-keys)]
    (if deleted
      {:concept-id concept-id
       :revision-id revision-id
       :deleted deleted
       :service-name service-name
       :service-name-lowercase (string/lower-case service-name)
       :provider-id provider-id
       :provider-id-lowercase (string/lower-case provider-id)
       :native-id native-id
       :native-id-lowercase (string/lower-case native-id)
       :keyword keyword-values
       :user-id user-id
       :revision-date revision-date}
      {:concept-id concept-id
       :revision-id revision-id
       :deleted deleted
       :service-name service-name
       :service-name-lowercase (string/lower-case service-name)
       :service-type-lowercase (string/lower-case service-type)
       :long-name long-name
       :long-name-lowercase (string/lower-case long-name)
       :provider-id provider-id
       :provider-id-lowercase (string/lower-case provider-id)
       :native-id native-id
       :native-id-lowercase (string/lower-case native-id)
       :keyword keyword-values
       :user-id user-id
       :revision-date revision-date
       :metadata-format (name (mt/format-key format))})))

(defn- service-associations->service-concepts
  "Returns the service concepts for the given service associations."
  [context service-associations]
  (let [service-concept-ids (map :service-concept-id service-associations)
        service-concepts (mdb/get-latest-concepts context service-concept-ids true)]
    (filter #(not (:deleted %)) service-concepts)))

(defn- has-formats?
  "Returns true if the given service has more than one supported formats value."
  [context service-concept]
  (let [service (concept-parser/parse-concept context service-concept)
        format-pairs (get-in service [:ServiceOptions :SupportedReformattings])
        input-formats (distinct (map :SupportedInputFormat format-pairs))
        output-formats (distinct (mapcat :SupportedOutputFormats format-pairs))
        distinct-input-output (distinct (concat input-formats output-formats))]
    (and (not= (count output-formats) 0)
         (> (count distinct-input-output) 1))))

(defn- has-subset-type?
  "Returns true if the given service has a defined Subset with one of its
  values matches the given subset type."
  [context service-concept subset-type]
  (let [service (concept-parser/parse-concept context service-concept)
        {{subset-types :Subset} :ServiceOptions} service]
    (and (seq subset-types)
         (-> subset-types
             (subset-type)
             (some?)))))

(defn- has-spatial-subsetting?
  "Returns true if the given service has a defined SubsetType with one of its
  values being 'Spatial'."
  [context service-concept]
  (has-subset-type? context service-concept :SpatialSubset))

(defn- has-temporal-subsetting?
  "Returns true if the given service has a defined SubsetType with one of its
  values being 'Temporal'."
  [context service-concept]
  (has-subset-type? context service-concept :TemporalSubset))

(defn- has-variables?
  "Returns true if the given service has a defined SubsetType with one of its
  values being 'Variable'."
  [context service-concept]
  (has-subset-type? context service-concept :VariableSubset))

(defn- has-transforms?
  "Returns true if the given service has a defined SubsetTypes or InterpolationTypes,
  or multiple supported projections values."
  [context service-concept]
  (let [service (concept-parser/parse-concept context service-concept)
        {service-options :ServiceOptions} service
        {interpolation-types :InterpolationTypes
         input-projections :SupportedInputProjections
         output-projections :SupportedOutputProjections} service-options
        supported-projections (distinct (concat
                                         (map :ProjectionName input-projections)
                                         (map :ProjectionName output-projections)))]
    (or (seq interpolation-types)
        (> (count supported-projections) 1))))

(defn- get-service-type
  "Get the service type from the service metadata that exists in the service-concept so that it can be indexed with the 
   collection when associating a service with a collection." 
  [context service-concept]
  (:Type (concept-parser/parse-concept context service-concept)))

(defn service-associations->elastic-doc
  "Converts the service association into the portion going in the collection elastic document."
  [context service-associations]
  (let [service-concepts (service-associations->service-concepts context service-associations)
        service-names (map #(get-in % [:extra-fields :service-name]) service-concepts)
        service-types (map #(get-service-type context %) service-concepts)
        service-concept-ids (map :concept-id service-concepts)]
    {:service-names service-names
     :service-names-lowercase (map string/lower-case service-names)
     :service-types-lowercase (map string/lower-case service-types)
     :service-concept-ids service-concept-ids
     :has-formats (boolean (some #(has-formats? context %) service-concepts))
     :has-spatial-subsetting (boolean (some #(has-spatial-subsetting? context %) service-concepts))
     :has-temporal-subsetting (boolean (some #(has-temporal-subsetting? context %) service-concepts))
     :has-transforms (boolean (some #(has-transforms? context %) service-concepts))
     :has-variables (boolean (some #(has-variables? context %) service-concepts))}))
