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
            [cmr.system-int-test.system :as s]
            [cmr.common.time-keeper :as tk]
            [cheshire.core :as json]
            [cmr.common.util :as util]
            [clj-time.core :as t]))

(def virtual-product-providers
  "Returns a list of the provider ids of the providers with virtual products."
  (->> vp-config/source-to-virtual-product-config
       keys
       (map first)))


(use-fixtures :each (ingest/reset-fixture (into {} (for [p virtual-product-providers]
                                                     [(str p "_guid") p]))))

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
    (assoc (dc/collection (merge (dissoc source-collection
                                         :revision-id :native-id :concept-id :entry-id :product)
                                 virtual-coll-attribs ))
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
        expected-granule-urs (granule->expected-virtual-granule-urs ast-l1a-gran)
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

(defn- get-sample-virtual-granule-entry
  "Get any virtual granule entry for a given virtual-entry-title"
  [virtual-entry-title]
  (let [virtual-granule-refs (:refs (search/find-refs
                                      :granule {:entry-title virtual-entry-title
                                                :page-size 1}))
        [virtual-granule-id virtual-granule-ur] ((juxt :id :name) (first virtual-granule-refs))]
    {:entry-title virtual-entry-title
     :concept-id virtual-granule-id
     :granule-ur virtual-granule-ur}))


(deftest translate-granule-entries-test
  (let [ast-coll (d/ingest "LPDAAC_ECS"
                           (dc/collection
                             {:entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"}))
        vp-colls (ingest-virtual-collections [ast-coll])
        granule-ur "SC:AST_L1A.003:2006227720"
        ast-l1a-gran (dg/granule ast-coll {:granule-ur granule-ur})
        ingest-result (d/ingest "LPDAAC_ECS" ast-l1a-gran)
        _ (index/wait-until-indexed)
        source-granule {:entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
                        :concept-id (:concept-id ingest-result)
                        :granule-ur granule-ur}
        virtual-granule1 (get-sample-virtual-granule-entry (:entry-title (first vp-colls)))
        virtual-granule2 (get-sample-virtual-granule-entry (:entry-title (second vp-colls)))
        other-granule {:entry-title "entry-title"
                       :concept-id "G1234-PROV1"
                       :granule-ur "granule-ur"}]

    (testing "Valid input to translate-granule-entries end-point"
      (util/are2 [input expected]
                 (let [response (vp/translate-granule-entries (json/generate-string input))]
                   (= (set expected) (set (json/parse-string (:body response) true))))

                 "Input with no virtual granules should return the original response"
                 [other-granule]
                 [other-granule]

                 "Virtual granule should be translated to corresponding source granule"
                 [other-granule virtual-granule1]
                 [other-granule source-granule]

                 "Multiple virtual granules based on same source granule should result in a single
                 entry for the source granule in the translated response"
                 [virtual-granule1 virtual-granule2]
                 [source-granule]))

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
