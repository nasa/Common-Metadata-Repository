(ns cmr.system-int-test.search.paging-search-test
  "Tests for search paging."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d2c]))

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

(deftest search-with-page-size
  (create-collections)
  (testing "Search with page_size."
    (let [{:keys [refs]} (search/find-refs :collection {:page_size 5})]
      (is (= 5 (count refs)))))
  (testing "Search with large page_size."
    (let [{:keys [refs]} (search/find-refs :collection {:page_size 100})]
      (is (= collection-count (count refs)))))
  (testing "page_size less than one."
    (try
      (search/find-refs :collection {:page_size 0})
      (catch clojure.lang.ExceptionInfo e
        (let [status (get-in (ex-data e) [:object :status])
              body (get-in (ex-data e) [:object :body])]
          (is (= 400 status))
          (is (re-matches #".*page_size must be a number between 1 and 2000.*" body))))))
  (testing "Negative page_size."
    (try
      (search/find-refs :collection {:page_size -1})
      (catch clojure.lang.ExceptionInfo e
        (let [status (get-in (ex-data e) [:object :status])
              body (get-in (ex-data e) [:object :body])]
          (is (= 400 status))
          (is (re-matches #".*page_size must be a number between 1 and 2000.*" body))))))
  (testing "page_size too large."
    (try
      (search/find-refs :collection {:page_size 2001})
      (catch clojure.lang.ExceptionInfo e
        (let [status (get-in (ex-data e) [:object :status])
              body (get-in (ex-data e) [:object :body])]
          (is (= 400 status))
          (is (re-matches #".*page_size must be a number between 1 and 2000.*" body))))))
  (testing "Non-numeric page_size"
    (try
      (search/find-refs :collection {:page_size "ABC"})
      (catch clojure.lang.ExceptionInfo e
        (let [status (get-in (ex-data e) [:object :status])
              body (get-in (ex-data e) [:object :body])]
          (is (= 400 status))
          (is (re-matches #".*page_size must be a number between 1 and 2000.*" body)))))))

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
      (try
        (search/find-refs :collection {:provider "PROV1"
                                       :page_size 5
                                       :page_num 0})
        (catch clojure.lang.ExceptionInfo e
          (let [status (get-in (ex-data e) [:object :status])
                body (get-in (ex-data e) [:object :body])]
            (is (= 400 status))
            (is (re-matches #".*page_num must be a number greater than or equal to 1.*" body))))))
    (testing "Non-numeric page_num."
      (try
        (search/find-refs :collection {:provider "PROV1"
                                       :page_size 5
                                       :page_num "ABC"})
        (catch clojure.lang.ExceptionInfo e
          (let [status (get-in (ex-data e) [:object :status])
                body (get-in (ex-data e) [:object :body])]
            (is (= 400 status))
            (is (re-matches #".*page_num must be a number greater than or equal to 1.*" body))))))))
