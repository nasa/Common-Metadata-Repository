(ns cmr.system-int-test.virtual-product-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.virtual-product-util :as vp]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.virtual-product.config :as vp-config]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
            [cmr.common.time-keeper :as tk]
            [cheshire.core :as json]
            [cmr.common.util :as util]
            [clj-time.core :as t]))

(use-fixtures :each (ingest/reset-fixture (into {} (for [p vp/virtual-product-providers]
                                                     [(str p "_guid") p]))))

(defn ingest-source-collections
  "Ingests the source collections and returns their UMM records with some extra information."
  ([]
   (ingest-source-collections (vp/source-collections)))
  ([source-collections]
   (mapv #(d/ingest (:provider-id %) %) source-collections)))

(defn ingest-virtual-collections
  "Ingests the virtual collections for the given set of source collections."
  [source-collections]
  (->> source-collections
       (mapcat vp/virtual-collections)
       (mapv #(d/ingest (:provider-id %) %))))

(defn- assert-matching-granule-urs
  "Asserts that the references found from a search match the expected granule URs."
  [expected-granule-urs {:keys [refs]}]
  (is (= (set expected-granule-urs)
         (set (map :name refs)))))

(comment
  (do
    (dev-sys-util/reset)
    (doseq [p virtual-product-providers]
      (ingest/create-provider (str p "_guid") p)))

  (dissoc (first isc) :revision-id :native-id :concept-id :entry-id)


  (def isc (ingest-source-collections))

  (def vpc (ingest-virtual-collections isc))

  )

;; TODO when testing a failure case we can delete the virtual collection. This would make the granule fail ingest.

(deftest specific-granule-in-virtual-product-test
  (let [ast-coll (d/ingest "LPDAAC_ECS"
                           (dc/collection
                             {:entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
                              :projects (dc/projects "proj1" "proj2" "proj3")}))
        vp-colls (ingest-virtual-collections [ast-coll])
        granule-ur "SC:AST_L1A.003:2006227720"
        ast-l1a-gran (d/ingest "LPDAAC_ECS" (dg/granule ast-coll {:granule-ur granule-ur
                                                                  :project-refs ["proj1"]}))
        expected-granule-urs (vp/source-granule->virtual-granule-urs ast-l1a-gran)
        all-expected-granule-urs (cons (:granule-ur ast-l1a-gran) expected-granule-urs)]
    (index/wait-until-indexed)

    (testing "Find all granules"
      (assert-matching-granule-urs
        all-expected-granule-urs
        (search/find-refs :granule {:page-size 50})))

    (testing "Find all granules in virtual collections"
      (doseq [vp-coll vp-colls]
        (assert-matching-granule-urs
          [(vp-config/generate-granule-ur
             "LPDAAC_ECS" "AST_L1A" (get-in vp-coll [:product :short-name]) granule-ur)]
          (search/find-refs :granule {:entry-title (:entry-title vp-coll)
                                      :page-size 50}))))

    (testing "Find virtual granule by shared fields"
      (assert-matching-granule-urs
        all-expected-granule-urs
        (search/find-refs :granule {:page-size 50
                                    :project "proj1"})))

    (testing "Update source granule"
      ;; Update the source granule so that the projects referenced are different than original
      (let [ast-l1a-gran-r2 (d/ingest "LPDAAC_ECS" (assoc ast-l1a-gran
                                                          :project-refs ["proj2" "proj3"]
                                                          :revision-id nil))]
        (index/wait-until-indexed)
        (testing "find none by original project"
          (is (= 0 (:hits (search/find-refs :granule {:project "proj1"})))))

        (testing "Find virtual granule by shared fields"
          (assert-matching-granule-urs
            all-expected-granule-urs
            (search/find-refs :granule {:page-size 50
                                        :project "proj2"})))))

    (testing "Delete source granule"
      (let [resp (ingest/delete-concept (d/item->concept ast-l1a-gran))]
        (is (= 200 (:status resp)) (pr-str resp)))
      (index/wait-until-indexed)

      (testing "Find no granules"
        (is (= 0 (:hits (search/find-refs :granule {}))))))

    (testing "Recreate source granule"
      (let [ast-l1a-gran-r4 (d/ingest "LPDAAC_ECS" (dissoc ast-l1a-gran :revision-id :concept-id))]
        (index/wait-until-indexed)
        (testing "Find all granules"
          (assert-matching-granule-urs
            all-expected-granule-urs
            (search/find-refs :granule {:page-size 50})))))))

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
        all-expected-granule-urs (concat (mapcat vp/source-granule->virtual-granule-urs source-granules)
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

(defn- assert-virtual-gran-revision-id
  "Assert that the revision ids of the granules of the collections vp-colls match expected-revision-id"
  [vp-colls expected-revision-id]
  (doseq [revision-id  (mapcat #(map :revision-id (:refs (search/find-refs
                                                            :granule {:entry-title (:entry-title %)
                                                                      :page-size 50}))) vp-colls)]
    (is (= expected-revision-id revision-id))))

(defn- assert-tombstones
  "Assert that the concepts with the given concept-ids and revision-id exist in mdb and are tombstones"
  [concept-ids revision-id]
  (doseq [concept-id concept-ids]
    (is (:deleted (ingest/get-concept concept-id revision-id)))))

;; Verify that latest revision ids of virtual granules and the corresponding source granules
;; are in sync as various ingest operations are performed on the source granules
(deftest revision-ids-in-sync-test
  (let [ast-coll (d/ingest "LPDAAC_ECS"
                           (dc/collection
                             {:entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"}))
        vp-colls (ingest-virtual-collections [ast-coll])
        granule-ur "SC:AST_L1A.003:2006227720"
        ast-l1a-gran (dg/granule ast-coll {:granule-ur granule-ur})
        ingest-result (d/ingest "LPDAAC_ECS" (assoc ast-l1a-gran :revision-id 5))
        _ (index/wait-until-indexed)
        vp-granule-ids (mapcat #(map :id (:refs (search/find-refs
                                                :granule {:entry-title (:entry-title %)
                                                          :page-size 50}))) vp-colls)]

    ;; check revision ids are in sync after ingest/update operations
    (assert-virtual-gran-revision-id vp-colls 5)
    (d/ingest "LPDAAC_ECS" (assoc ast-l1a-gran :revision-id 10))
    (index/wait-until-indexed)
    (assert-virtual-gran-revision-id vp-colls 10)

    ;; check revision ids are in sync after delete operations
    (ingest/delete-concept (d/item->concept ingest-result) {:revision-id 12})
    (index/wait-until-indexed)
    (assert-tombstones vp-granule-ids 12)
    (ingest/delete-concept (d/item->concept ingest-result) {:revision-id 14})
    (index/wait-until-indexed)
    (assert-tombstones vp-granule-ids 14)))

(defn- get-any-granule-entry-triplet
  "Get granule entry triplet consisting of entry title, concept id and granule ur for any one
  granule in the collection with the given entry-title."
  [entry-title]
  (let [granule-refs (:refs (search/find-refs
                              :granule {:entry-title entry-title
                                        :page-size 1}))
        {granule-id :id granule-ur :name} (first granule-refs)]
    {:entry-title entry-title
     :concept-id granule-id
     :granule-ur granule-ur}))

(defn- ingest-ast-granule
  "Ingest an AST-L1A granule with the given granule-ur"
  [ast-coll gran-ur]
  (let [ast-l1a-gran (dg/granule ast-coll {:granule-ur gran-ur})
        ingest-result (d/ingest "LPDAAC_ECS" ast-l1a-gran)]
    {:entry-title (:entry-title ast-coll)
     :concept-id (:concept-id ingest-result)
     :granule-ur gran-ur}))

(deftest translate-granule-entries-test
  (let [ast-entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
        ast-coll (d/ingest "LPDAAC_ECS"
                           (dc/collection
                             {:entry-title ast-entry-title}))
        vp-colls (ingest-virtual-collections [ast-coll])
        source-granule (ingest-ast-granule ast-coll "SC:AST_L1A.003:2006227720")
        _ (index/wait-until-indexed)
        virtual-granule1 (get-any-granule-entry-triplet (:entry-title (first vp-colls)))
        virtual-granule2 (get-any-granule-entry-triplet (:entry-title (second vp-colls)))
        virtual-granule3 (get-any-granule-entry-triplet (:entry-title (nth vp-colls 2)))
        virtual-granule4 (get-any-granule-entry-triplet (:entry-title (nth vp-colls 3)))

        ;; non-virtual granule with the same granule ur as a virtual granule but from a different provider than LPDAAC_ECS
        non-virtual-granule1 {:entry-title "entry-title1"
                              :concept-id "G1234-PROV1"
                              :granule-ur (:granule-ur virtual-granule1)}
        ;; non-virtual granule with the same provider id as a virtual granule
        non-virtual-granule2 {:entry-title "non virtual entry title"
                              :concept-id "G1234-LPDAAC_ECS"
                              :granule-ur "granule-ur2"}
        non-virtual-granule3 {:entry-title "entry-title3"
                              :concept-id "G5678-PROV"
                              :granule-ur "granule-ur3"}]

    (testing "Valid input to translate-granule-entries end-point"
      (util/are2 [input expected]
                 (let [response (vp/translate-granule-entries (json/generate-string input))]
                   (= expected (json/parse-string (:body response) true)))

                 "Input with no virtual granules should return the original response"
                 [non-virtual-granule1 non-virtual-granule2]
                 [non-virtual-granule1 non-virtual-granule2]

                 "Virtual granule should be translated to corresponding source granule"
                 [non-virtual-granule1 virtual-granule1]
                 [non-virtual-granule1 source-granule]

                 "The order of the output granules should match the corresponding input"
                 [source-granule non-virtual-granule1 virtual-granule1 non-virtual-granule2 virtual-granule2 virtual-granule3 non-virtual-granule3 virtual-granule4]
                 [source-granule non-virtual-granule1 source-granule non-virtual-granule2 source-granule source-granule non-virtual-granule3 source-granule]))

    (testing "Malformed JSON"
      (let [malformed-json (str/replace (json/generate-string [virtual-granule1]) #"}" "]")
            response (vp/translate-granule-entries malformed-json)
            errors (:errors (json/parse-string (:body response) true))]
        (is (= 1 (count errors)))
        (is (.startsWith (first errors) "Invalid JSON: Unexpected close marker ']': expected '}'"))))

    (testing "Invalid input to translate-granule-items end-point should result in error"
      (let [invalid-json (json/generate-string [virtual-granule1
                                                (dissoc virtual-granule1 :concept-id)])
            response (vp/translate-granule-entries invalid-json)
            errors (:errors (json/parse-string (:body response) true))]
        (and (= 400 (:status response))
             (= ["/1 object has missing required properties ([\"concept-id\"])"] errors))))))
