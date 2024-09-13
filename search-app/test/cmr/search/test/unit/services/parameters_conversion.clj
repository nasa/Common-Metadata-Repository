(ns cmr.search.test.unit.services.parameters-conversion
  (:require 
   [clojure.test :refer [deftest is testing]]
   [cmr.common.util :as util :refer [are3]]
   [cmr.search.services.parameters.conversion :as param-conv]))

;; Test for converting tag parameters
(deftest tag-parameter->query-condition-test
  (testing "Testing the conversion of tag parameters to query condition."
    (are3 
     [expected-condition param value pattern?]
     (let [actual (util/dissoc-multiple
                   (param-conv/tag-param->condition param value pattern?)
                   [:_type])
           expected (util/dissoc-multiple expected-condition [:_type])]
       (is (= expected actual)))
     
     "Testing tag-key parameter"
     #cmr.common.services.search.query_model.NestedCondition
     {:path :tags 
      :condition #cmr.common.services.search.query_model.StringCondition
      {:field :tags.tag-key
       :value "some_value"
       :case-sensitive? false
       :pattern? "some_pattern"}}
     :tag-key
     "some_value"
     "some_pattern"
     
     "Testing tag-originator-id parameter"
     #cmr.common.services.search.query_model.NestedCondition
      {:path :tags
       :condition #cmr.common.services.search.query_model.StringCondition
                   {:field :tags.originator-id
                    :value "some_value"
                    :case-sensitive? false
                    :pattern? "some_pattern"}}
     :tag-originator-id
     "some_value"
     "some_pattern"
     
     "Testing tag-data parameter"
     #cmr.common.services.search.query_model.ConditionGroup
     {:operation :and
      :conditions (#cmr.common.services.search.query_model.NestedCondition
                   {:path :tags
                    :condition #cmr.common.services.search.query_model.ConditionGroup
                               {:operation :and
                                :conditions (#cmr.common.services.search.query_model.StringCondition
                                             {:field :tags.tag-key
                                              :value "tag-key"
                                              :case-sensitive? false
                                              :pattern? "some_pattern"} 
                                             #cmr.common.services.search.query_model.StringCondition
                                             {:field :tags.tag-value
                                              :value "some_key"
                                              :case-sensitive? false
                                              :pattern? "some_pattern"})}}
                   #cmr.common.services.search.query_model.NestedCondition
                   {:path :tags
                    :condition #cmr.common.services.search.query_model.ConditionGroup
                               {:operation :and
                                :conditions (#cmr.common.services.search.query_model.StringCondition
                                             {:field :tags.tag-key
                                              :value "tag-value"
                                              :case-sensitive? false
                                              :pattern? "some_pattern"} 
                                             #cmr.common.services.search.query_model.StringCondition
                                             {:field :tags.tag-value
                                              :value "some_value"
                                              :case-sensitive? false
                                              :pattern? "some_pattern"})}})}
     :tag-data
     {:tag-key "some_key"
      :tag-value "some_value"}
     "some_pattern")))
