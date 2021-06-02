(ns cmr.system-int-test.utils.virtual-product-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.virtual-product.config]
            [cmr.virtual-product.data.source-to-virtual-mapping :as svm]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.umm.umm-granule :as umm-g]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common.mime-types :as mt]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]))

(def virtual-product-providers
  "Returns a list of the provider ids of the providers with virtual products."
  (->> svm/source-to-virtual-product-mapping
       keys
       (map first)))

(defn source-collections
  "Returns a sequence of UMM collection records that are sources of virtual products."
  []
  (for [[[provider-id entry-title]
         {:keys [short-name]}] svm/source-to-virtual-product-mapping]
    (assoc (dc/collection {:entry-title entry-title :short-name short-name})
           :provider-id provider-id)))

(defn set-provider-aliases
  [aliases]
  ;; Set in the system int test vm AND in the dev system VM
  (cmr.virtual-product.config/set-virtual-product-provider-aliases! aliases)

  (dev-sys-util/eval-in-dev-sys
    `(cmr.virtual-product.config/set-virtual-product-provider-aliases! ~aliases)))

(defn get-provider-aliases
  []
  (dev-sys-util/eval-in-dev-sys
    `(cmr.virtual-product.config/virtual-product-provider-aliases)))

(defmacro with-provider-aliases
  "Wraps body while using aliases for the provider aliases."
  [aliases body]
  `(let [orig-aliases# (get-provider-aliases)]
    (set-provider-aliases ~aliases)
    (try
      ~body
      (finally
        (set-provider-aliases orig-aliases#)))))

(defn set-disabled-collections
  [colls]
  ;; Set in the system int test vm AND in the dev system VM
  (cmr.virtual-product.config/set-disabled-virtual-product-source-collections! colls)

  (dev-sys-util/eval-in-dev-sys
    `(cmr.virtual-product.config/set-disabled-virtual-product-source-collections! ~colls)))

