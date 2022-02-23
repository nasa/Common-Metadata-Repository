(ns cmr.indexer.test.services.autocomplete
  "Tests for index service"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.indexer.services.autocomplete :as autocomplete]))

(deftest anti-value-test
  "Tests the anti-value-suggestion function. When certain values exist in the values field in the
  autocomplete document, they need to be removed. This test tests the function that determines if
  a value fits that criteria."

  (are3 [doc result]
    (is (= result (autocomplete/anti-value-suggestion? doc)))

    "Testing that the word of not is not an anti-value."
    {:value "not"} false

    "Testing other types of keyword that contains the value of not is also not an anti-value."
    {:value "Nothofagus"} false

    "Testing other types of keyword that contains the value of not is also not an anti-value part 2."
    {:value "Notothenioids"} false

    "Testing that none is an anti value."
    {:value "none"} true

    "Testing that not applicable is an anti value."
    {:value "not applicable"} true

    "Testing that not provided is an anti value."
    {:value "not provided"} true

    "Testing that an empty string is an anti value."
    {:value ""} true

    "Testing that a string of spaces is an anti value."
    {:value "      "} true

    "Testing that carrage returns and tabs are anti values."
    {:value "\n\t"} true

    "Testing that nil is an anti value."
    {:value nil} true))

(def humanized-fields
  [{:permissions ["guest" "registered"]
    :granule-data-format-humanized [],
    :instrument-sn-humanized [{:value-lowercase "atm",
                               :value "ATM",
                               :priority 0}
                              {:value-lowercase "lvis",
                               :value "lVIs",
                               :priority 0}]
    :science-keywords-humanized [{:topic-lowercase "topic1",
                                  :category "Earth Science",
                                  :variable-level-2 "Level1-2",
                                  :category-lowercase "earth science",
                                  :detailed-variable "Detail1",
                                  :topic "Topic1",
                                  :variable-level-1-lowercase "level1-1",
                                  :term "Term1",
                                  :variable-level-2-lowercase "level1-2",
                                  :variable-level-3-lowercase "level1-3",
                                  :term-lowercase "term1",
                                  :variable-level-1 "Level1-1",
                                  :detailed-variable-lowercase "detail1",
                                  :variable-level-3 "Level1-3"}
                                 {:topic-lowercase "popular",
                                  :category "Earth Science",
                                  :variable-level-2 "Level2-2",
                                  :category-lowercase "earth science",
                                  :detailed-variable "Universal",
                                  :topic "Popular",
                                  :variable-level-1-lowercase "level2-1",
                                  :term "Extreme",
                                  :variable-level-2-lowercase "level2-2",
                                  :variable-level-3-lowercase nil,
                                  :term-lowercase "extreme",
                                  :variable-level-1 "Level2-1",
                                  :detailed-variable-lowercase "universal",
                                  :variable-level-3 nil}]
    :id "C1200000012-PROV1",
    :platforms2-humanized [{:category "Earth Observation Satellites",
                            :short-name "DIADEM-1D",
                            :uuid-lowercase "143a5181-7601-4cc7-96d1-2b1a04b08fa7",
                            :category-lowercase "earth observation satellites",
                            :long-name-lowercase nil,
                            :short-name-lowercase "diadem-1d",
                            :basis "Space-based Platforms",
                            :basis-lowercase "space-based platforms",
                            :sub-category "DIADEM",
                            :long-name nil,
                            :uuid "143a5181-7601-4cc7-96d1-2b1a04b08fa7",
                            :sub-category-lowercase "diadem"}
                           {:category "Earth Observation Satellites",
                            :short-name "DMSP 5B/F3",
                            :uuid-lowercase "7ed12e98-95b1-406c-a58a-f4bbfa405269",
                            :category-lowercase "earth observation satellites",
                            :long-name-lowercase "defense meteorological satellite program-f3",
                            :short-name-lowercase "dmsp 5b/f3",
                            :basis "Space-based Platforms",
                            :basis-lowercase "space-based platforms",
                            :sub-category "Defense Meteorological Satellite Program(DMSP)",
                            :long-name "Defense Meteorological Satellite Program-F3",
                            :uuid "7ed12e98-95b1-406c-a58a-f4bbfa405269",
                            :sub-category-lowercase "defense meteorological satellite program(dmsp)"}
                           {:category "Earth Observation Satellites",
                            :short-name "Terra",
                            :uuid-lowercase "80eca755-c564-4616-b910-a4c4387b7c54",
                            :category-lowercase "earth observation satellites",
                            :long-name-lowercase "earth observing system, terra (am-1)",
                            :short-name-lowercase "terra",
                            :basis "Space-based Platforms",
                            :basis-lowercase "space-based platforms",
                            :sub-category nil,
                            :long-name "Earth Observing System, Terra (AM-1)",
                            :uuid "80eca755-c564-4616-b910-a4c4387b7c54",
                            :sub-category-lowercase nil}]
    :processing-level-id-humanized [{:value-lowercase "pl1",
                                     :value "PL1",
                                     :priority 0}]
    :project-sn-humanized [{:value-lowercase "proj1",
                            :value "proj1",
                            :priority 0}
                           {:value-lowercase "proj2",
                            :value "PROJ2",
                            :priority 0}]
    :organization-humanized [{:value-lowercase "not provided",
                              :value "Not provided",
                              :priority 0}]}])

