(ns cmr.indexer.data.concepts.service
  "Contains functions to parse and convert service and service association concepts."
  (:require
   [clojure.string :as string]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.indexer.data.concept-parser :as concept-parser]
   [cmr.indexer.data.concepts.service-keyword-util :as service-keyword-util]
   [cmr.indexer.data.concepts.association-util :as assoc-util]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.transmit.metadata-db :as mdb]))

(defmethod es/parsed-concept->elastic-doc :service
  [_context concept parsed-concept]
  (let [{:keys [concept-id revision-id deleted provider-id native-id user-id
                revision-date format extra-fields service-associations generic-associations]} concept
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
                        parsed-concept schema-keys)
        all-assocs (concat service-associations generic-associations)]
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
       :metadata-format (name (mt/format-key format))
       :associations-gzip-b64 (assoc-util/associations->gzip-base64-str all-assocs concept-id)})))

(defn- service-associations->service-concepts
  "Returns the service concepts for the given service associations."
  [context service-associations]
  (let [service-concept-ids (map :service-concept-id service-associations)
        service-concepts (mdb/get-latest-concepts context service-concept-ids true)]
    (filter #(not (:deleted %)) service-concepts)))

(defn- has-formats?
  "Returns true if the given service has more than one supported formats value."
  [service]
  (let [format-pairs (get-in service [:ServiceOptions :SupportedReformattings])
        input-formats (distinct (map :SupportedInputFormat format-pairs))
        output-formats (distinct (mapcat :SupportedOutputFormats format-pairs))
        distinct-input-output (distinct (concat input-formats output-formats))]
    (and (not (zero? (count output-formats)))
         (> (count distinct-input-output) 1))))

(defn- has-subset-type?
  "Returns true if the given service has a defined Subset with one of its
  values matches the given subset type."
  [service subset-type]
  (let [{{subset-types :Subset} :ServiceOptions} service]
    (and (seq subset-types)
         (-> subset-types
             (subset-type)
             (some?)))))

(defn- has-spatial-subsetting?
  "Returns true if the given service has a defined SubsetType with one of its
  values being 'Spatial'."
  [service]
  (has-subset-type? service :SpatialSubset))

(defn- has-temporal-subsetting?
  "Returns true if the given service has a defined SubsetType with one of its
  values being 'Temporal'."
  [service]
  (has-subset-type? service :TemporalSubset))

(defn- has-variables?
  "Returns true if the given service has a defined SubsetType with one of its
  values being 'Variable'."
  [service]
  (has-subset-type? service :VariableSubset))

(defn- has-transforms?
  "Returns true if the given service has a defined SubsetTypes or InterpolationTypes,
  or multiple supported projections values."
  [service]
  (let [{service-options :ServiceOptions} service
        {interpolation-types :InterpolationTypes
         input-projections :SupportedInputProjections
         output-projections :SupportedOutputProjections} service-options
        supported-projections (distinct (concat
                                         (map :ProjectionName input-projections)
                                         (map :ProjectionName output-projections)))]
    (or (seq interpolation-types)
        (> (count supported-projections) 1))))

(defn- get-has-features
  "Returns the has features for the given services"
  [services]
  {:has-formats (boolean (some has-formats? services))
   :has-transforms (boolean (some has-transforms? services))
   :has-variables (boolean (some has-variables? services))
   :has-spatial-subsetting (boolean (some has-spatial-subsetting? services))
   :has-temporal-subsetting (boolean (some has-temporal-subsetting? services))})

(defn- get-trimmed-has-features
  "Returns the has features for the given services with false features trimmed off"
  [services]
  (->> services
       get-has-features
       (util/remove-map-keys false?)))

(defn- get-service-features
  "Returns the service features for the list of services"
  [services]
  (let [opendap-services (filter #(= "OPeNDAP" (:Type %)) services)
        esi-services (filter #(= "ESI" (:Type %)) services)
        harmony-services (filter #(= "Harmony" (:Type %)) services)]
    (util/remove-map-keys
      empty?
      {:opendap (get-trimmed-has-features opendap-services )
       :esi (get-trimmed-has-features esi-services)
       :harmony (get-trimmed-has-features harmony-services)})))

(defn service-associations->elastic-doc
  "Converts the service association into the portion going in the collection elastic document."
  [context service-associations]
  (let [service-concepts (service-associations->service-concepts context service-associations)
        service-names (map #(get-in % [:extra-fields :service-name]) service-concepts)
        service-concept-ids (map :concept-id service-concepts)
        parsed-services (map #(concept-parser/parse-concept context %) service-concepts)
        service-types (map :Type parsed-services)
        service-features (get-service-features parsed-services)]
    (merge
     {:service-names service-names
      :service-names-lowercase (map string/lower-case service-names)
      :service-concept-ids service-concept-ids
      :service-types-lowercase (map string/lower-case service-types)
      :service-features-gzip-b64 (when (seq service-features)
                                   (-> service-features
                                       pr-str
                                       util/string->gzip-base64))}
     (get-has-features parsed-services))))
