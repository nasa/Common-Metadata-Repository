(ns cmr.system-int-test.bootstrap.virtual-products-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util :refer [eval-in-dev-sys]]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.metadata-db-util :as mdb]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.virtual-product-util :as vp]
            [cmr.system-int-test.system :as s]
            [cmr.umm.umm-granule :as umm-g]
            [cmr.virtual-product.config]
            [cmr.virtual-product.data.source-to-virtual-mapping :as svm]
            [cmr.common.util :refer [are2]]))

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
  (doseq [[provider-id entry-title] (keys svm/source-to-virtual-product-mapping)]
    (bootstrap/bootstrap-virtual-products provider-id entry-title))
  (index/wait-until-indexed))

(defn fixture
  [f]
  (dev-sys-util/reset)
  (doseq [provider-id vp/virtual-product-providers]
    (ingest/create-provider {:provider-guid (str provider-id "-guid")
                             :provider-id provider-id
                             :cmr-only true}))
  (ingest/create-provider {:provider-guid "lp-alias-guid"
                           :provider-id "LP_ALIAS"
                           :cmr-only true})
  ;; turn off virtual products using eval-in-dev-sys so that it works
  ;; with integration tests when the CMR is running in another process
  (eval-in-dev-sys
    '(cmr.virtual-product.config/set-virtual-products-enabled! false))
  ;; run the test itself
  (f)
  ;; turn on virtual products again
  (eval-in-dev-sys
    '(cmr.virtual-product.config/set-virtual-products-enabled! true)))

(use-fixtures :each fixture)

;; The following test is copied from the main virtual product
;; integration tests, but initiates the virtual product bootstrap
;; process before checking for virtual products.

(deftest ^:oracle virtual-product-bootstrap-params
  (s/only-with-real-database
    (testing "invalid params"
      (are2 [exp-status exp-errors provider-id entry-title]
            (let [{:keys [status errors]}(bootstrap/bootstrap-virtual-products provider-id entry-title)]
              (is (= exp-status status))
              (is (= (set exp-errors) (set errors))))

            "missing provider-id"
            400
            ["The parameters [:provider-id :entry-title] are required"]
            nil
            "et1"

            "missing entry-id"
            400
            ["The parameters [:provider-id :entry-title] are required"]
            "PROV1"
            nil

            "missing both"
            400
            ["The parameters [:provider-id :entry-title] are required"]
            nil
            nil

            "invalid provider-id"
            404
            ["No virtual product configuration found for provider [FOO] and entry-title [ASTER L1A Reconstructed Unprocessed Instrument Data V003]"]
            "FOO"
            "ASTER L1A Reconstructed Unprocessed Instrument Data V003"

            "invalid entry-id"
            404
            ["No virtual product configuration found for provider [LPDAAC_ECS] and entry-title [BAR]"]
            "LPDAAC_ECS"
            "BAR"))))

(deftest ^:oracle virtual-product-bootstrap
  (s/only-with-real-database
    (let [;; ingest and then delete a collection that has the same entry-title as one of the virtual
          ;; collections to test the edge case documented in CMR-2169
          coll1 (d/ingest "LPDAAC_ECS"
                          (dc/collection {:entry-title "ASTER L2 Surface Emissivity V003"
                                          :native-id "NID-1"}))
          _ (ingest/delete-concept (d/item->concept coll1 :echo10))
          source-collections (vp/ingest-source-collections)
          ;; Ingest the virtual collections. For each virtual collection associate it with the source
          ;; collection to use later.
          vp-colls (reduce (fn [new-colls source-coll]
                             (into new-colls (map #(assoc % :source-collection source-coll)
                                                  (vp/ingest-virtual-collections [source-coll]))))
                           []
                           source-collections)
          source-granules (doall (for [source-coll source-collections
                                       :let [{:keys [provider-id entry-title]} source-coll]
                                       granule-ur (svm/sample-source-granule-urs
                                                    [provider-id entry-title])]
                                   (vp/ingest-source-granule provider-id
                                                             (dg/granule source-coll {:granule-ur granule-ur}))))
          all-expected-granule-urs (concat (mapcat vp/source-granule->virtual-granule-urs source-granules)
                                           (map :granule-ur source-granules))]
      (index/wait-until-indexed)

      (testing "Only source granules exist (virtual products system is disabled)"
        (vp/assert-matching-granule-urs
          (map :granule-ur source-granules)
          (search/find-refs :granule {:page-size 1000})))

      (bootstrap-and-index)

      (testing "Find all granules"
        (vp/assert-matching-granule-urs
          all-expected-granule-urs
          (search/find-refs :granule {:page-size 1000})))

      (testing "Find all granules in virtual collections"
        (doseq [vp-coll vp-colls
                :let [{:keys [provider-id source-collection]} vp-coll
                      source-short-name (get-in source-collection [:product :short-name])
                      vp-short-name (get-in vp-coll [:product :short-name])
                      vp-version-id (get-in vp-coll [:product :version-id])]]
          (vp/assert-matching-granule-urs
            (map #(svm/generate-granule-ur provider-id source-short-name {:short-name vp-short-name
                                                                          :version-id vp-version-id}  %)
                 (svm/sample-source-granule-urs
                   [provider-id (:entry-title source-collection)]))
            (search/find-refs :granule {:entry-title (:entry-title vp-coll)
                                        :page-size 50})))))))

(deftest ^:oracle virtual-product-provider-alias-bootstrap
  (s/only-with-real-database
    (vp/with-provider-aliases
      {"LPDAAC_ECS"  #{"LP_ALIAS"}}
      (let [entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
            bootstrap-collection (fn []
                                   (index/wait-until-indexed)
                                   (bootstrap/bootstrap-virtual-products "LP_ALIAS" entry-title)
                                   (index/wait-until-indexed))
            [ast-coll] (vp/ingest-source-collections
                         [(assoc
                            (dc/collection
                              {:entry-title entry-title
                               :short-name "AST_L1A"
                               :projects (dc/projects "proj1" "proj2" "proj3")})
                            :provider-id "LP_ALIAS")])
            vp-colls (vp/ingest-virtual-collections [ast-coll])
            granule-ur "SC:AST_L1A.003:2006227720"
            ast-l1a-gran (vp/ingest-source-granule "LP_ALIAS"
                                                   (dg/granule ast-coll {:granule-ur granule-ur
                                                                         :project-refs ["proj1"]}))
            virtual-granule-urs (vp/source-granule->virtual-granule-urs ast-l1a-gran)
            all-expected-granule-urs (cons (:granule-ur ast-l1a-gran) virtual-granule-urs)]
        (index/wait-until-indexed)

        (testing "Only source granules exist (virtual products system is disabled)"
          (vp/assert-matching-granule-urs
            [(:granule-ur ast-l1a-gran)]
            (search/find-refs :granule {:page-size 100})))

        ;; Bootstrap collection
        (bootstrap-collection)

        (testing "Virtual granules are generated after bootstrap source granule"
          (vp/assert-matching-granule-urs
            all-expected-granule-urs
            (search/find-refs :granule {:page-size 100})))

        ;; delete the source granule
        (ingest/delete-concept (d/item->concept ast-l1a-gran) {:revision-id 12})

        ;; Bootstrap collection again
        (bootstrap-collection)

        (testing "Virtual granules are deleted as a result of bootstrap deleted source granule"
          (vp/assert-matching-granule-urs
            []
            (search/find-refs :granule {:page-size 100})))))))

;; Verify that disabled source collections configuration is ignored during bootstrapping
(deftest ^:oracle virtual-product-bootstrap-disabled-source-collections
  (s/only-with-real-database
    (vp/with-disabled-source-collections
      #{"ASTER L1A Reconstructed Unprocessed Instrument Data V003"}
      (let [entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
            bootstrap-collection (fn []
                                   (index/wait-until-indexed)
                                   (bootstrap/bootstrap-virtual-products "LPDAAC_ECS" entry-title)
                                   (index/wait-until-indexed))
            [ast-coll] (vp/ingest-source-collections
                         [(assoc
                            (dc/collection
                              {:entry-title entry-title
                               :short-name "AST_L1A"
                               :version-id "003"
                               :projects (dc/projects "proj1" "proj2" "proj3")})
                            :provider-id "LPDAAC_ECS")])
            vp-colls (vp/ingest-virtual-collections [ast-coll])
            granule-ur "SC:AST_L1A.003:2006227720"
            ast-l1a-gran (vp/ingest-source-granule "LPDAAC_ECS"
                                                   (dg/granule ast-coll {:granule-ur granule-ur
                                                                         :project-refs ["proj1"]}))
            virtual-granule-urs (vp/source-granule->virtual-granule-urs ast-l1a-gran)
            all-expected-granule-urs (cons (:granule-ur ast-l1a-gran) virtual-granule-urs)]
        (index/wait-until-indexed)

        (testing "Only source granules exist (virtual products system is disabled)"
          (vp/assert-matching-granule-urs
            [(:granule-ur ast-l1a-gran)]
            (search/find-refs :granule {:page-size 100})))

        ;; Bootstrap collection
        (bootstrap-collection)

        (testing "Virtual granules are generated after bootstrap source granule"
          (vp/assert-matching-granule-urs
            all-expected-granule-urs
            (search/find-refs :granule {:page-size 100})))

        ;; delete the source granule
        (ingest/delete-concept (d/item->concept ast-l1a-gran) {:revision-id 12})

        ;; Bootstrap collection again
        (bootstrap-collection)

        (testing "Virtual granules are deleted as a result of bootstrap deleted source granule"
          (vp/assert-matching-granule-urs
            []
            (search/find-refs :granule {:page-size 100})))))))

;; Verify that latest revision ids of virtual granules and the corresponding source granules
;; are in sync as various ingest operations are performed on the source granules
(deftest ^:oracle deleted-virtual-granules
  (s/only-with-real-database
    (let [ast-coll (vp/ingest-ast-coll)
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
                         (is (:deleted (mdb/get-concept (:id gran) 12)))))]

      (is (not (empty? v-granules)))

      (testing "after deleting source granules"
        (doseq [granule s-granules]
          (ingest/delete-concept (d/item->concept granule) {:revision-id 12}))
        (bootstrap-and-index)
        (verify))

      (testing "bootstrapping should be idempotent"
        (bootstrap-and-index)
        (verify)))))

;; NOTE: This test uses the assert-psa-granules-match helper to
;; abstract out a large amount of code borrowed from the normal
;; virtual product service tests.

(deftest ^:oracle ast-granule-umm-matchers-test
  (s/only-with-real-database
    (vp/assert-psa-granules-match bootstrap-and-index)))