(def expected-suggestion-docs
  [{:_id 1295773924,
    :value "ATM",
    :fields "ATM",
    :type "instrument",
    :_index "1_autocomplete",
    :contains-public-collections true,
    :permitted-group-ids "registered"}
   {:_id -1158917897,
    :value "lVIs",
    :fields "lVIs",
    :type "instrument",
    :_index "1_autocomplete",
    :contains-public-collections true,
    :permitted-group-ids "registered"}
   {:_id -2001466745,
    :value "Detail1",
    :fields "Topic1:Term1:Level1-1:Level1-2:Level1-3:Detail1",
    :type "science_keywords",
    :_index "1_autocomplete",
    :contains-public-collections true,
    :permitted-group-ids "registered"}
   {:_id 736408225,
    :value "Universal",
    :fields "Popular:Extreme:Level2-1:Level2-2::Universal",
    :type "science_keywords",
    :_index "1_autocomplete",
    :contains-public-collections true,
    :permitted-group-ids "registered"}
   {:_id -1020268039,
    :value "DIADEM-1D",
    :fields "Space-based Platforms:Earth Observation Satellites:DIADEM:DIADEM-1D",
    :type "platforms",
    :_index "1_autocomplete",
    :contains-public-collections true,
    :permitted-group-ids "registered"}
   {:_id 525675246,
    :value "DMSP 5B/F3",
    :fields "Space-based Platforms:Earth Observation Satellites:Defense Meteorological Satellite Program(DMSP):DMSP 5B/F3",
    :type "platforms",
    :_index "1_autocomplete",
    :contains-public-collections true,
    :permitted-group-ids "registered"}
   {:_id 363618952,
    :value "Terra",
    :fields "Space-based Platforms:Earth Observation Satellites::Terra",
    :type "platforms",
    :_index "1_autocomplete",
    :contains-public-collections true,
    :permitted-group-ids "registered"}
   {:_id -1994632963,
    :value "PL1",
    :fields "PL1",
    :type "processing_level",
    :_index "1_autocomplete",
    :contains-public-collections true,
    :permitted-group-ids "registered"}
   {:_id 471728712,
    :value "proj1",
    :fields "proj1",
    :type "project",
    :_index "1_autocomplete",
    :contains-public-collections true,
    :permitted-group-ids "registered"}
   {:_id 57623960,
    :value "PROJ2",
    :fields "PROJ2",
    :type "project",
    :_index "1_autocomplete",
    :contains-public-collections true,
    :permitted-group-ids "registered"}])

(deftest get-suggestion-docs-test
  "Tests generating the suggestion or autocomplete documents from a list of humanized collections.
  This test exercises science keyword, platform, and other generic humanized elements that make up
  the autocomplete documents."

  (testing "Testing the autocomplete document creation."
     (is (= expected-suggestion-docs
            (->> humanized-fields
                 (map #(#'autocomplete/get-suggestion-docs "1_autocomplete" %))
                 flatten
                 (remove autocomplete/anti-value-suggestion?)
                 (map #(dissoc % :modified)))))))
