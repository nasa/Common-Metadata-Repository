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
                          :cmr.humanized/processing-level-id {:value "level-A" :priority 0}}
                :platforms [{:short-name "plat-A"
                             :cmr.humanized/short-name {:value "plat-A" :priority 0}
                             :instruments [{:short-name "inst-A"
                                            :cmr.humanized/short-name {:value "inst-A" :priority 0}}
                                           {:short-name "inst-B"
                                            :cmr.humanized/short-name {:value "inst-B" :priority 0}}
                                           {:short-name "inst-C"
                                            :cmr.humanized/short-name {:value "inst-C" :priority 0}}]}
                            {:short-name "plat-B"
                             :cmr.humanized/short-name {:value "plat-B" :priority 0}}]
                :projects [{:short-name "proj-A"
                            :cmr.humanized/short-name {:value "proj-A" :priority 0}}
                           {:short-name "proj-B"
                            :cmr.humanized/short-name {:value "proj-B" :priority 0}}]
                :science-keywords [{:category "sk-A"
                                    :cmr.humanized/category {:value "sk-A" :priority 0}
                                    :topic "sk-B"
                                    :cmr.humanized/topic {:value "sk-B" :priority 0}
                                    :term "sk-C"
                                    :cmr.humanized/term {:value "sk-C" :priority 0}
                                    :variable-level-1 "sk-D"
                                    :cmr.humanized/variable-level-1 {:value "sk-D" :priority 0}
                                    :variable-level-2 "sk-E"
                                    :cmr.humanized/variable-level-2 {:value "sk-E" :priority 0}
                                    :variable-level-3 "sk-F"
                                    :cmr.humanized/variable-level-3 {:value "sk-F" :priority 0}
                                    :detailed-variable "sk-G"
                                    :cmr.humanized/detailed-variable {:value "sk-G" :priority 0}}]
                :organizations [{:org-name "org-A"
                                 :cmr.humanized/org-name {:value "org-A" :priority 0}},
                                {:org-name "org-B"
                                 :cmr.humanized/org-name {:value "org-B" :priority 0}}]}))

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
                          :cmr.humanized/processing-level-id {:value "level-human" :priority 0}}
                :platforms [{:short-name "plat-A"
                             :cmr.humanized/short-name {:value "plat-human" :priority 0}
                             :instruments [{:short-name "inst-A"
                                            :cmr.humanized/short-name {:value "inst-human" :priority 0}}
                                           {:short-name "inst-B"
                                            :cmr.humanized/short-name {:value "inst-human" :priority 0}}
                                           {:short-name "inst-C"
                                            :cmr.humanized/short-name {:value "inst-human" :priority 0}}]}
                            {:short-name "plat-B"
                             :cmr.humanized/short-name {:value "plat-human" :priority 0}}]
                :projects [{:short-name "proj-A"
                            :cmr.humanized/short-name {:value "proj-human" :priority 0}}
                           {:short-name "proj-B"
                            :cmr.humanized/short-name {:value "proj-human" :priority 0}}]
                :science-keywords [{:category "sk-A"
                                    :cmr.humanized/category {:value "sk-human" :priority 0}
                                    :topic "sk-B"
                                    :cmr.humanized/topic {:value "sk-human" :priority 0}
                                    :term "sk-C"
                                    :cmr.humanized/term {:value "sk-human" :priority 0}
                                    :variable-level-1 "sk-D"
                                    :cmr.humanized/variable-level-1 {:value "sk-human" :priority 0}
                                    :variable-level-2 "sk-E"
                                    :cmr.humanized/variable-level-2 {:value "sk-human" :priority 0}
                                    :variable-level-3 "sk-F"
                                    :cmr.humanized/variable-level-3 {:value "sk-human" :priority 0}
                                    :detailed-variable "sk-G"
                                    :cmr.humanized/detailed-variable {:value "sk-human" :priority 0}}]
                :organizations [{:org-name "org-A"
                                 :cmr.humanized/org-name {:value "org-human" :priority 0}},
                                {:org-name "org-B"
                                 :cmr.humanized/org-name {:value "org-human" :priority 0}}]}))

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
                          :cmr.humanized/processing-level-id {:value "level-Y" :priority 0}}
                :platforms [{:short-name "plat-A"
                             :cmr.humanized/short-name {:value "plat-A" :priority 0}
                             :instruments [{:short-name "inst-A"
                                            :cmr.humanized/short-name {:value "inst-A" :priority 0}}
                                           {:short-name "inst-X"
                                            :cmr.humanized/short-name {:value "inst-Y" :priority 0}}
                                           {:short-name "inst-C"
                                            :cmr.humanized/short-name {:value "inst-C" :priority 0}}]}
                            {:short-name "plat-X"
                             :cmr.humanized/short-name {:value "plat-Y" :priority 0}}]
                :projects [{:short-name "proj-X"
                            :cmr.humanized/short-name {:value "proj-Y" :priority 0}}
                           {:short-name "proj-B"
                            :cmr.humanized/short-name {:value "proj-B" :priority 0}}]
                :science-keywords [{:category "sk-X"
                                    :cmr.humanized/category {:value "sk-Y" :priority 0}
                                    :topic "sk-B"
                                    :cmr.humanized/topic {:value "sk-B" :priority 0}
                                    :term "sk-X"
                                    :cmr.humanized/term {:value "sk-Y" :priority 0}
                                    :variable-level-1 "sk-D"
                                    :cmr.humanized/variable-level-1 {:value "sk-D" :priority 0}
                                    :variable-level-2 "sk-X"
                                    :cmr.humanized/variable-level-2 {:value "sk-Y" :priority 0}
                                    :variable-level-3 "sk-F"
                                    :cmr.humanized/variable-level-3 {:value "sk-F" :priority 0}
                                    :detailed-variable "sk-X"
                                    :cmr.humanized/detailed-variable {:value "sk-Y" :priority 0}}]
                :organizations [{:org-name "org-X"
                                 :cmr.humanized/org-name {:value "org-Y" :priority 0}}
                                {:org-name "org-X"
                                 :cmr.humanized/org-name {:value "org-Y" :priority 0}}]}))

  (testing "humanize with sort order"
    (humanizes [{:type "alias" :field "organization" :source_value "A" :replacement_value "X" :order 0}
                {:type "alias" :field "organization" :source_value "X" :replacement_value "Y" :order 1}]

               {:organizations [{:org-name "A"}]}

               {:organizations [{:org-name "A" :cmr.humanized/org-name {:value "Y" :priority 0}}]})

    (humanizes [{:type "alias" :field "organization" :source_value "A" :replacement_value "X" :order 1}
                {:type "alias" :field "organization" :source_value "X" :replacement_value "Y" :order 0}]

               {:organizations [{:org-name "A"}]}

               {:organizations [{:org-name "A" :cmr.humanized/org-name {:value "X" :priority 0}}]})))
