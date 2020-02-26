(ns cmr.common-app.test.humanizer
  "Tests for humanizer transforms"
  (:require [clojure.test :refer :all]
            [cmr.common-app.humanizer :as humanizer]))

(defn- humanizes
  [humanizers source expected]
  (is (= expected
         (humanizer/umm-collection->umm-collection+humanizers source humanizers))))

(deftest to-human
  (testing "trim_whitespace"
    (are [a b] (= {:value b} (humanizer/to-human {:type "trim_whitespace"} {:value a}))
      "Test" "Test"
      "Test/test" "Test/test"
      "Test\ntest" "Test test"
      "Test  test" "Test test"
      "Test test  " "Test test"
      "   \t\r\nTest  \t\r\ntest   \t\r\n" "Test test"))

  (testing "capitalize"
    (are [a b] (= {:value b} (humanizer/to-human {:type "capitalize"} {:value a}))
      "TEST" "Test"
      "TestTest" "Testtest"
      "Test Test" "Test Test"
      "TEST-TEST" "Test-Test"
      "TEST/TEST" "Test/Test"
      "/TEST/TEST/" "/Test/Test/"))

  (testing "alias"
    (are [a b] (= {:value b} (humanizer/to-human {:type "alias" :replacement_value b} {:value a}))
      "A" "B"))

  (testing "ignore"
    (are [a b] (= b (humanizer/to-human {:type "ignore"} {:value a}))
      "A" nil))

  (testing "priority"
    (are [a b] (= {:priority b} (humanizer/to-human {:type "priority" :priority a} {:priority 0}))
      10 10)))

;; TODO add reportable tests. If a humanizer is reportable the output value should be reportable.

