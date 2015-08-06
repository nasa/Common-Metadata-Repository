(ns cmr.system-int-test.search.paging-search-test
  "Tests for search paging."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.common.concepts :as concepts]
            [cmr.system-int-test.data2.core :as d2c]
            [cmr.search.services.parameters.parameter-validation :as pm]))

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
                                 :let [coll-r1 (d2c/ingest "PROV1" (dc/collection))]]
                             [coll-r1
                              (d2c/ingest "PROV1" (dissoc coll-r1 :revision-id))]))
        prov1-deleted-colls (doall (for [n (range prov1-deleted-collection-count)
                                         :let [coll-r1 (d2c/ingest "PROV1" (dc/collection))]]
                                     (do
                                       (ingest/delete-concept (d2c/item->concept coll-r1))
                                       [coll-r1 (assoc coll-r1 :deleted true
                                                       :revision-id 2)])))

        prov2-colls (doall (for [n (range prov2-collection-count)]
                             (d2c/ingest "PROV2" (dc/collection))))]
    (index/wait-until-indexed)

    (flatten (concat prov1-colls prov1-deleted-colls prov2-colls))))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest search-with-page-size
  (create-collections)

  (testing "Exceeded page depth (page-num * page-size)"
    (let [page-size 10
          page-num 1000000000]
      (let [resp (search/find-refs :collection
                                   {:page_size page-size
                                    :page-num page-num})
            {:keys [status errors]} resp]
        (is (= 400 status))
        (is (re-matches #"The paging depth \(page_num \* page_size\) of \[\d*?\] exceeds the limit of \d*?.*?"
                        (first errors))))))

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
             (let [et1 (:entry-title c1)
                   et2 (:entry-title c2)
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
        [col1 col2 col3 col4 col5 col6 col7 col8 col9 col10] (for [n (range 10)]
                                                               (d2c/ingest provider-id
                                                                           (dc/collection {})))]
    (index/wait-until-indexed)
    (testing "Search with page_num."
      (let [{:keys [refs]} (search/find-refs :collection {:provider "PROV1"
                                                          :page_size 5
                                                          :page_num 2})]
        (is (= (map :short-name [col5 col4 col3 col2 col1])
               (map :short-name refs)))))
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
