(ns cmr.system-int-test.virtual-product-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.virtual-product.config :as vp-config]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]))

(def virtual-product-providers
  "Returns a list of the provider ids of the providers with virtual products."
  (->> vp-config/source-to-virtual-product-config
       keys
       (map first)))


(use-fixtures :each (ingest/reset-fixture (into {} (for [p virtual-product-providers]
                                                     [(str p "_guid") p]))))

(comment
  (do
    (dev-sys-util/reset)
    (doseq [p virtual-product-providers]
      (ingest/create-provider (str p "_guid") p)))


  (def isc (ingest-source-collections))

  (def vpc (ingest-virtual-collections isc))

  )

(defn source-collections
  "Returns a sequence of UMM collection records that are sources of virtual products."
  []
  (for [[[provider-id entry-title] {short-name :source-short-name}] vp-config/source-to-virtual-product-config]
    (assoc (dc/collection {:entry-title entry-title :short-name short-name})
           :provider-id provider-id)))

(defn ingest-source-collections
  "Ingests the source collections and returns their UMM records with some extra information."
  ([]
   (ingest-source-collections (source-collections)))
  ([source-collections]
   (mapv #(d/ingest (:provider-id %) %) source-collections)))

(defn virtual-collections
  "Returns virtual collections from a source collection"
  [source-collection]
  (for [virtual-coll-attribs (-> vp-config/source-to-virtual-product-config
                                 (get [(:provider-id source-collection) (:entry-title source-collection)])
                                 :virtual-collections)]
    (assoc (dc/collection virtual-coll-attribs)
           :provider-id (:provider-id source-collection))))

(defn ingest-virtual-collections
  "Ingests the virtual collections for the given set of source collections."
  [source-collections]
  (->> source-collections
       (mapcat virtual-collections)
       (mapv #(d/ingest (:provider-id %) %))))

(defn granule->expected-virtual-granule-urs
  "Returns the set of expected granule URs for the given source granule."
  [granule]
  (let [{:keys [provider-id granule-ur]
         {:keys [entry-title]} :collection-ref} granule
        vp-config (get vp-config/source-to-virtual-product-config [provider-id entry-title])]
    (for [virtual-coll (:virtual-collections vp-config)]
      (vp-config/generate-granule-ur
        provider-id (:source-short-name vp-config) (:short-name virtual-coll) granule-ur))))

(defn assert-matching-granule-urs
  "Asserts that the references found from a search match the expected granule URs."
  [expected-granule-urs {:keys [refs]}]
  (is (= (set expected-granule-urs)
         (set (map :name refs)))))

(deftest specific-granule-in-virtual-product-test
  (let [ast-coll (d/ingest "LPDAAC_ECS"
                           (dc/collection
                             {:entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"}))
        vp-colls (ingest-virtual-collections [ast-coll])
        granule-ur "SC:AST_L1A.003:2006227720"
        ast-l1a-gran (d/ingest "LPDAAC_ECS" (dg/granule ast-coll {:granule-ur granule-ur}))
        expected-granule-urs (granule->expected-virtual-granule-urs ast-l1a-gran)]
    (index/wait-until-indexed)

    (testing "Find all granules"
      (assert-matching-granule-urs
        (cons (:granule-ur ast-l1a-gran) expected-granule-urs)
        (search/find-refs :granule {:page-size 50})))

    (testing "Find all granules in virtual collections"
      (doseq [vp-coll vp-colls]
        (assert-matching-granule-urs
          [(vp-config/generate-granule-ur
             "LPDAAC_ECS" "AST_L1A" (get-in vp-coll [:product :short-name]) granule-ur)]
          (search/find-refs :granule {:entry-title (:entry-title vp-coll)
                                      :page-size 50}))))))

(deftest all-granules-in-virtual-product-test
  (let [source-collections (ingest-source-collections)
        ;; Ingest the virtual collections. For each virtual collection associate it with the source
        ;; collection to use later.
        vp-colls (reduce (fn [new-colls source-coll]
                           (into new-colls (map #(assoc % :source-collection source-coll)
                                                (ingest-virtual-collections [source-coll]))))
                         []
                         source-collections)
        source-granules (doall (for [source-coll source-collections
                                     :let [{:keys [provider-id entry-title]} source-coll]
                                     granule-ur (vp-config/sample-source-granule-urs
                                                  [provider-id entry-title])]
                                 (d/ingest provider-id (dg/granule source-coll {:granule-ur granule-ur}))))
        all-expected-granule-urs (concat (mapcat granule->expected-virtual-granule-urs source-granules)
                                         (map :granule-ur source-granules))]
    (index/wait-until-indexed)

    (testing "Find all granules"
      (assert-matching-granule-urs
        all-expected-granule-urs
        (search/find-refs :granule {:page-size 50})))

    (testing "Find all granules in virtual collections"
      (doseq [vp-coll vp-colls
              :let [{:keys [provider-id source-collection]} vp-coll
                    source-short-name (get-in source-collection [:product :short-name])
                    vp-short-name (get-in vp-coll [:product :short-name])]]
        (assert-matching-granule-urs
          (map #(vp-config/generate-granule-ur provider-id source-short-name vp-short-name %)
               (vp-config/sample-source-granule-urs
                 [provider-id (:entry-title source-collection)]))
          (search/find-refs :granule {:entry-title (:entry-title vp-coll)
                                      :page-size 50}))))))








