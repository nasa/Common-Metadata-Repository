(ns cmr.system-int-test.transformer.collection
  "Integration test for CMR transformer for collections"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.transformer :as t]))

(use-fixtures :each (ingest/reset-fixture "PROV1"))

(deftest transform-collection-echo10
  (let [col1-1 (d/ingest "PROV1" (dc/collection {:short-name "MINIMAL"
                                               :long-name "A minimal valid collection"
                                               :version-id 1}))
        col2 (dc/collection {:short-name "MINIMAL2"
                             :long-name "A second minimal valid collection."
                             :version-id 3})
        col2-1 (d/ingest "PROV1" col2)
        col2-2 (d/ingest "PROV1" col2)]
    (testing "transform collections"
      (are [v]
           (= (t/expected-response v :echo10)
               (:response (t/transform-concepts v :echo10)))
           [col1-1]
           [col1-1 col2-1]
           [col1-1 col2-2]))
    (testing "tranform missing collection revision returns 404"
      (let [resp (t/transform-concepts [(assoc col1-1 :revision-id 5)] :echo10)]
        (is (= 404 (:status resp)))))))
