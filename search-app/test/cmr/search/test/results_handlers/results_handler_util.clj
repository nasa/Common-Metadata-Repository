(ns cmr.search.test.results-handlers.results-handler-util
  (:require [clojure.test :refer :all]
            [cmr.common.util :as util :refer [are3]]
            [cmr.search.results-handlers.results-handler-util :as rs-util]))

(deftest convert-associations-test
   (let [associations {:variables [{:concept-id "V1200000019-PROV1"} {:concept-id "V1200000021-PROV1"}]
                       :services [{:concept-id "S1200000009-PROV1" :data {:convert-format {:XYZ "ZYX"}, :allow-regridding "true"}}]
                       :tools [{:concept-id "TL1200000010-PROV1"}]
                       :data-quality-summaries [{:concept-id "DQS1200000012-PROV1", :data {:XYZ "ZYX"}, :revision-id 1}],
                       :order-options [{:concept-id "OO1200000014-PROV1"} {:concept-id "OO1200000015-PROV1"}]}
         expected-detail {:variables [{:concept-id "V1200000019-PROV1"} {:concept-id "V1200000021-PROV1"}],
                          :services [{:data {:convert-format {:XYZ "ZYX"}, :allow-regridding "true"}, :concept-id "S1200000009-PROV1"}],
                          :tools [{:concept-id "TL1200000010-PROV1"}]
                          :data-quality-summaries [{:concept-id "DQS1200000012-PROV1" :data {:XYZ "ZYX"}, :revision-id 1}],
                          :order-options [{:concept-id "OO1200000014-PROV1"} {:concept-id "OO1200000015-PROV1"}]}
         expected-list {:variables ["V1200000019-PROV1" "V1200000021-PROV1"] ,
                        :services ["S1200000009-PROV1"] ,
                        :tools ["TL1200000010-PROV1"] ,
                        :data-quality-summaries ["DQS1200000012-PROV1"],
                        :order-options ["OO1200000014-PROV1" "OO1200000015-PROV1"]}
         tool-associations {:collections [{:concept-id "C1200000007-PROV1" 
                                           :revision-id 1, :data {:convert-format {:XYZ "ZYX"}, :allow-regridding "true"}}],
                            :data-quality-summaries [{:concept-id "DQS1200000012-PROV1", :data {:XYZ "ZYX"}, :revision-id 1}]}
         tool-expected-detail {:collections [{:concept-id "C1200000007-PROV1" :revision-id 1, :data {:convert-format {:XYZ "ZYX"}, :allow-regridding "true"}}]
                               :data-quality-summaries [{:concept-id "DQS1200000012-PROV1" :data {:XYZ "ZYX"}, :revision-id 1}]}]
    (testing "Test building the concept id list from the pass in associations."
      (are3 [expected assoc concept-type]
            (is (= expected
                   (rs-util/build-association-concept-id-list assoc concept-type)))

            "Tests a concept list just gets returned."
            expected-list
            associations
            :collection

            "Tests an empty map"
            nil
            {}
            :collection))

    (testing "Testing building the detailed associations."
      (are3 [expected assoc concept-type]
            (is (= expected 
                   (rs-util/build-association-details assoc concept-type)))

            "Tests that an association list gets converted to association details."
            expected-detail
            associations
            :collection

            "Tests that an association list gets converted to association details for tools."
            tool-expected-detail
            tool-associations
            :tool

            "Tests a nil list"
            nil
            {}
            :collection))))
