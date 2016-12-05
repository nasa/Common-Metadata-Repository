(ns cmr.ingest.test.services.humanizer-alias-cache
  "This tests some of the more complicated functions of cmr.ingest.services.humanizer_alias_cache"
  (:require 
    [clojure.test :refer :all]
    [cmr.ingest.services.humanizer-alias-cache :as humanizer-alias-cache]))

(deftest retrieve-humanizer-platform-alias-map-test
  (is (= {"TERRA" ["AM-1" "am-1"] "FOO" ["old-foo1" "old-foo2"]} 
         (#'humanizer-alias-cache/retrieve-humanizer-platform-alias-map
             [{:type "trim_whitespace", :field "platform", :order -100}
              {:type "alias", :field "platform", :source_value "AM-1", :replacement_value "Terra", :reportable true, :order 0}  
              {:type "alias", :field "platform", :source_value "am-1", :replacement_value "Terra", :reportable true, :order 0}  
              {:type "alias", :field "platform", :source_value "old-foo1", :replacement_value "Foo", :reportable true, :order 0}  
              {:type "alias", :field "platform", :source_value "old-foo2", :replacement_value "Foo", :reportable true, :order 0}]))))  


