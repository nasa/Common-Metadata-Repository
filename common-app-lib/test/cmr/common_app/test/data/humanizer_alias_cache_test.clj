(ns cmr.common-app.test.data.humanizer-alias-cache-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [cmr.common-app.data.humanizer-alias-cache :as humanizer-alias-cache])
  (:import (clojure.lang ExceptionInfo)))

(def create-context
  "Creates a testing concept with the KMS caches."
  {:system {:caches {humanizer-alias-cache/humanizer-alias-cache-key (humanizer-alias-cache/create-cache-client)}}})

(def create-context-broken
  "Creates a testing concept with the KMS caches."
  (-> create-context
      (update-in
       [:system :caches :humanizer-alias-cache-by-field :read-connection :spec :host]
       (constantly "example.gov"))))

(deftest create-humanizer-alias-map-test
  (is (= {"platform" {"TERRA" ["AM-1" "am-1"] "FOO" ["old-foo1" "old-foo2"]}
          "tiling_system_name" {"TILE" ["tile-1" "tile-2"]}
          "instrument" {"INSTRUMENT" ["instr-1" "instr-2" "instr-3"]}}
         (#'humanizer-alias-cache/create-humanizer-alias-map
           [{:type "trim_whitespace", :field "platform", :order -100}
            {:type "alias", :field "platform", :source_value "AM-1", :replacement_value "Terra", :reportable true, :order 0}
            {:type "alias", :field "platform", :source_value "am-1", :replacement_value "Terra", :reportable true, :order 0}
            {:type "alias", :field "platform", :source_value "old-foo1", :replacement_value "Foo", :reportable true, :order 0}
            {:type "alias", :field "platform", :source_value "old-foo2", :replacement_value "Foo", :reportable true, :order 0}
            {:type "alias", :field "tiling_system_name", :source_value "tile-1", :replacement_value "Tile", :reportable true, :order 0}
            {:type "alias", :field "tiling_system_name", :source_value "tile-2", :replacement_value "Tile", :reportable true, :order 0}
            {:type "alias", :field "instrument", :source_value "instr-1", :replacement_value "Instrument", :reportable true, :order 0}
            {:type "alias", :field "instrument", :source_value "instr-2", :replacement_value "Instrument", :reportable true, :order 0}
            {:type "alias", :field "instrument", :source_value "instr-3", :replacement_value "inStruMent", :reportable true, :order 0}]))))

(deftest get-non-humanized-source-to-aliases-map-test
  (testing "cache connection error"
    (is (nil? (humanizer-alias-cache/get-non-humanized-source-to-aliases-map
               create-context
               "platform")))))
