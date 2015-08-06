(ns cmr.system-int-test.utils.virtual-product-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.virtual-product.config :as vp-config]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.umm.granule :as umm-g]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common.mime-types :as mt]))

(def virtual-product-providers
  "Returns a list of the provider ids of the providers with virtual products."
  (->> vp-config/source-to-virtual-product-config
       keys
       (map first)))

(defn source-collections
  "Returns a sequence of UMM collection records that are sources of virtual products."
  []
  (for [[[provider-id entry-title]
         {short-name :source-short-name}] vp-config/source-to-virtual-product-config]
    (assoc (dc/collection {:entry-title entry-title :short-name short-name})
           :provider-id provider-id)))

(defn source-collection->virtual-collections
  "Returns virtual collections from a source collection"
  [source-collection]
  (for [virtual-coll-attribs (-> vp-config/source-to-virtual-product-config
                                 (get [(:provider-id source-collection) (:entry-title source-collection)])
                                 :virtual-collections)]
    (assoc (dc/collection (merge (dissoc source-collection
                                         :revision-id :native-id :concept-id :entry-id :product)
                                 virtual-coll-attribs))
           :product-specific-attributes (conj (:product-specific-attributes source-collection)
                                              {:name vp-config/source-granule-ur-additional-attr-name
                                               :description "Granule-ur of the source granule"
                                               :data-type :string})
           :provider-id (:provider-id source-collection))))

(defn source-granule->virtual-granule-urs
  "Returns the set of granule URs for the given source granule."
  [granule]
  (let [{:keys [provider-id granule-ur]
         {:keys [entry-title]} :collection-ref} granule
        vp-config (get vp-config/source-to-virtual-product-config [provider-id entry-title])]
    (for [virtual-coll (:virtual-collections vp-config)]
      (vp-config/generate-granule-ur
        provider-id (:source-short-name vp-config) (:short-name virtual-coll) granule-ur))))

(defn translate-granule-entries
  "Translate the virtual granule entries to the corresponding source entries in the input json"
  [json-str]
  (client/post (url/virtual-product-translate-granule-entries-url)
               {:throw-exceptions false
                :content-type mt/json
                :body json-str
                :connection-manager (s/conn-mgr)}))

(defmulti add-collection-attributes
  "A method to add custom attributes to collection concepts depending on the source collection"
  (fn [collection]
    [(:provider-id collection) (get-in collection [:product :short-name])]))

(defmethod add-collection-attributes :default
  [collection]
  collection)

(defmethod add-collection-attributes ["LPDAAC_ECS" "AST_L1A"]
  [collection]
  (let [psa1 (dc/psa "TIR_ObservationMode" :string)
        psa2 (dc/psa "SWIR_ObservationMode" :string)
        psa3 (dc/psa "VNIR1_ObservationMode" :string)
        psa4 (dc/psa "VNIR2_ObservationMode" :string)]
              (assoc collection
                     :product-specific-attributes [psa1 psa2 psa3 psa4])))

(defn ingest-source-collections
  "Ingests the source collections and returns their UMM records with some extra information."
  ([]
   (ingest-source-collections (source-collections)))
  ([source-collections]
   (ingest-source-collections source-collections {}))
  ([source-collections options]
   (mapv #(d/ingest (:provider-id %) (add-collection-attributes %) options) source-collections)))

(defn ingest-virtual-collections
  "Ingests the virtual collections for the given set of source collections."
  ([]
   (ingest-virtual-collections (source-collections) {}))
  ([source-collections]
   (ingest-virtual-collections source-collections {}))
  ([source-collections options]
  (->> source-collections
       (mapcat source-collection->virtual-collections)
       (mapv #(d/ingest (:provider-id %) % options)))))

(defmulti ingest-source-granule
  "A method to ingest a source granule while adding necessary attributes that allow all related virtual granules to pass through all the associated matchers"
  (fn [provider-id concept & options]
    [provider-id (get-in concept [:collection-ref :short-name])]))

(defmethod ingest-source-granule :default
  [provider-id concept & options]
  (d/ingest provider-id concept (apply hash-map options)))

(defmethod ingest-source-granule ["LPDAAC_ECS" "AST_L1A"]
  [provider-id concept & options]
  (let [psa1 (dg/psa "TIR_ObservationMode" ["ON"])
        psa2 (dg/psa "SWIR_ObservationMode" ["ON"])
        psa3 (dg/psa "VNIR1_ObservationMode" ["ON"])
        psa4 (dg/psa "VNIR2_ObservationMode" ["ON"])]
    (d/ingest provider-id (-> concept
                              (assoc :data-granule
                                     (umm-g/map->DataGranule
                                       {:day-night "DAY"
                                        :production-date-time "2014-09-26T11:11:00Z"}))
                              (assoc :product-specific-attributes [psa1 psa2 psa3 psa4]))
              (apply hash-map options))))