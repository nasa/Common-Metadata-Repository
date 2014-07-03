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
      (are [concepts]
           (= (t/expected-response concepts :echo10)
               (:response (t/transform-concepts concepts :echo10)))
           [col1-1]
           [col1-1 col2-1]
           [col1-1 col2-2]))
    (testing "transform missing collection revision returns 404"
      (let [resp (t/transform-concepts [(assoc col1-1 :revision-id 5)] :echo10)]
        (is (= 404 (:status resp)))))))

(deftest transform-latest-collection-echo10
  (let [col1-1 (d/ingest "PROV1" (dc/collection {:short-name "MINIMAL"
                                               :long-name "A minimal valid collection"
                                               :version-id 1}))
        col2 (dc/collection {:short-name "MINIMAL2"
                             :long-name "A second minimal valid collection."
                             :version-id 3})
        col2-1 (d/ingest "PROV1" col2)
        col2-2 (d/ingest "PROV1" (assoc col2-1 :entry-id "MINIMAL2-2"))]
    (testing "transform collections"
      (are [sent-ids expected]
           (= (t/expected-response expected :echo10)
               (:response (t/transform-latest-concepts sent-ids :echo10)))
           [(:concept-id col1-1)] [col1-1]
           [(:concept-id col1-1) (:concept-id col2-1)] [col1-1 col2-2]))
    (testing "transform missing concept-id returns 404"
      (let [resp (t/transform-latest-concepts ["C1234-PROV1"] :echo10)]
        (is (= 404 (:status resp)))))))