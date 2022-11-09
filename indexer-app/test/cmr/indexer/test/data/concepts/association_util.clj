(ns cmr.indexer.test.data.concepts.association-util
  "Code coverage tests for the functions of testing cmr.indexer.data.concepts.association-util
   namespace."
  (:require
   [clojure.test :refer :all]
   [cmr.indexer.data.concepts.association-util :as assoc-util]))

(deftest concept-named-assoc-list->assocs-test

  (testing "Tests that building a concept named association list from a 
            association list works."
    (let [associations [{:source-concept-identifier "C1200000021-PROV1"
                         :associated-concept-id "DQS1200000012-PROV1"
                         :data {:hello "ok"}}
                        {:source-concept-identifier "C1200000021-PROV1"
                         :associated-concept-id "DQS1200000013-PROV1"}
                        {:source-concept-identifier "C1200000021-PROV1"
                         :associated-concept-id "OO1200000014-PROV1"}
                        {:source-concept-identifier "C1200000021-PROV1"
                         :associated-concept-id "SO1200000015-PROV1"}
                        {:source-concept-identifier "SO1200000016-PROV1"
                         :associated-concept-id "C1200000021-PROV1"}
                        {:source-concept-identifier "C1200000021-PROV1"
                         :associated-concept-id "SE1200000017-PROV1"}
                        {:source-concept-identifier "C1200000021-PROV1"
                         :source-revision-id 2
                         :associated-concept-id "GRD1200000018-PROV1"
                         :associated-revision-id 4}
                        {:service-concept-id "S1200000022-PROV1"
                         :associated-concept-id "C1200000021-PROV1"}
                        {:tool-concept-id "TL1200000023-PROV1"
                         :associated-concept-id "C1200000021-PROV1"}
                        {:variable-concept-id "V1200000024-PROV1"
                         :associated-concept-id "C1200000021-PROV1"}]
          expected {:data-quality-summaries [{:concept-id "DQS1200000012-PROV1" :data {:hello "ok"}} 
                                           {:concept-id "DQS1200000013-PROV1"}]
                    :order-options [{:concept-id "OO1200000014-PROV1"}]
                    :service-options [{:concept-id "SO1200000015-PROV1"} {:concept-id "SO1200000016-PROV1"}]
                    :service-entries [{:concept-id "SE1200000017-PROV1"}]
                    :grids [{:concept-id "GRD1200000018-PROV1" :revision-id 4}]
                    :services [{:concept-id "S1200000022-PROV1"}] 
                    :tools [{:concept-id "TL1200000023-PROV1"}] 
                    :variables [{:concept-id "V1200000024-PROV1"}]}]
      (is (= expected (assoc-util/assoc-list->assoc-struct associations "C1200000021-PROV1"))))))
