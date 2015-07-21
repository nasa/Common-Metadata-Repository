(ns cmr.system-int-test.utils.virtual-product-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.virtual-product.config :as vp-config]
            [cmr.system-int-test.data2.collection :as dc]
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
  (for [[[provider-id entry-title] {short-name :source-short-name}] vp-config/source-to-virtual-product-config]
    (assoc (dc/collection {:entry-title entry-title :short-name short-name})
           :provider-id provider-id)))

(defn virtual-collections
  "Returns virtual collections from a source collection"
  [source-collection]
  (for [virtual-coll-attribs (-> vp-config/source-to-virtual-product-config
                                 (get [(:provider-id source-collection) (:entry-title source-collection)])
                                 :virtual-collections)]
    (assoc (dc/collection (merge (dissoc source-collection
                                         :revision-id :native-id :concept-id :entry-id :product)
                                 virtual-coll-attribs ))
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