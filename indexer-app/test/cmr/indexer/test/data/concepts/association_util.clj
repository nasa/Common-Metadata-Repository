(ns cmr.indexer.test.data.concepts.association-util
  "Code coverage tests for the functions of testing cmr.indexer.data.concepts.association-util
   namespace."
  (:require
   [clojure.test :refer :all]
   [cmr.indexer.data.concepts.association-util :as assoc-util]))

(deftest concept-named-assoc-list->generic-assocs-test

  (testing "Tests that building a concept named association list from a 
            generics association list works."
    (let [generic-associations [{:source-concept-identifier "C1200000021-PROV1"
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
                                 :associated-concept-id "GRD1200000018-PROV1"}]
          expected {:dataqualitysummaries [{:concept-id "DQS1200000012-PROV1" :data {:hello "ok"}} 
                                           {:concept-id "DQS1200000013-PROV1"}]
                    :orderoptions [{:concept-id "OO1200000014-PROV1"}]
                    :serviceoptions [{:concept-id "SO1200000015-PROV1"} {:concept-id "SO1200000016-PROV1"}]
                    :serviceentries [{:concept-id "SE1200000017-PROV1"}]
                    :grids [{:concept-id "GRD1200000018-PROV1"}]}]
      (is (= expected (assoc-util/generic-assoc-list->assoc-struct generic-associations "C1200000021-PROV1"))))))
