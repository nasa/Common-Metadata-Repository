(ns cmr.system-int-test.bootstrap.virtual-products-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util :refer [eval-in-dev-sys]]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.virtual-product-util :as vp]
            [cmr.virtual-product.config :as vp-config]))

;; test procedure:
;;
;; 1. fixtures create an empty database
;; 2. create providers
;; 3. create source collection
;; 4. create source granules
;; 5. ensure virtual granules do NOT exist
;; 6. run bootstrap virtual products
;; 7. ensure virtual granules DO exist

(defn bootstrap-and-index
  []
  (index/wait-until-indexed)
  (bootstrap/bootstrap-virtual-products)
  (index/wait-until-indexed))

(defn virtual-products-fixture
  [f]
  (dev-sys-util/reset)
  (doseq [provider-id vp/virtual-product-providers]
    (println "Creating provider" provider-id)
    (ingest/create-provider (str provider-id "-guid") provider-id {:cmr-only true}))
  ;; turn off virtual products using eval-in-dev-sys so that it works
  ;; with integration tests when the CMR is running in another process
  (eval-in-dev-sys
   '(cmr.virtual-product.config/set-virtual-products-enabled! false))
  ;; run the test itself
  (f)
  ;; turn on virtual products again
  (eval-in-dev-sys
   '(cmr.virtual-product.config/set-virtual-products-enabled! true)))

(use-fixtures :each virtual-products-fixture)

;; The following function is copied from the other virtual product
;; test namespace; needs a new home.

(defn- assert-matching-granule-urs
  "Asserts that the references found from a search match the expected granule URs."
  [expected-granule-urs {:keys [refs]}]
  (is (= (set expected-granule-urs)
         (set (map :name refs)))))

;; The following test is copied from the main virtual product
;; integration tests, but initiates the virtual product bootstrap
;; process before checking for virtual products.

(deftest virtual-product-bootstrap
  (let [source-collections (vp/ingest-source-collections)
        ;; Ingest the virtual collections. For each virtual collection associate it with the source
        ;; collection to use later.
        vp-colls (reduce (fn [new-colls source-coll]
                           (into new-colls (map #(assoc % :source-collection source-coll)
                                                (vp/ingest-virtual-collections [source-coll]))))
                         []
                         source-collections)
        source-granules (doall (for [source-coll source-collections
                                     :let [{:keys [provider-id entry-title]} source-coll]
                                     granule-ur (vp-config/sample-source-granule-urs
                                                  [provider-id entry-title])]
                                 (vp/ingest-source-granule provider-id
                                                          (dg/granule source-coll {:granule-ur granule-ur}))))
        all-expected-granule-urs (concat (mapcat vp/source-granule->virtual-granule-urs source-granules)
                                         (map :granule-ur source-granules))]
    (index/wait-until-indexed)

    (testing "Only source granules exist (virtual products system is disabled)"
      (assert-matching-granule-urs
       (map :granule-ur source-granules)
       (search/find-refs :granule {:page-size 50})))

    (bootstrap-and-index)

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

;; Verify that latest revision ids of virtual granules and the corresponding source granules
;; are in sync as various ingest operations are performed on the source granules
(deftest deleted-virtual-granules
  (let [ast-coll   (d/ingest "LPDAAC_ECS"
                             (dc/collection
                              {:entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"}))
        vp-colls   (vp/ingest-virtual-collections [ast-coll])
        s-granules (doall
                    (for [n (range 10)]
                      (vp/ingest-source-granule
                       "LPDAAC_ECS"
                       (dg/granule ast-coll {:granule-ur (format "SC:AST_L1A.003:%d" n)
                                             :revision-id 5}))))
        _          (bootstrap-and-index)
        v-granules (mapcat #(:refs
                             (search/find-refs :granule
                                               {:entry-title (:entry-title %)
                                                :page-size 50}))
                           vp-colls)
        verify     (fn []
                     (doseq [gran v-granules]
                       (is (:deleted (ingest/get-concept (:id gran) 12)))))]

    (testing "after deleting source granules"
      (doseq [granule s-granules]
        (ingest/delete-concept (d/item->concept granule) {:revision-id 12}))
      (bootstrap-and-index)
      (verify))

    (testing "bootstrapping should be idempotent"
      (bootstrap-and-index)
      (verify))))
