(ns cmr.indexer.test.data.concepts.association-util
  "Code coverage tests for the functions of testing cmr.indexer.data.concepts.tag
   namespace."
  (:require
   [clojure.test :refer :all]
   [cmr.indexer.data.concepts.association-util :as assoc-util]))

(deftest concept-named-assoc-list->generic-assocs-test

  (testing "Tests that building a concept named association list from a 
            generics association list works."
    (let [generic-associations ["DQS1200000012-PROV1"
                                "DQS1200000013-PROV1"
                                "OO1200000014-PROV1"
                                "SO1200000015-PROV1"
                                "SO1200000016-PROV1"
                                "SE1200000017-PROV1"
                                "GRD1200000018-PROV1"]
          expected {:dataqualitysummaries ["DQS1200000012-PROV1" "DQS1200000013-PROV1"]
                    :orderoptions ["OO1200000014-PROV1"]
                    :serviceoptions ["SO1200000015-PROV1" "SO1200000016-PROV1"]
                    :serviceentries ["SE1200000017-PROV1"]
                    :grids ["GRD1200000018-PROV1"]}]
      (is (= expected (assoc-util/generic-assoc-list->assoc-struct generic-associations))))))
