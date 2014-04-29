(ns cmr.system-int-test.search.granule-retrieval-test
  "Integration test for granule retrieval with cmr-concept-id"
  (:require [clojure.test :refer :all]
            [cmr.umm.echo10.granule :as g]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1"))

(deftest retrieve-granule-by-cmr-concept-id
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule1"}))]
    (index/flush-elastic-index)
    (testing "retrieval by granule cmr-concept-id."
      (let [response (search/retrieve-concept-metadata-by-id (:concept-id gran1))
            parsed-granule (g/parse-granule (:body response))]
        (is (= "Granule1" (:granule-ur parsed-granule)))))
    (testing "retrieval by granule cmr-concept-id, not found."
      (let [response (search/retrieve-concept-metadata-by-id "G1111-CMR_PROV1")]
        (is (= 404 (:status response)))
        (re-find #"Failed to retrieve concept G1111-CMR_PROV1 from metadata-db:" (:body response))
        (re-find #"Concept with concept-id \[G1111-CMR_PROV1\] does not exist" (:body response))))))
