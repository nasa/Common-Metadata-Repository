(ns cmr.search.test.results-handlers.results-handler-util
  (:require [clojure.set :as sj]
            [clojure.walk :as walk]
            [camel-snake-kebab.core :as csk]
            [clojure.test :refer :all]
            [cmr.common.util :as util :refer [are3]]
            [cmr.search.results-handlers.results-handler-util :as rs-util]))

(defn postwalk-associations
  "Replace the keys in the nested data structure"
  [associations]
  (->> associations
    (walk/postwalk-replace {:concept-id :concept_id})
    (walk/postwalk-replace {:revision-id :revision_id})))


(deftest convert-associations-test
   (let [associations {:variables [{:concept-id "V1200000019-PROV1"} {:concept-id "V1200000021-PROV1"}]
                       :services [{:concept-id "S1200000009-PROV1" :data {:convert-format {:XYZ "ZYX"}, :allow-regridding "true"}}]
                       :tools [{:concept-id "TL1200000010-PROV1"}]
                       :data-quality-summaries [{:concept-id "DQS1200000012-PROV1", :data {:XYZ "ZYX"}, :revision-id 1}],
                       :order-options [{:concept-id "OO1200000014-PROV1"} {:concept-id "OO1200000015-PROV1"}]}

         expected-detail {:variables [{:concept_id "V1200000019-PROV1"} {:concept_id "V1200000021-PROV1"}],
                          :services [{:data {:convert-format {:XYZ "ZYX"}, :allow-regridding "true"}, :concept_id "S1200000009-PROV1"}],
                          :tools [{:concept_id "TL1200000010-PROV1"}]
                          :data-quality-summaries [{:concept_id "DQS1200000012-PROV1" :data {:XYZ "ZYX"}, :revision_id 1}],
                          :order-options [{:concept_id "OO1200000014-PROV1"} {:concept_id "OO1200000015-PROV1"}]}
         expected-list {:variables ["V1200000019-PROV1" "V1200000021-PROV1"] ,
                        :services ["S1200000009-PROV1"] ,
                        :tools ["TL1200000010-PROV1"] ,
                        :data-quality-summaries ["DQS1200000012-PROV1"],
                        :order-options ["OO1200000014-PROV1" "OO1200000015-PROV1"]}
         tool-associations {:collections [{:concept-id "C1200000007-PROV1"
                                           :revision-id 1, :data {:convert-format {:XYZ "ZYX"}, :allow-regridding "true"}}],
                            :data-quality-summaries [{:concept-id "DQS1200000012-PROV1", :data {:XYZ "ZYX"}, :revision-id 1}]}
         tool-expected-detail {:collections [{:concept_id "C1200000007-PROV1" :revision_id 1, :data {:convert-format {:XYZ "ZYX"}, :allow-regridding "true"}}]
                               :data-quality-summaries [{:concept_id "DQS1200000012-PROV1" :data {:XYZ "ZYX"}, :revision_id 1}]}]
    (testing "Test building the concept-id list from the pass in associations."
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
                   (rs-util/build-association-details (rs-util/replace-snake-keys assoc) concept-type)))

            "Tests that an association list gets converted to association details."
            expected-detail
            associations
            :collection
            ;; TODO review this comment
            "Tests that an association list gets converted to association details for a particular concept in this case tools."
            tool-expected-detail
            tool-associations
            :tool

            "Tests a nil list"
            nil
            {}
            :collection))))

(comment "Useful for debugging nested association DS"
  ((def assoc {:variables [{:concept-id "V1200000019-PROV1"} {:concept-id "V1200000021-PROV1"}]
                      :services [{:concept-id "S1200000009-PROV1" :data {:convert-format {:XYZ "ZYX"}, :allow-regridding "true"}}]
                      :tools [{:concept-id "TL1200000010-PROV1"}]
                      :data-quality-summaries [{:concept-id "DQS1200000012-PROV1", :data {:XYZ "ZYX"}, :revision-id 1}],
                      :order-options [{:concept-id "OO1200000014-PROV1"} {:concept-id "OO1200000015-PROV1"}]})
    def assocVals (vals {:variables [{:concept-id "V1200000019-PROV1"} {:concept-id "V1200000021-PROV1"}]
                      :services [{:concept-id "S1200000009-PROV1" :data {:convert-format {:XYZ "ZYX"}, :allow-regridding "true"}}]
                      :tools [{:concept-id "TL1200000010-PROV1"}]
                      :data-quality-summaries [{:concept-id "DQS1200000012-PROV1", :data {:XYZ "ZYX"}, :revision-id 1}],
                      :order-options [{:concept-id "OO1200000014-PROV1"} {:concept-id "OO1200000015-PROV1"}]})))
; ;   (defn postwalk-mapentry
; ;       [smap nmap form]
; ;       (walk/postwalk (fn [x] (if (= smap x) nmap x)) form))
; ;
; ;
; ;
; ;   (defn rename->vectors
; ;     [vectors]
; ;     (mapv #(sj/rename-keys % {:concept-id :concept_id :revision-id :revision_id}) vector)
; ;     )
; ;
; ; (defn postwalk-mapentry
; ;     [smap nmap form]
; ;     (walk/postwalk (fn [x] (if (= smap x) nmap x)) form))
;
; (defn postwalk-associations
;   "Replace the keys in the nested data structure"
;   [associations]
;   (->> associations
;     (walk/postwalk-replace {:concept-id :concept_id})
;     (walk/postwalk-replace {:revision-id :revision_id})))
;
; (defn postwalk-replace-keys
;   "Replace the old keys in the nested data structure with the new ones"
;   [data-input old-key new-key]
;     (walk/postwalk-replace {old-key new-key} data-input))
;
;
;
; ;; input the association and then output the modified association
; (defn manipulateConcept-id
; "Take concept-id make it concept_id"
; [associations]
;   (for [x (vals associations)]
;     (mapv #(sj/rename-keys % {:concept-id :concept_id :revision-id :revision_id}) x)))