(deftest humanize-collection
  (testing "humanize missing values"
    (humanizes [{:type "capitalize" :field "platform"}
                {:type "capitalize" :field "instrument"}
                {:type "capitalize" :field "processing_level"}
                {:type "capitalize" :field "project"}
                {:type "capitalize" :field "science_keyword"}
                {:type "capitalize" :field "organization"}]
               {}
               {}))
  (testing "humanize no changes"
    (humanizes []
               {:ProcessingLevel {:Id "level-A"}
                :Platforms [{:ShortName "plat-A"
                             :Instruments [{:ShortName "inst-A"}
                                           {:ShortName "inst-B"}
                                           {:ShortName "inst-C"}]}
                            {:ShortName "plat-B"}]
                :Projects [{:ShortName "proj-A"}
                           {:ShortName "proj-B"}]
                :ScienceKeywords [{:Category "sk-A"
                                    :Topic "sk-B"
                                    :Term "sk-C"
                                    :VariableLevel1 "sk-D"
                                    :VariableLevel2 "sk-E"
                                    :VariableLevel3 "sk-F"
                                    :DetailedVariable "sk-G"}]
                :DataCenters [{:ShortName "org-A"}
                              {:ShortName "org-B"}]}

               {:ProcessingLevel {:Id "level-A"
                                  :cmr-humanized/Id {:value "level-A" :priority 0}}
                :Platforms [{:ShortName "plat-A"
                             :cmr-humanized/ShortName {:value "plat-A" :priority 0}
                             :Instruments [{:ShortName "inst-A"
                                            :cmr-humanized/ShortName {:value "inst-A" :priority 0}}
                                           {:ShortName "inst-B"
                                            :cmr-humanized/ShortName {:value "inst-B" :priority 0}}
                                           {:ShortName "inst-C"
                                            :cmr-humanized/ShortName {:value "inst-C" :priority 0}}]}
                            {:ShortName "plat-B"
                             :cmr-humanized/ShortName {:value "plat-B" :priority 0}}]
                :Projects [{:ShortName "proj-A"
                            :cmr-humanized/ShortName {:value "proj-A" :priority 0}}
                           {:ShortName "proj-B"
                            :cmr-humanized/ShortName {:value "proj-B" :priority 0}}]
                :ScienceKeywords [{:Category "sk-A"
                                    :cmr-humanized/Category {:value "sk-A" :priority 0}
                                    :Topic "sk-B"
                                    :cmr-humanized/Topic {:value "sk-B" :priority 0}
                                    :Term "sk-C"
                                    :cmr-humanized/Term {:value "sk-C" :priority 0}
                                    :VariableLevel1 "sk-D"
                                    :cmr-humanized/VariableLevel1 {:value "sk-D" :priority 0}
                                    :VariableLevel2 "sk-E"
                                    :cmr-humanized/VariableLevel2 {:value "sk-E" :priority 0}
                                    :VariableLevel3 "sk-F"
                                    :cmr-humanized/VariableLevel3 {:value "sk-F" :priority 0}
                                    :DetailedVariable "sk-G"
                                    :cmr-humanized/DetailedVariable {:value "sk-G" :priority 0}}]
                :DataCenters [{:ShortName "org-A"
                                 :cmr-humanized/ShortName {:value "org-A" :priority 0}},
                                {:ShortName "org-B"
                                 :cmr-humanized/ShortName {:value "org-B" :priority 0}}]}))

  (testing "humanize with changes"
    (humanizes [{:type "alias" :field "platform" :replacement_value "plat-human"}
                {:type "alias" :field "instrument" :replacement_value "inst-human"}
                {:type "alias" :field "processing_level" :replacement_value "level-human"}
                {:type "alias" :field "project" :replacement_value "proj-human"}
                {:type "alias" :field "science_keyword" :replacement_value "sk-human"}
                {:type "alias" :field "organization" :replacement_value "org-human"}
                {:type "alias" :field "tiling_system_name" :replacement_value "tiling-human"}]

               {:ProcessingLevel {:Id "level-A"}
                :Platforms [{:ShortName "plat-A"
                             :Instruments [{:ShortName "inst-A"}
                                           {:ShortName "inst-B"}
                                           {:ShortName "inst-C"}]}
                            {:ShortName "plat-B"}]
                :Projects [{:ShortName "proj-A"}
                           {:ShortName "proj-B"}]
                :ScienceKeywords [{:Category "sk-A"
                                    :Topic "sk-B"
                                    :Term "sk-C"
                                    :VariableLevel1 "sk-D"
                                    :VariableLevel2 "sk-E"
                                    :VariableLevel3 "sk-F"
                                    :DetailedVariable "sk-G"}]
                :DataCenters [{:ShortName "org-A"}
                              {:ShortName "org-B"}]
                :TilingIdentificationSystems [{:TilingIdentificationSystemName "tis-A"}
                                              {:TilingIdentificationSystemName "tis-B"}]}

               {:ProcessingLevel {:Id "level-A"
                                  :cmr-humanized/Id {:value "level-human" :priority 0}}
                :Platforms [{:ShortName "plat-A"
                             :cmr-humanized/ShortName {:value "plat-human" :priority 0}
                             :Instruments [{:ShortName "inst-A"
                                            :cmr-humanized/ShortName {:value "inst-human" :priority 0}}
                                           {:ShortName "inst-B"
                                            :cmr-humanized/ShortName {:value "inst-human" :priority 0}}
                                           {:ShortName "inst-C"
                                            :cmr-humanized/ShortName {:value "inst-human" :priority 0}}]}
                            {:ShortName "plat-B"
                             :cmr-humanized/ShortName {:value "plat-human" :priority 0}}]
                :Projects [{:ShortName "proj-A"
                            :cmr-humanized/ShortName {:value "proj-human" :priority 0}}
                           {:ShortName "proj-B"
                            :cmr-humanized/ShortName {:value "proj-human" :priority 0}}]
                :ScienceKeywords [{:Category "sk-A"
                                    :cmr-humanized/Category {:value "sk-human" :priority 0}
                                    :Topic "sk-B"
                                    :cmr-humanized/Topic {:value "sk-human" :priority 0}
                                    :Term "sk-C"
                                    :cmr-humanized/Term {:value "sk-human" :priority 0}
                                    :VariableLevel1 "sk-D"
                                    :cmr-humanized/VariableLevel1 {:value "sk-human" :priority 0}
                                    :VariableLevel2 "sk-E"
                                    :cmr-humanized/VariableLevel2 {:value "sk-human" :priority 0}
                                    :VariableLevel3 "sk-F"
                                    :cmr-humanized/VariableLevel3 {:value "sk-human" :priority 0}
                                    :DetailedVariable "sk-G"
                                    :cmr-humanized/DetailedVariable {:value "sk-human" :priority 0}}]
                :DataCenters [{:ShortName "org-A"
                               :cmr-humanized/ShortName {:value "org-human" :priority 0}},
                              {:ShortName "org-B"
                               :cmr-humanized/ShortName {:value "org-human" :priority 0}}]
                :TilingIdentificationSystems [{:TilingIdentificationSystemName "tis-A"
                                               :cmr-humanized/TilingIdentificationSystemName {:value "tiling-human" :priority 0}}
                                              {:TilingIdentificationSystemName "tis-B"
                                               :cmr-humanized/TilingIdentificationSystemName {:value "tiling-human" :priority 0}}]}))

  (testing "humanize with source value selection"
    (humanizes [{:type "alias" :field "platform" :source_value "plat-X" :replacement_value "plat-Y"}
                {:type "alias" :field "instrument" :source_value "inst-X" :replacement_value "inst-Y"}
                {:type "alias" :field "processing_level" :source_value "level-X" :replacement_value "level-Y"}
                {:type "alias" :field "project" :source_value "proj-X" :replacement_value "proj-Y"}
                {:type "alias" :field "science_keyword" :source_value "sk-X" :replacement_value "sk-Y"}
                {:type "alias" :field "organization" :source_value "org-X" :replacement_value "org-Y"}
                {:type "alias" :field "tiling_system_name" :source-value "tis-A" :replacement_value "tis-B"}]

               {:ProcessingLevel {:Id "level-X"}
                :Platforms [{:ShortName "plat-A"
                             :Instruments [{:ShortName "inst-A"}
                                           {:ShortName "inst-X"}
                                           {:ShortName "inst-C"}]}
                            {:ShortName "plat-X"}]
                :Projects [{:ShortName "proj-X"}
                           {:ShortName "proj-B"}]
                :ScienceKeywords [{:Category "sk-X"
                                    :Topic "sk-B"
                                    :Term "sk-X"
                                    :VariableLevel1 "sk-D"
                                    :VariableLevel2 "sk-X"
                                    :VariableLevel3 "sk-F"
                                    :DetailedVariable "sk-X"}]
                :DataCenters [{:ShortName "org-X"}
                              {:ShortName "org-X"}]
                :TilingIdentificationSystems [{:TilingIdentificationSystemName "tis-A"}]}

               {:ProcessingLevel {:Id "level-X"
                                  :cmr-humanized/Id {:value "level-Y" :priority 0}}
                :Platforms [{:ShortName "plat-A"
                             :cmr-humanized/ShortName {:value "plat-A" :priority 0}
                             :Instruments [{:ShortName "inst-A"
                                            :cmr-humanized/ShortName {:value "inst-A" :priority 0}}
                                           {:ShortName "inst-X"
                                            :cmr-humanized/ShortName {:value "inst-Y" :priority 0}}
                                           {:ShortName "inst-C"
                                            :cmr-humanized/ShortName {:value "inst-C" :priority 0}}]}
                            {:ShortName "plat-X"
                             :cmr-humanized/ShortName {:value "plat-Y" :priority 0}}]
                :Projects [{:ShortName "proj-X"
                            :cmr-humanized/ShortName {:value "proj-Y" :priority 0}}
                           {:ShortName "proj-B"
                            :cmr-humanized/ShortName {:value "proj-B" :priority 0}}]
                :ScienceKeywords [{:Category "sk-X"
                                    :cmr-humanized/Category {:value "sk-Y" :priority 0}
                                    :Topic "sk-B"
                                    :cmr-humanized/Topic {:value "sk-B" :priority 0}
                                    :Term "sk-X"
                                    :cmr-humanized/Term {:value "sk-Y" :priority 0}
                                    :VariableLevel1 "sk-D"
                                    :cmr-humanized/VariableLevel1 {:value "sk-D" :priority 0}
                                    :VariableLevel2 "sk-X"
                                    :cmr-humanized/VariableLevel2 {:value "sk-Y" :priority 0}
                                    :VariableLevel3 "sk-F"
                                    :cmr-humanized/VariableLevel3 {:value "sk-F" :priority 0}
                                    :DetailedVariable "sk-X"
                                    :cmr-humanized/DetailedVariable {:value "sk-Y" :priority 0}}]
                :DataCenters [{:ShortName "org-X"
                                 :cmr-humanized/ShortName {:value "org-Y" :priority 0}}
                              {:ShortName "org-X"
                                 :cmr-humanized/ShortName {:value "org-Y" :priority 0}}]
                :TilingIdentificationSystems [{:TilingIdentificationSystemName "tis-A"
                                               :cmr-humanized/TilingIdentificationSystemName {:value "tis-B" :priority 0}}]}))

  (testing "humanize with sort order"
    (humanizes [{:type "alias" :field "organization" :source_value "A" :replacement_value "X" :order 0}
                {:type "alias" :field "organization" :source_value "X" :replacement_value "Y" :order 1}]

               {:DataCenters [{:ShortName "A"}]}

               {:DataCenters [{:ShortName "A" :cmr-humanized/ShortName {:value "Y" :priority 0}}]})

    (humanizes [{:type "alias" :field "organization" :source_value "A" :replacement_value "X" :order 1}
                {:type "alias" :field "organization" :source_value "X" :replacement_value "Y" :order 0}]

               {:DataCenters [{:ShortName "A"}]}

               {:DataCenters [{:ShortName "A" :cmr-humanized/ShortName {:value "X" :priority 0}}]})))
