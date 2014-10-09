(ns cmr.system-int-test.search.paging-search-test
  "Tests for search paging."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d2c]
            [cmr.search.services.parameters.parameter-validation :as pm]))

(def prov1-collection-count 10)
(def prov2-collection-count 15)

(def collection-count (+ prov2-collection-count prov1-collection-count))

(defn create-collections
  "Set up the fixtures for tests."
  []
  (dotimes [n prov1-collection-count]
    (d2c/ingest "PROV1" (dc/collection)))
  (dotimes [n prov2-collection-count]
    (d2c/ingest "PROV2" (dc/collection)))
  (index/refresh-elastic-index))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest search-with-page-size-and-page-num
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
                        (first errors)))))))

(deftest search-with-page-size
  (create-collections)
  (testing "Search with page_size."
    (let [{:keys [refs]} (search/find-refs :collection {:page_size 5})]
      (is (= 5 (count refs)))))
  (testing "Search with large page_size."
    (let [{:keys [refs]} (search/find-refs :collection {:page_size 100})]
      (is (= collection-count (count refs)))))
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
       0 {:provider "NONE"}
       prov1-collection-count {:provider "PROV1"}
       prov2-collection-count {:provider "PROV2"}))

(deftest search-with-page-num
  (let [provider-id "PROV1"
        [col1 col2 col3 col4 col5 col6 col7 col8 col9 col10] (for [n (range 10)]
                                                               (d2c/ingest provider-id
                                                                           (dc/collection {})))]
    (index/refresh-elastic-index)
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
