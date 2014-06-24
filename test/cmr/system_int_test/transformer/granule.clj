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
        g1 (d/ingest "PROV1" (dg/granule col1))
        g2 (dg/granule col1)
       	g2 (d/ingest "PROV1" g2)
        g2 (d/ingest "PROV1" g2)]
    (testing "transform granules"
      (are [v]
           (t/transform-and-compare v)
           [[g1 1]]
           [[g1 1] [g2 1]]
           [[g1 1] [g2 2]]))
    (testing "tranform missing granule revision returns 404"
      (let [resp (t/transform-concepts [[(:concept-id g1) 5]] "application/echo10+xml")]
        (is (= 404 (:status resp)))))))
