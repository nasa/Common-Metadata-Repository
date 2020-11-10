(ns cmr.system-int-test.search.paging-search-test
  "Tests for search paging."
  (:require
   [clojure.test :refer :all]
   [cmr.common.concepts :as concepts]
   [cmr.common.util :refer [are3]]
   [cmr.search.services.parameters.parameter-validation :as pm]
   [cmr.system-int-test.data2.core :as d2c]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(def prov1-collection-count 10)
(def prov1-deleted-collection-count 3)
(def prov2-collection-count 15)

(def collection-count (+ prov2-collection-count prov1-collection-count))

(def revision-count (+ (* 2 prov1-collection-count)
                       prov2-collection-count
                       (* 2 prov1-deleted-collection-count)))



(defn create-collections
  "Set up the fixtures for tests."
  []
  (let [prov1-colls (doall (for [n (range prov1-collection-count)
                                 :let [coll-r1 (d2c/ingest-umm-spec-collection "PROV1" (data-umm-c/collection n {}))]]
                             [coll-r1
                              (d2c/ingest-umm-spec-collection "PROV1" (dissoc coll-r1 :revision-id))]))
        prov1-deleted-colls (doall (for [n (range prov1-deleted-collection-count)
                                         :let [coll-r1 (d2c/ingest-umm-spec-collection "PROV1" (data-umm-c/collection (+ 100 n) {}))]]
                                     (do
                                       (ingest/delete-concept (d2c/umm-c-collection->concept coll-r1))
                                       [coll-r1 (assoc coll-r1 :deleted true
                                                       :revision-id 2)])))

        prov2-colls (doall (for [n (range prov2-collection-count)]
                             (d2c/ingest-umm-spec-collection "PROV2" (data-umm-c/collection n {}))))]
    (index/wait-until-indexed)

    (flatten (concat prov1-colls prov1-deleted-colls prov2-colls))))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(defn assert-exceeds-paging-depth
  [{:keys [status errors]}]
  (is (= 400 status))
  (is (re-matches #"The paging depth \(page_num \* page_size(?: or offset)?\) of \[\d*?\] exceeds the limit of \d*?.*?"
                  (or (first errors) "No errors"))))

(deftest page-depth-test
  (testing "Exceeded page depth"
    (are3 [resp]
      (assert-exceeds-paging-depth resp)

      "Collection query with page_size and page_num"
      (search/find-refs :collection {:page-size 10 :page-num 1000000000})

      "All granules query"
      (search/find-refs :granule {:page-size 500 :page-num 22})))

  (testing "page size and offset exceed limit"
    (let [{:keys [status errors]} (search/find-refs :granule
                                                    {:collection_concept_id "C1-PROV1"
                                                     :page-size 5
                                                     :offset 1000000})]
      (is (= 400 status))
      (is (= ["The paging depth (page_size + offset) of [1000005] exceeds the limit of 1000000."]
             errors))))

  (testing "Within page depth"
    (are3 [resp]
      ;; This means the query was successful.
      (is (= 0 (:hits resp)))

      "Collection query with page_size and page_num"
      (search/find-refs :collection {:page-size 10 :page-num 100000})

      "All granules query"
      (search/find-refs :granule {:page-size 500 :page-num 20})

      "Granules in a specific provider query can go to a higher page number"
      (search/find-refs :granule {:page-size 500 :page-num 22 :provider-id "foo"}))))

(deftest search-with-page-size
  (create-collections)

  (testing "Search with page_size."
    (let [{:keys [refs]} (search/find-refs :collection {:page_size 5})]
      (is (= 5 (count refs)))))
  (testing "Search with large page_size."
    (let [{:keys [refs]} (search/find-refs :collection {:page_size 100})]
      (is (= collection-count (count refs)))))
  (testing "all revision page size"
    (let [{:keys [refs]} (search/find-refs :collection {:page_size 5 :all-revisions true})]
      (is (= 5 (count refs)))))
  (testing "page_size less than zero."
    (let [resp (search/find-refs :collection {:page_size -1})
          {:keys [status errors]} resp]
      (is (= 400 status))
      (is (re-matches #".*page_size must be a number between 0 and 2000.*" (first errors)))))
  (testing "page_size too large."
    (let [resp (search/find-refs :collection {:page_size 2001})
          {:keys [status errors]} resp]
      (is (= 400 status))
      (is (re-matches #".*page_size must be a number between 0 and 2000.*" (first errors)))))
  (testing "Non-numeric page_size"
    (let [resp (search/find-refs :collection {:page_size "ABC"})
          {:keys [status errors]} resp]
      (is (= 400 status))
      (is (re-matches #".*page_size must be a number between 0 and 2000.*" (first errors)))))
  (testing "Vector page_size"
    (let [resp (search/find-refs :collection {:page_size [10 20]})
          {:keys [status errors]} resp]
      (is (= 400 status))
      (is (= "Parameter [page_size] must have a single value." (first errors))))))

(deftest search-for-hits
  (create-collections)
  (are [hits params]
       (= hits (:hits (search/find-refs :collection params)))
       collection-count {}
       revision-count {:all-revisions true}
       0 {:provider "NONE"}
       prov1-collection-count {:provider "PROV1"}
       prov2-collection-count {:provider "PROV2"}))

(defn get-concept-id-revision-id
  "Returns a tuple of concept id and revision id. Works with references or collections."
  [c]
  [(or (:concept-id c) (:id c))
   (:revision-id c)])

(defn concept-id->sequence-number
  "Returns the sequence number used for sorting in elastic in leau of concept-id"
  [concept-id]
  (:sequence-number (concepts/parse-concept-id concept-id)))

(defn sort-collections-all-revisions
  "Sorts a list of collections using the same default sort as search for all revisions."
  [collections]
  (sort-by identity
           (fn [c1 c2]
             (let [et1 (:EntryTitle c1)
                   et2 (:EntryTitle c2)
                   p1 (:provider-id c1)
                   p2 (:provider-id c2)
                   seq1 (concept-id->sequence-number (:concept-id c1))
                   seq2 (concept-id->sequence-number (:concept-id c2))]
               (cond
                 (not= et1 et2) (compare et1 et2)
                 (not= p1 p2) (compare p1 p2)
                 (not= seq1 seq2) (compare seq1 seq2)
                 :else (compare (:revision-id c2) (:revision-id c1)))))
           collections))

(defn sort-collections-latest-revisions
  "Sorts a list of collections using the same default sort as search for latest revisions."
  [collections]
  (sort-by identity
           (fn [c1 c2]
             (let [et1 (:entry-title c1)
                   et2 (:entry-title c2)
                   p1 (:provider-id c1)
                   p2 (:provider-id c2)
                   seq1 (concept-id->sequence-number (:concept-id c1))
                   seq2 (concept-id->sequence-number (:concept-id c2))]
               (cond
                 (not= et1 et2) (compare et1 et2)
                 (not= p1 p2) (compare p1 p2)
                 :else (compare seq1 seq2))))
           collections))

(defn select-page
  "Manually selects a page of collections."
  [collections page-num page-size]
  (nth (partition-all page-size collections) (dec page-num)))


(deftest search-all-revisions-with-page-num
  (let [all-collections (sort-collections-all-revisions (create-collections))]

    (testing "Find everything on one big page"
      (let [{:keys [refs]} (search/find-refs :collection {:page_size 1000
                                                          :all-revisions true})]
        (is (= (map get-concept-id-revision-id all-collections)
               (map get-concept-id-revision-id refs)))))

    (testing "Find by page 1"
      (let [{:keys [refs]} (search/find-refs :collection {:page_size 12
                                                          :page_num 1
                                                          :all-revisions true})]
        (is (= (map get-concept-id-revision-id (select-page all-collections 1 12))
               (map get-concept-id-revision-id refs)))))

    (testing "Find by page 2"
      (let [{:keys [refs]} (search/find-refs :collection {:page_size 12
                                                          :page_num 2
                                                          :all-revisions true})]
        (is (= (map get-concept-id-revision-id (select-page all-collections 2 12))
               (map get-concept-id-revision-id refs)))))))


(deftest search-with-page-num
  (let [provider-id "PROV1"
        collections (doall (for [n (range 10)]
                             (d2c/ingest-umm-spec-collection provider-id
                                         (data-umm-c/collection n {}))))]
    (index/wait-until-indexed)
    (testing "Search with page_num."
      (let [{:keys [refs]} (search/find-refs :collection {:provider "PROV1"
                                                          :page_size 5
                                                          :page_num 2})]
        (is (= (map get-concept-id-revision-id (select-page (sort-collections-latest-revisions
                                                              collections)
                                                            2 5))
               (map get-concept-id-revision-id refs)))))
    (testing "page_num less than one."
      (let [resp (search/find-refs :collection {:provider "PROV1"
                                                :page_size 5
                                                :page_num 0})
            {:keys [status errors]} resp]
        (is (= 400 status))
        (is (re-matches #".*page_num must be a number greater than or equal to 1.*"
                        (first errors)))))
    (testing "Non-numeric page_num."
      (let [resp (search/find-refs :collection {:provider "PROV1"
                                                :page_size 5
                                                :page_num "ABC"})
            {:keys [status errors]} resp]
        (is (= 400 status))
        (is (re-matches #".*page_num must be a number greater than or equal to 1.*"
                        (first errors)))))
    (testing "Vector page_num."
      (let [resp (search/find-refs :collection {:provider "PROV1"
                                                :page_size 5
                                                :page_num [1 2]})
            {:keys [status errors]} resp]
        (is (= 400 status))
        (is (= "Parameter [page_num] must have a single value." (first errors)))))))


(deftest search-with-offset
  (create-collections)
  (testing "invalid offset param values"
    (are [offset]
      (= "offset must be an integer between zero and 1000000"
         (first (:errors (search/find-refs :collection {"offset" offset}))))
      "asdf"
      "-1"
      "1000001"))
  (testing "invalid combination of offset and page-num"
    (is (= "Only one of offset or page-num may be specified"
           (first (:errors (search/find-refs :collection {"page_num" 2 "offset" 100}))))))
  (testing "with valid offset"
    (is (= (:refs (search/find-refs :collection {"page_num" 1 "page_size" 5}))
           (:refs (search/find-refs :collection {"offset" 0 "page_size" 5}))))
    (is (= (:refs (search/find-refs :collection {"page_num" 2 "page_size" 5}))
           (:refs (search/find-refs :collection {"offset" 5 "page_size" 5}))))
    (is (= [] (:refs (search/find-refs :collection {"offset" 25 "page_size" 10}))))))
