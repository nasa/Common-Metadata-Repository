(ns cmr.search.test.api.keyword
  "Tests to verify functionality in cmr.search.api.keyword namespace."
  (:require [clojure.test :refer :all]
            [cmr.search.api.keyword :as k]
            [cmr.common.util :as util]))

(deftest parse-hierarchical-keywords
  (util/are2
    [keyword-hierarchy keywords expected-hierarchy]
    (= expected-hierarchy
       (#'cmr.search.api.keyword/parse-hierarchical-keywords keyword-hierarchy keywords))

    ;; TODO - Figure out if I want to handle this case
    ; "One key"
    ; [:a :b :c :d] [{:b "B"}] {:b [{:value "B"}]}

    ; "No matching keywords"
    ; [:a :b] nil {}

    "Two keys"
    [:a :b] [{:a "A" :b "B"}] {:a [{:value "A"
                                    :subfields ["b"]
                                    :b [{:value "B"}]}]}

    "Missing one key"
    [:a :b :c] [{:a "A" :c "C"}] {:a [{:value "A"
                                       :subfields ["c"]
                                       :c [{:value "C"}]}]}

    "Missing several keys in a row"
    [:a :b :c :d :e :f :g :h] [{:a "A" :d "D" :h "H"}] {:a [{:value "A"
                                                             :subfields ["d"]
                                                             :d [{:value "D"
                                                                  :subfields ["h"]
                                                                  :h [{:value "H"}]}]}]}

    "No missing keys with multiple keywords"
    [:a :b :c]
    [{:a "A1" :b "B1" :c "C1"}
     {:a "A1" :b "B1" :c "C2"}
     {:a "A2" :b "B2" :c "C3"}]
    {:a [{:value "A1"
          :subfields ["b"]
          :b [{:value "B1"
               :subfields ["c"]
               :c [{:value "C1"}
                   {:value "C2"}]}]}
         {:value "A2",
          :subfields ["b"]
          :b [{:value "B2"
               :subfields ["c"]
               :c [{:value "C3"}]}]}]}

    "Multiple subfields"
    [:a :b :c]
    [{:a "A1" :b "B1"}
     {:a "A1" :c "C1"}]
    {:a [{:value "A1"
          :subfields ["b" "c"]
          :b [{:value "B1"}]
          :c [{:value "C1"}]}]}

    ; "UUID test"



    ))