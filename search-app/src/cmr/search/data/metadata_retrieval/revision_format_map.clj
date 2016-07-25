(ns cmr.search.data.metadata-retrieval.revision-format-map
  "Defines a set of helper functions for the metadata cache for dealing with revision format maps.
  Revision format maps contain metadata from a concept in multiple formats. See the metadata cache
  namespace for the exact list of fields."
  (require [cmr.common.util :as u]
           [clojure.string :as str]
           [clojure.set :as set]
           [cmr.common.log :as log :refer (debug info warn error)]
           [cmr.search.services.result-format-helper :as rfh]
           [cmr.search.data.metadata-retrieval.metadata-transformer :as metadata-transformer]))

(def ^:private non-metadata-fields
  #{:concept-id :revision-id :native-format :compressed? :size})

(def ^:private key-sorted-revision-format-map
  (u/key-sorted-map [:concept-id :revision-id :native-format :echo10 :dif :dif10 :iso19115]))

(defn- map-metadata-values
  "Applies a function over the metadata fields of the revision format map"
  [f rfm]
  (into {} (mapv (fn [entry]
                   (let [k (key entry)]
                     (if (contains? non-metadata-fields k)
                       entry
                       [k (f (val entry))])))
                 rfm)))

(defn cached-formats
  "Takes a revision format map and returns a set of the formats that are cached."
  [rfm]
  (set/difference (set (keys rfm)) non-metadata-fields))

(defn- with-size
  "Adds a size field to the revision format map by calculating the sizes of the individual cached
   metadata."
  [rfm]
  (let [metadata-fields (filterv (complement non-metadata-fields) (keys rfm))
        size (reduce + (map (comp count :compressed rfm) metadata-fields))]
    (assoc rfm :size size)))

(defn compress
  "Compresses the metadata in a revision format map if it is not yet compressed."
  [revision-format-map]
  (if (:compressed? revision-format-map)
    revision-format-map
    (-> (map-metadata-values u/string->lz4-bytes revision-format-map)
        (assoc :compressed? true)
        with-size)))

(defn decompress
  "Decompresses a compressed revision format map. Safe to run on non-compressed maps."
  [revision-format-map]
  (if (:compressed? revision-format-map)
    (-> (map-metadata-values u/lz4-bytes->string revision-format-map)
        (assoc :compressed? false)
        (dissoc :size))
    revision-format-map))

(defn prettify
  "Returns a version of the revision format map decompressed with trimmed XML to help in debugging."
  [revision-format-map]
  (let [uncompressed-map (dissoc (decompress revision-format-map) :compressed?)
        trim-xml (fn [xml]
                   (-> xml
                       (str/replace "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" "")
                       (subs 0 30)
                       (str "...")))
        trimmed (map-metadata-values trim-xml uncompressed-map)
        keys-as-keywords (into {} (mapv (fn [[k v]]
                                          (if (map? k)
                                            [(keyword (str (name (:format k)) "_" (name (:version k)))) v]
                                            [k v]))
                                        trimmed))]
    (into key-sorted-revision-format-map keys-as-keywords)))

(defn- get-metadata-in-format
  "Gets cached metadata in the specified format from the revision format map. Assumes the format is
   present."
  [target-format revision-format-map]
  (let [target-format (if (= target-format :native)
                        (:native-format revision-format-map)
                        target-format)
        metadata (get revision-format-map target-format)]
    (if (:compressed? revision-format-map)
      (u/lz4-bytes->string metadata)
      metadata)))

(defn revision-format-map->concept
  "Converts a revision format map into a concept map with the target format. Assumes target format
   is present in revision format map."
  [target-format revision-format-map]
  {:pre [(or (get revision-format-map target-format)
             (= :native target-format))]}
  (let [{:keys [concept-id revision-id]} revision-format-map]
    {:concept-id concept-id
     :revision-id revision-id
     :concept-type :collection
     :metadata (get-metadata-in-format target-format revision-format-map)
     :format (if (= :native target-format)
               (rfh/search-result-format->mime-type (:native-format revision-format-map))
               (rfh/search-result-format->mime-type target-format))}))

(defn revision-format-maps->concepts
  "Converts a set of revision format maps to concepts with the target format."
  [target-format revision-format-maps]
  (u/fast-map #(revision-format-map->concept target-format %)
              revision-format-maps))

(defn concept->revision-format-map
  "Converts a concept into a revision format map. Returns nil if the concept was deleted."
  ([context concept target-format-set]
   (concept->revision-format-map context concept target-format-set false))
  ([context concept target-format-set ignore-exceptions?]
   (when-not (:deleted concept)
     (let [{:keys [concept-id revision-id metadata] concept-mime-type :format} concept
           native-format (rfh/mime-type->search-result-format concept-mime-type)
           base-map {:concept-id concept-id
                     :revision-id revision-id
                     :native-format native-format
                     native-format metadata}
           ;; Translate to all the cached formats except the native format.
           target-formats (disj target-format-set native-format :native)
           formats-map (metadata-transformer/transform-to-multiple-formats
                        context concept target-formats ignore-exceptions?)]
       (merge base-map formats-map)))))

(defn add-additional-format
  "Adds an additional stored format to the revision format map."
  [context target-format revision-format-map]
  (let [concept (revision-format-map->concept :native revision-format-map)
        transformed (metadata-transformer/transform context concept target-format)]
    (if (:compressed? revision-format-map)
     (assoc revision-format-map target-format (u/string->lz4-bytes transformed))
     (assoc revision-format-map target-format transformed))))

(defn merge-into-cache-map
  "Merges in the updated revision-format-map into the existing cache map. cache-map should be a map of
  concept ids to revision format maps. The passed in revision format map can contain an unknown
  item, a newer revision, or formats not yet cached. The data will be merged in the right way."
  [cache-map revision-format-map]
  (let [{:keys [concept-id revision-id]} revision-format-map]
    (if-let [curr-rev-format-map (cache-map concept-id)]
      ;; We've cached this concept
      (cond
        ;; We've got a newer revision
        (> revision-id (:revision-id curr-rev-format-map))
        (assoc cache-map concept-id revision-format-map)

        ;; We somehow retrieved older data than was cached. Keep newer data
        (< revision-id (:revision-id curr-rev-format-map))
        cache-map

        ;; Same revision
        :else
        ;; Merge in the newer data which may have additional cached formats.
        (assoc cache-map concept-id (merge curr-rev-format-map revision-format-map)))

      ;; We haven't cached this concept yet.
      (assoc cache-map concept-id revision-format-map))))