(defn get-disabled-collections
  []
  (dev-sys-util/eval-in-dev-sys
    `(cmr.virtual-product.config/disabled-virtual-product-source-collections)))

(defmacro with-disabled-source-collections
  "Wraps body while using disabled source collections."
  [disabled-collections body]
  `(let [orig-colls# (get-disabled-collections)]
    (set-disabled-collections ~disabled-collections)
    (try
      ~body
      (finally
        (set-disabled-collections orig-colls#)))))

(defn source-collection->virtual-collections
  "Returns virtual collections from a source collection"
  [source-collection]
  (for [virtual-coll-attribs (-> svm/source-to-virtual-product-mapping
                                 (get [(svm/provider-alias->provider-id (:provider-id source-collection))
                                       (:entry-title source-collection)])
                                 :virtual-collections)]
    (assoc (dc/collection (merge (dissoc source-collection
                                         :revision-id :native-id :concept-id :entry-id :product)
                                 virtual-coll-attribs))
           :product-specific-attributes (conj (:product-specific-attributes source-collection)
                                              {:name svm/source-granule-ur-additional-attr-name
                                               :description "Granule-ur of the source granule"
                                               :data-type :string})
           :provider-id (:provider-id source-collection))))

(defn source-granule->virtual-granule-urs
  "Returns the set of granule URs for the given source granule."
  [granule]
  (let [{provider-alias :provider-id granule-ur :granule-ur
         {:keys [entry-title]} :collection-ref} granule
        provider-id (svm/provider-alias->provider-id provider-alias)
        vp-config (get svm/source-to-virtual-product-mapping [provider-id entry-title])]
    (for [virtual-coll (:virtual-collections vp-config)]
      (svm/generate-granule-ur
        provider-id (:short-name vp-config) virtual-coll granule-ur))))

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
    [(svm/provider-alias->provider-id (:provider-id collection))
     (get-in collection [:product :short-name])]))

(defmethod add-collection-attributes :default
  [collection]
  collection)

(defmethod add-collection-attributes ["LPDAAC_ECS" "AST_L1A"]
  [collection]
  (let [psa1 (dc/psa {:name "TIR_ObservationMode" :data-type :string})
        psa2 (dc/psa {:name "SWIR_ObservationMode" :data-type :string})
        psa3 (dc/psa {:name "VNIR1_ObservationMode" :data-type :string})
        psa4 (dc/psa {:name "VNIR2_ObservationMode" :data-type :string})]
    (assoc collection
           :product-specific-attributes [psa1 psa2 psa3 psa4])))

(defmethod add-collection-attributes ["LPDAAC_ECS" "AST_L1T"]
  [collection]
  (let [frbt (dc/psa {:name "FullResolutionThermalBrowseAvailable" :data-type :string})
        frbv (dc/psa {:name "FullResolutionVisibleBrowseAvailable" :data-type :string})]
    (assoc collection
           :product-specific-attributes [frbt frbv])))

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

(defmulti add-granule-attributes
  "A method to add custom attributes to granule concepts depending on the source granule"
  (fn [provider-id granule]
    [(svm/provider-alias->provider-id provider-id)
     (get-in granule [:collection-ref :short-name])]))

(defmethod add-granule-attributes :default
  [provider-id granule]
  granule)

(defmethod add-granule-attributes ["LPDAAC_ECS" "AST_L1A"]
  [provider-id granule]
  (let [psa1 (dg/psa "TIR_ObservationMode" ["ON"])
        psa2 (dg/psa "SWIR_ObservationMode" ["ON"])
        psa3 (dg/psa "VNIR1_ObservationMode" ["ON"])
        psa4 (dg/psa "VNIR2_ObservationMode" ["ON"])]
    (-> granule
        (assoc :data-granule
               (umm-g/map->DataGranule
                 {:day-night "DAY"
                  :production-date-time "2014-09-26T11:11:00Z"}))
        (assoc :product-specific-attributes [psa1 psa2 psa3 psa4]))))

(defmethod add-granule-attributes ["LPDAAC_ECS" "AST_L1T"]
  [provider-id granule]
  (let [frbt (dg/psa "FullResolutionThermalBrowseAvailable" ["YES"])
        frbv (dg/psa "FullResolutionVisibleBrowseAvailable" ["YES"])]
    (-> granule
        (assoc :product-specific-attributes [frbt frbv]))))

(defn ingest-source-granule
  [provider-id concept & options]
  (d/ingest provider-id
            (add-granule-attributes
                          (svm/provider-alias->provider-id provider-id) concept)
            (apply hash-map options)))


(defn ingest-ast-coll
  "Ingests the AST_L1A collection commonly used in virtual product tests."
  []
  (first
   (ingest-source-collections
    [(assoc
      (dc/collection
       {:entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
        :short-name "AST_L1A"})
      :provider-id "LPDAAC_ECS")])))

;;; Functions for use in assertions

(defn assert-matching-granule-urs
  "Asserts that the references found from a search match the expected granule URs."
  [expected-granule-urs search-results]
  (is (= (set expected-granule-urs)
         (set (map :name (:refs search-results))))))

(defn assert-psa-granules-match
  "A large assertion used to test the behavior of both the virtual
  product service and virtual product bootstrapping logic. f is a
  function that will be called after indexing each source granule."
  [f]
  (let [ast-coll (ingest-ast-coll)
        psa1 (dg/psa "TIR_ObservationMode" ["ON"])
        psa2 (dg/psa "SWIR_ObservationMode" ["ON"])
        psa3 (dg/psa "VNIR1_ObservationMode" ["ON"])
        psa4 (dg/psa "VNIR2_ObservationMode" ["ON"])]
    (ingest-virtual-collections [ast-coll])
    (are [granule-attrs expected-granule-urs]
        (let [params {"attribute[]" (format "string,%s,%s"
                                            svm/source-granule-ur-additional-attr-name
                                            (:granule-ur granule-attrs))
                      :page-size 20}]
          (d/ingest "LPDAAC_ECS" (dg/granule ast-coll granule-attrs))
          (f)
          (assert-matching-granule-urs expected-granule-urs
                                       (search/find-refs :granule params)))

      {:granule-ur "SC:AST_L1A.003:2006227720"
       :product-specific-attributes [psa4]}
      ["SC:AST_L1B.003:2006227720" "SC:AST_L1T.031:2006227720"]

      {:granule-ur "SC:AST_L1A.003:2006227721"
       :product-specific-attributes [psa1]}
      ["SC:AST_05.003:2006227721" "SC:AST_08.003:2006227721" "SC:AST_09T.003:2006227721"
       "SC:AST_L1B.003:2006227721" "SC:AST_L1T.031:2006227721"]

      {:granule-ur "SC:AST_L1A.003:2006227722"
       :product-specific-attributes [psa1 psa2 psa3 psa4]}
      ["SC:AST_05.003:2006227722" "SC:AST_08.003:2006227722" "SC:AST_09T.003:2006227722"
       "SC:AST_L1B.003:2006227722" "SC:AST_L1T.031:2006227722"]

      {:granule-ur "SC:AST_L1A.003:2006227724"
       :product-specific-attributes [psa3 psa4]
       :data-granule (umm-g/map->DataGranule
                      {:day-night "DAY"
                       :production-date-time "2014-09-26T11:11:00Z"})}
      ["SC:AST14DEM.003:2006227724" "SC:AST14OTH.003:2006227724" "SC:AST14DMO.003:2006227724"
       "SC:AST_L1B.003:2006227724" "SC:AST_L1T.031:2006227724"]

      {:granule-ur "SC:AST_L1A.003:2006227725"
       :product-specific-attributes [psa2 psa3 psa4]
       :data-granule (umm-g/map->DataGranule
                      {:day-night "DAY"
                       :production-date-time "2014-09-26T11:11:00Z"})}
      ["SC:AST14DMO.003:2006227725" "SC:AST_09.003:2006227725"
       "SC:AST_09XT.003:2006227725" "SC:AST14DEM.003:2006227725"
       "SC:AST_07.003:2006227725"   "SC:AST14OTH.003:2006227725"
       "SC:AST_07XT.003:2006227725" "SC:AST_L1B.003:2006227725" "SC:AST_L1T.031:2006227725"]

      {:granule-ur "SC:AST_L1A.003:2006227726"
       :product-specific-attributes [psa1 psa2 psa3 psa4]
       :data-granule (umm-g/map->DataGranule
                      {:day-night "DAY"
                       :production-date-time "2014-09-26T11:11:00Z"})}
      ["SC:AST_09XT.003:2006227726" "SC:AST14DEM.003:2006227726"
       "SC:AST_08.003:2006227726"   "SC:AST_05.003:2006227726"
       "SC:AST14OTH.003:2006227726" "SC:AST_07.003:2006227726"
       "SC:AST_09.003:2006227726"   "SC:AST_09T.003:2006227726" "SC:AST_L1T.031:2006227726"
       "SC:AST_07XT.003:2006227726" "SC:AST14DMO.003:2006227726" "SC:AST_L1B.003:2006227726"])))
