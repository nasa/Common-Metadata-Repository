(ns cmr.opendap.tests.unit.ous.granule
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.ous.granule :as granule]))

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
    (is (= "collection_concept_id=C123&concept_id\\[\\]=G234"
           (granule/build-query
            {:collection-id "C123"
             :granules ["G234"]})))
    (is (= "collection_concept_id=C123&concept_id\\[\\]=G234&concept_id\\[\\]=G345"
           (granule/build-query
            {:collection-id "C123"
             :granules ["G234" "G345"]}))))
  (testing "With granule ids and exclude ..."
    (is (= (str "collection_concept_id=C123&"
                "exclude\\[echo_granule_id\\]\\[\\]=G234")
           (granule/build-query
            {:collection-id "C123"
             :granules ["G234"]
             :exclude-granules true})))
    (is (= (str "collection_concept_id=C123&"
                "exclude\\[echo_granule_id\\]\\[\\]=G234&"
                "exclude\\[echo_granule_id\\]\\[\\]=G345")
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
             :exclude-granules false})))))
