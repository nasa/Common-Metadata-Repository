(ns cmr.system-int-test.transformer.granule
  "Integration test for CMR transformer for granules"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.transformer :as t]))

(use-fixtures :each (ingest/reset-fixture "PROV1"))

(deftest transform-granule-echo10
  (let [col1 (d/ingest "PROV1" (dc/collection {:short-name "MINIMAL"
                                               :long-name "A minimal valid collection"
                                               :version-id 1}))
        g1-1 (d/ingest "PROV1" (dg/granule col1))
        g2 (dg/granule col1)
       	g2-1 (d/ingest "PROV1" g2)
        g2-2 (d/ingest "PROV1" g2)]
    (testing "transform granules to echo10"
      (are [concepts]
           (= (t/expected-response concepts :echo10)
              (:response (t/transform-concepts concepts :echo10)))
           [g1-1]
           [g1-1 g2-1]
           [g1-1 g2-2]))
    (testing "transform missing granule revision returns 404"
      (let [resp (t/transform-concepts [(assoc g1-1 :revision-id 5)] :echo10)]
        (is (= 404 (:status resp)))))))

(deftest transform-latest-granule-echo10
  (let [col1 (d/ingest "PROV1" (dc/collection {:short-name "MINIMAL"
                                               :long-name "A minimal valid collection"
                                               :version-id 1}))
        g1-1 (d/ingest "PROV1" (dg/granule col1))
        g2 (dg/granule col1)
        g2-1 (d/ingest "PROV1" g2)
        g2-2 (d/ingest "PROV1" g2)]
    (testing "transform latest revision of granules to echo10"
      (are [sent expected]
           (= (t/expected-response expected :echo10)
              (:response (t/transform-latest-concepts sent :echo10)))
           [(:concept-id g1-1)] [g1-1]
           [(:concept-id g1-1) (:concept-id g2-1)] [g1-1 g2-2]))
    (testing "transform latest with missing concept-id returns 404"
      (let [resp (t/transform-latest-concepts ["G1234-PROV1"] :echo10)]
        (is (= 404 (:status resp)))))))

