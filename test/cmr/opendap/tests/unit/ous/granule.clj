(ns cmr.opendap.tests.unit.ous.granule
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.ous.granule :as granule]
    [ring.util.codec :as codec]))

(deftest build-query
  (testing "No granules ..."
    (is (= "collection_concept_id=C123"
           (granule/build-query
            {:collection-id "C123"})))
    (is (= "collection_concept_id=C123"
           (granule/build-query
            {:collection-id "C123"
             :granules []})))
    (is (= "collection_concept_id=C123"
           (granule/build-query
            {:collection-id "C123"
             :granules [nil]})))
    (is (= "collection_concept_id=C123"
           (granule/build-query
            {:collection-id "C123"
             :granules [nil nil]})))
    (is (= "collection_concept_id=C123"
           (granule/build-query
            {:collection-id "C123"
             :granules [""]})))
    (is (= "collection_concept_id=C123"
           (granule/build-query
            {:collection-id "C123"
             :granules ["" ""]}))))
  (testing "With granule ids ..."
    (is (= "collection_concept_id=C123&page_size=1&concept_id%5B%5D=G234"
           (granule/build-query
            {:collection-id "C123"
             :granules ["G234"]})))
    (is (= (str "collection_concept_id=C123&page_size=2&"
                "concept_id%5B%5D=G234&concept_id%5B%5D=G345")
           (granule/build-query
            {:collection-id "C123"
             :granules ["G234" "G345"]}))))
  (testing "With granule ids and exclude ..."
    (is (= (str "collection_concept_id=C123&page_size=2000&"
                "exclude%5Becho_granule_id%5D%5B%5D=G234")
           (granule/build-query
            {:collection-id "C123"
             :granules ["G234"]
             :exclude-granules true})))
    (is (= (str "collection_concept_id=C123&page_size=2000&"
                "exclude%5Becho_granule_id%5D%5B%5D=G234&"
                "exclude%5Becho_granule_id%5D%5B%5D=G345")
           (granule/build-query
            {:collection-id "C123"
             :granules ["G234" "G345"]
             :exclude-granules true}))))
  (testing "With exclude but no granule ids..."
    (is (= "collection_concept_id=C123"
           (granule/build-query
            {:collection-id "C123"
             :exclude-granules true})))
    (is (= "collection_concept_id=C123"
           (granule/build-query
            {:collection-id "C123"
             :exclude-granules false}))))
  (testing "With temporal ...."
    (let [result (granule/build-query
                  {:collection-id "C123"
                   :granules ["G234" "G345"]
                   :temporal "2002-09-01T00:00:00Z,2016-07-03T00:00:00Z"})]
      (is (= (str "collection_concept_id=C123&page_size=2&"
                  "concept_id%5B%5D=G234&concept_id%5B%5D=G345&"
                  "temporal%5B%5D=2002-09-01T00%3A00%3A00Z%2C2016-07-03T00%3A00%3A00Z")
             result))
      (is (= (str "collection_concept_id=C123&page_size=2&"
                  "concept_id[]=G234&concept_id[]=G345&"
                  "temporal[]=2002-09-01T00:00:00Z,2016-07-03T00:00:00Z")
             (codec/url-decode result))))))
