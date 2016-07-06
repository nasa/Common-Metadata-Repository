(ns cmr.indexer.test.data.concepts.humanizer
  "Tests for humanizer transforms"
  (:require [clojure.test :refer :all]
            [cmr.indexer.data.concepts.humanizer :as humanizer]))

(defn- humanizes
  [humanizers source expected]
  (is (= expected
         (humanizer/umm-collection->umm-collection+humanizers source humanizers))))

(deftest to-human
  (testing "trim_whitespace"
    (are [a b] (= b (humanizer/to-human {:type "trim_whitespace"} a))
      "Test" "Test"
      "Test/test" "Test/test"
      "Test\ntest" "Test test"
      "Test  test" "Test test"
      "Test test  " "Test test"
      "   \t\r\nTest  \t\r\ntest   \t\r\n" "Test test"))

  (testing "capitalize"
    (are [a b] (= b (humanizer/to-human {:type "capitalize"} a))
      "TEST" "Test"
      "TestTest" "Testtest"
      "Test Test" "Test Test"
      "TEST-TEST" "Test-Test"
      "TEST/TEST" "Test/Test"
      "/TEST/TEST/" "/Test/Test/"))

  (testing "alias"
    (are [a b] (= b (humanizer/to-human {:type "alias" :replacement_value b} a))
      "A" "B"))

  (testing "ignore"
    (are [a b] (= b (humanizer/to-human {:type "ignore"} a))
      "A" nil)))

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
               {:product {:processing-level-id "level-A"}
                :platforms [{:short-name "plat-A"
                             :instruments [{:short-name "inst-A"}
                                           {:short-name "inst-B"}
                                           {:short-name "inst-C"}]}
                            {:short-name "plat-B"}]
                :projects [{:short-name "proj-A"}
                           {:short-name "proj-B"}]
                :science-keywords [{:category "sk-A"
                                    :topic "sk-B"
                                    :term "sk-C"
                                    :variable-level-1 "sk-D"
                                    :variable-level-2 "sk-E"
                                    :variable-level-3 "sk-F"
                                    :detailed-variable "sk-G"}]
                :organizations [{:org-name "org-A"}
                                {:org-name "org-B"}]}

               {:product {:processing-level-id "level-A"
                          :cmr.humanized/processing-level-id "level-A"}
                :platforms [{:short-name "plat-A"
                             :cmr.humanized/short-name "plat-A"
                             :instruments [{:short-name "inst-A"
                                            :cmr.humanized/short-name "inst-A"}
                                           {:short-name "inst-B"
                                            :cmr.humanized/short-name "inst-B"}
                                           {:short-name "inst-C"
                                            :cmr.humanized/short-name "inst-C"}]}
                            {:short-name "plat-B"
                             :cmr.humanized/short-name "plat-B"}]
                :projects [{:short-name "proj-A"
                            :cmr.humanized/short-name "proj-A"}
                           {:short-name "proj-B"
                            :cmr.humanized/short-name "proj-B"}]
                :science-keywords [{:category "sk-A"
                                    :cmr.humanized/category "sk-A"
                                    :topic "sk-B"
                                    :cmr.humanized/topic "sk-B"
                                    :term "sk-C"
                                    :cmr.humanized/term "sk-C"
                                    :variable-level-1 "sk-D"
                                    :cmr.humanized/variable-level-1 "sk-D"
                                    :variable-level-2 "sk-E"
                                    :cmr.humanized/variable-level-2 "sk-E"
                                    :variable-level-3 "sk-F"
                                    :cmr.humanized/variable-level-3 "sk-F"
                                    :detailed-variable "sk-G"
                                    :cmr.humanized/detailed-variable "sk-G"}]
                :organizations [{:org-name "org-A"
                                 :cmr.humanized/org-name "org-A"},
                                {:org-name "org-B"
                                 :cmr.humanized/org-name "org-B"}]}))

  (testing "humanize with changes"
    (humanizes [{:type "alias" :field "platform" :replacement_value "plat-human"}
                {:type "alias" :field "instrument" :replacement_value "inst-human"}
                {:type "alias" :field "processing_level" :replacement_value "level-human"}
                {:type "alias" :field "project" :replacement_value "proj-human"}
                {:type "alias" :field "science_keyword" :replacement_value "sk-human"}
                {:type "alias" :field "organization" :replacement_value "org-human"}]

               {:product {:processing-level-id "level-A"}
                :platforms [{:short-name "plat-A"
                             :instruments [{:short-name "inst-A"}
                                           {:short-name "inst-B"}
                                           {:short-name "inst-C"}]}
                            {:short-name "plat-B"}]
                :projects [{:short-name "proj-A"}
                           {:short-name "proj-B"}]
                :science-keywords [{:category "sk-A"
                                    :topic "sk-B"
                                    :term "sk-C"
                                    :variable-level-1 "sk-D"
                                    :variable-level-2 "sk-E"
                                    :variable-level-3 "sk-F"
                                    :detailed-variable "sk-G"}]
                :organizations [{:org-name "org-A"}
                                {:org-name "org-B"}]}

               {:product {:processing-level-id "level-A"
                          :cmr.humanized/processing-level-id "level-human"}
                :platforms [{:short-name "plat-A"
                             :cmr.humanized/short-name "plat-human"
                             :instruments [{:short-name "inst-A"
                                            :cmr.humanized/short-name "inst-human"}
                                           {:short-name "inst-B"
                                            :cmr.humanized/short-name "inst-human"}
                                           {:short-name "inst-C"
                                            :cmr.humanized/short-name "inst-human"}]}
                            {:short-name "plat-B"
                             :cmr.humanized/short-name "plat-human"}]
                :projects [{:short-name "proj-A"
                            :cmr.humanized/short-name "proj-human"}
                           {:short-name "proj-B"
                            :cmr.humanized/short-name "proj-human"}]
                :science-keywords [{:category "sk-A"
                                    :cmr.humanized/category "sk-human"
                                    :topic "sk-B"
                                    :cmr.humanized/topic "sk-human"
                                    :term "sk-C"
                                    :cmr.humanized/term "sk-human"
                                    :variable-level-1 "sk-D"
                                    :cmr.humanized/variable-level-1 "sk-human"
                                    :variable-level-2 "sk-E"
                                    :cmr.humanized/variable-level-2 "sk-human"
                                    :variable-level-3 "sk-F"
                                    :cmr.humanized/variable-level-3 "sk-human"
                                    :detailed-variable "sk-G"
                                    :cmr.humanized/detailed-variable "sk-human"}]
                :organizations [{:org-name "org-A"
                                 :cmr.humanized/org-name "org-human"},
                                {:org-name "org-B"
                                 :cmr.humanized/org-name "org-human"}]}))

  (testing "humanize with source value selection"
    (humanizes [{:type "alias" :field "platform" :source_value "plat-X" :replacement_value "plat-Y"}
                {:type "alias" :field "instrument" :source_value "inst-X" :replacement_value "inst-Y"}
                {:type "alias" :field "processing_level" :source_value "level-X" :replacement_value "level-Y"}
                {:type "alias" :field "project" :source_value "proj-X" :replacement_value "proj-Y"}
                {:type "alias" :field "science_keyword" :source_value "sk-X" :replacement_value "sk-Y"}
                {:type "alias" :field "organization" :source_value "org-X" :replacement_value "org-Y"}]

               {:product {:processing-level-id "level-X"}
                :platforms [{:short-name "plat-A"
                             :instruments [{:short-name "inst-A"}
                                           {:short-name "inst-X"}
                                           {:short-name "inst-C"}]}
                            {:short-name "plat-X"}]
                :projects [{:short-name "proj-X"}
                           {:short-name "proj-B"}]
                :science-keywords [{:category "sk-X"
                                    :topic "sk-B"
                                    :term "sk-X"
                                    :variable-level-1 "sk-D"
                                    :variable-level-2 "sk-X"
                                    :variable-level-3 "sk-F"
                                    :detailed-variable "sk-X"}]
                :organizations [{:org-name "org-X"}
                                {:org-name "org-X"}]}

               {:product {:processing-level-id "level-X"
                          :cmr.humanized/processing-level-id "level-Y"}
                :platforms [{:short-name "plat-A"
                             :cmr.humanized/short-name "plat-A"
                             :instruments [{:short-name "inst-A"
                                            :cmr.humanized/short-name "inst-A"}
                                           {:short-name "inst-X"
                                            :cmr.humanized/short-name "inst-Y"}
                                           {:short-name "inst-C"
                                            :cmr.humanized/short-name "inst-C"}]}
                            {:short-name "plat-X"
                             :cmr.humanized/short-name "plat-Y"}]
                :projects [{:short-name "proj-X"
                            :cmr.humanized/short-name "proj-Y"}
                           {:short-name "proj-B"
                            :cmr.humanized/short-name "proj-B"}]
                :science-keywords [{:category "sk-X"
                                    :cmr.humanized/category "sk-Y"
                                    :topic "sk-B"
                                    :cmr.humanized/topic "sk-B"
                                    :term "sk-X"
                                    :cmr.humanized/term "sk-Y"
                                    :variable-level-1 "sk-D"
                                    :cmr.humanized/variable-level-1 "sk-D"
                                    :variable-level-2 "sk-X"
                                    :cmr.humanized/variable-level-2 "sk-Y"
                                    :variable-level-3 "sk-F"
                                    :cmr.humanized/variable-level-3 "sk-F"
                                    :detailed-variable "sk-X"
                                    :cmr.humanized/detailed-variable "sk-Y"}]
                :organizations [{:org-name "org-X"
                                 :cmr.humanized/org-name "org-Y"}
                                {:org-name "org-X"
                                 :cmr.humanized/org-name "org-Y"}]}))

  (testing "humanize from json definitions"
    ;; Small sanity check to ensure humanizers are pulled from the resource json by default.
    ;; Assumes that trimming whitespace is done in the humanizer
    (is (=
         {:organizations [{:org-name " TEST\t" :cmr.humanized/org-name "TEST"}]}
         (humanizer/umm-collection->umm-collection+humanizers {:organizations [{:org-name " TEST\t"}]}))))

  (testing "humanize with priority order"
    (humanizes [{:type "alias" :field "organization" :source_value "A" :replacement_value "X" :priority 0}
                {:type "alias" :field "organization" :source_value "X" :replacement_value "Y" :priority 1}]

               {:organizations [{:org-name "A"}]}

               {:organizations [{:org-name "A" :cmr.humanized/org-name "Y"}]})

    (humanizes [{:type "alias" :field "organization" :source_value "A" :replacement_value "X" :priority 1}
                {:type "alias" :field "organization" :source_value "X" :replacement_value "Y" :priority 0}]

               {:organizations [{:org-name "A"}]}

               {:organizations [{:org-name "A" :cmr.humanized/org-name "X"}]})))
