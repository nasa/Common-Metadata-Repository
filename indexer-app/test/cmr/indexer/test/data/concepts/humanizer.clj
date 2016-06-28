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
      "TEST/value" "TEST/value"
      "TEST  value" "TEST  value"
      "TEST value  " "TEST value"
      "   \t\r\nTEST value   \t\r\n" "TEST value"))

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
  (testing "humanize platform"
    (testing "trim_whitespace"
      (let [humanizers [{:type "trim_whitespace" :field "platform" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {}))
        (testing "one"
          (humanizes humanizers
                     {:platforms [{:short-name " TEST/value\t"}]}
                     {:platforms [{:short-name " TEST/value\t"
                                   :cmr.humanized/short-name "TEST/value"}]}))

        (testing "many"
          (humanizes humanizers
                     {:platforms [{:short-name " TEST/value1\t"},
                                  {:short-name " TEST/value2\t"}]}
                     {:platforms [{:short-name " TEST/value1\t"
                                   :cmr.humanized/short-name "TEST/value1"},
                                  {:short-name " TEST/value2\t"
                                   :cmr.humanized/short-name "TEST/value2"}]}))

        (testing "with source value"
          (humanizes (map #(assoc %1 :source_value " TEST/value1\t") humanizers)
                     {:platforms [{:short-name " TEST/value1\t"},
                                  {:short-name " TEST/value2\t"}]}
                     {:platforms [{:short-name " TEST/value1\t"
                                   :cmr.humanized/short-name "TEST/value1"},
                                  {:short-name " TEST/value2\t"
                                   :cmr.humanized/short-name " TEST/value2\t"}]}))))
    (testing "capitalize"
      (let [humanizers [{:type "capitalize" :field "platform" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {}))
        (testing "one"
          (humanizes humanizers
                     {:platforms [{:short-name "TEST/value"}]}
                     {:platforms [{:short-name "TEST/value"
                                   :cmr.humanized/short-name "Test/Value"}]}))

        (testing "many"
          (humanizes humanizers
                     {:platforms [{:short-name "TEST/value1"},
                                  {:short-name "TEST/value2"}]}
                     {:platforms [{:short-name "TEST/value1"
                                   :cmr.humanized/short-name "Test/Value1"},
                                  {:short-name "TEST/value2"
                                   :cmr.humanized/short-name "Test/Value2"}]}))

        (testing "with source value"
          (humanizes (map #(assoc %1 :source_value "TEST/value2") humanizers)
                     {:platforms [{:short-name "TEST/value1"},
                                  {:short-name "TEST/value2"}]}
                     {:platforms [{:short-name "TEST/value1"
                                   :cmr.humanized/short-name "TEST/value1"},
                                  {:short-name "TEST/value2"
                                   :cmr.humanized/short-name "Test/Value2"}]}))))
    (testing "alias"
      (let [humanizers [{:type "alias" :field "platform" :source_value "A" :replacement_value "X" :priority 0}
                        {:type "alias" :field "platform" :source_value "B" :replacement_value "Y" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {}))
        (testing "one"
          (humanizes humanizers
                     {:platforms [{:short-name "A"}]}
                     {:platforms [{:short-name "A"
                                   :cmr.humanized/short-name "X"}]}))

        (testing "many"
          (humanizes humanizers
                     {:platforms [{:short-name "A"},
                                  {:short-name "B"}]}
                     {:platforms [{:short-name "A"
                                   :cmr.humanized/short-name "X"},
                                  {:short-name "B"
                                   :cmr.humanized/short-name "Y"}]}))))
    (testing "ignore"
      (let [humanizers [{:type "ignore" :field "platform" :source_value "A" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {}))
        (testing "one"
          (humanizes humanizers
                     {:platforms [{:short-name "A"}]}
                     {:platforms [{:short-name "A"
                                   :cmr.humanized/short-name nil}]}))

        (testing "many"
          (humanizes humanizers
                     {:platforms [{:short-name "A"},
                                  {:short-name "B"}]}
                     {:platforms [{:short-name "A"
                                   :cmr.humanized/short-name nil},
                                  {:short-name "B"
                                   :cmr.humanized/short-name "B"}]})))))

  (testing "humanize instrument"
    (testing "trim_whitespace"
      (let [humanizers [{:type "trim_whitespace" :field "instrument" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {})
          (humanizes humanizers
                     {:platforms [{:short-name "Foo\t"}]}
                     {:platforms [{:short-name "Foo\t"
                                   :cmr.humanized/short-name "Foo\t"}]}))
        (testing "one"
          (humanizes humanizers
                     {:platforms [{:instruments [{:short-name " TEST/value\t"}]}]}
                     {:platforms [{:cmr.humanized/short-name nil
                                   :instruments [{:short-name " TEST/value\t"
                                                  :cmr.humanized/short-name "TEST/value"}]}]}))
        (testing "many"
          (humanizes humanizers
                     {:platforms [{:instruments [{:short-name " TEST/value1\t"},
                                                 {:short-name " TEST/value2\t"}]},
                                  {:instruments [{:short-name " TEST/value3\t"}]}]}
                     {:platforms [{:cmr.humanized/short-name nil
                                   :instruments [{:short-name " TEST/value1\t"
                                                  :cmr.humanized/short-name "TEST/value1"},
                                                 {:short-name " TEST/value2\t"
                                                  :cmr.humanized/short-name "TEST/value2"}]},
                                  {:cmr.humanized/short-name nil
                                   :instruments [{:short-name " TEST/value3\t"
                                                  :cmr.humanized/short-name "TEST/value3"}]}]}))
        (testing "with source value"
          (humanizes (map #(assoc %1 :source_value " TEST/value2\t") humanizers)
                     {:platforms [{:instruments [{:short-name " TEST/value1\t"},
                                                 {:short-name " TEST/value2\t"}]},
                                  {:instruments [{:short-name " TEST/value3\t"}]}]}
                     {:platforms [{:cmr.humanized/short-name nil
                                   :instruments [{:short-name " TEST/value1\t"
                                                  :cmr.humanized/short-name " TEST/value1\t"},
                                                 {:short-name " TEST/value2\t"
                                                  :cmr.humanized/short-name "TEST/value2"}]},
                                  {:cmr.humanized/short-name nil
                                   :instruments [{:short-name " TEST/value3\t"
                                                  :cmr.humanized/short-name " TEST/value3\t"}]}]}))))
    (testing "capitalize"
      (let [humanizers [{:type "capitalize" :field "instrument" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {})
          (humanizes humanizers
                     {:platforms [{:short-name "foo"}]}
                     {:platforms [{:short-name "foo"
                                   :cmr.humanized/short-name "foo"}]}))
        (testing "one"
          (humanizes humanizers
                     {:platforms [{:instruments [{:short-name "TEST/value"}]}]}
                     {:platforms [{:cmr.humanized/short-name nil
                                   :instruments [{:short-name "TEST/value"
                                                  :cmr.humanized/short-name "Test/Value"}]}]}))
        (testing "many"
          (humanizes humanizers
                     {:platforms [{:instruments [{:short-name "TEST/value1"},
                                                 {:short-name "TEST/value2"}]},
                                  {:instruments [{:short-name "TEST/value3"}]}]}
                     {:platforms [{:cmr.humanized/short-name nil
                                   :instruments [{:short-name "TEST/value1"
                                                  :cmr.humanized/short-name "Test/Value1"},
                                                 {:short-name "TEST/value2"
                                                  :cmr.humanized/short-name "Test/Value2"}]},
                                  {:cmr.humanized/short-name nil
                                   :instruments [{:short-name "TEST/value3"
                                                  :cmr.humanized/short-name "Test/Value3"}]}]}))
        (testing "with source value"
          (humanizes (map #(assoc %1 :source_value "TEST/value2") humanizers)
                     {:platforms [{:instruments [{:short-name "TEST/value1"},
                                                 {:short-name "TEST/value2"}]},
                                  {:instruments [{:short-name "TEST/value3"}]}]}
                     {:platforms [{:cmr.humanized/short-name nil
                                   :instruments [{:short-name "TEST/value1"
                                                  :cmr.humanized/short-name "TEST/value1"},
                                                 {:short-name "TEST/value2"
                                                  :cmr.humanized/short-name "Test/Value2"}]},
                                  {:cmr.humanized/short-name nil
                                   :instruments [{:short-name "TEST/value3"
                                                  :cmr.humanized/short-name "TEST/value3"}]}]}))))


    (testing "alias"
      (let [humanizers [{:type "alias" :field "instrument" :source_value "A" :replacement_value "X":priority 0}
                        {:type "alias" :field "instrument" :source_value "B" :replacement_value "Y":priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {})
          (humanizes humanizers
                     {:platforms [{:short-name "foo"}]}
                     {:platforms [{:short-name "foo"
                                   :cmr.humanized/short-name "foo"}]}))
        (testing "one"
          (humanizes humanizers
                     {:platforms [{:instruments [{:short-name "A"}]}]}
                     {:platforms [{:cmr.humanized/short-name nil
                                   :instruments [{:short-name "A"
                                                  :cmr.humanized/short-name "X"}]}]}))
        (testing "many"
          (humanizes humanizers
                     {:platforms [{:instruments [{:short-name "A"},
                                                 {:short-name "C"}]},
                                  {:instruments [{:short-name "B"}]}]}
                     {:platforms [{:cmr.humanized/short-name nil
                                   :instruments [{:short-name "A"
                                                  :cmr.humanized/short-name "X"},
                                                 {:short-name "C"
                                                  :cmr.humanized/short-name "C"}]},
                                  {:cmr.humanized/short-name nil
                                   :instruments [{:short-name "B"
                                                  :cmr.humanized/short-name "Y"}]}]}))))

    (testing "ignore"
      (let [humanizers [{:type "ignore" :field "instrument" :source_value "A" :priority 0}
                        {:type "ignore" :field "instrument" :source_value "B" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {})
          (humanizes humanizers
                     {:platforms [{:short-name "foo"}]}
                     {:platforms [{:short-name "foo"
                                   :cmr.humanized/short-name "foo"}]}))
        (testing "one"
          (humanizes humanizers
                     {:platforms [{:instruments [{:short-name "A"}]}]}
                     {:platforms [{:cmr.humanized/short-name nil
                                   :instruments [{:short-name "A"
                                                  :cmr.humanized/short-name nil}]}]}))
        (testing "many"
          (humanizes humanizers
                     {:platforms [{:instruments [{:short-name "A"},
                                                 {:short-name "C"}]},
                                  {:instruments [{:short-name "B"}]}]}
                     {:platforms [{:cmr.humanized/short-name nil
                                   :instruments [{:short-name "A"
                                                  :cmr.humanized/short-name nil},
                                                 {:short-name "C"
                                                  :cmr.humanized/short-name "C"}]},
                                  {:cmr.humanized/short-name nil
                                   :instruments [{:short-name "B"
                                                  :cmr.humanized/short-name nil}]}]})))))

  (testing "humanize processing level"
    (testing "trim_whitespace"
      (let [humanizers [{:type "trim_whitespace" :field "processing_level" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {}))

        (testing "one"
          (humanizes humanizers
                     {:product {:processing-level-id " TEST/value\t"}}
                     {:product {:processing-level-id " TEST/value\t"
                                :cmr.humanized/processing-level-id "TEST/value"}}))

        (testing "with source value"
          (humanizes (map #(assoc %1 :source_value " TEST/value1\t") humanizers)
                     {:product {:processing-level-id " TEST/value1\t"}}
                     {:product {:processing-level-id " TEST/value1\t"
                                :cmr.humanized/processing-level-id "TEST/value1"}})
          (humanizes (map #(assoc %1 :source_value " TEST/value1\t") humanizers)
                     {:product {:processing-level-id " TEST/value2\t"}}
                     {:product {:processing-level-id " TEST/value2\t"
                                :cmr.humanized/processing-level-id " TEST/value2\t"}}))))

    (testing "capitalize"
      (let [humanizers [{:type "capitalize" :field "processing_level" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {}))

        (testing "one"
          (humanizes humanizers
                     {:product {:processing-level-id "TEST/value"}}
                     {:product {:processing-level-id "TEST/value"
                                :cmr.humanized/processing-level-id "Test/Value"}}))

        (testing "with source value"
          (humanizes (map #(assoc %1 :source_value "TEST/value1") humanizers)
                     {:product {:processing-level-id "TEST/value1"}}
                     {:product {:processing-level-id "TEST/value1"
                                :cmr.humanized/processing-level-id "Test/Value1"}})
          (humanizes (map #(assoc %1 :source_value "TEST/value1") humanizers)
                     {:product {:processing-level-id "TEST/value2"}}
                     {:product {:processing-level-id "TEST/value2"
                                :cmr.humanized/processing-level-id "TEST/value2"}}))))

    (testing "alias"
      (let [humanizers [{:type "alias" :field "processing_level" :source_value "A" :replacement_value "X" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {}))

        (testing "one"
          (humanizes humanizers
                     {:product {:processing-level-id "A"}}
                     {:product {:processing-level-id "A"
                                :cmr.humanized/processing-level-id "X"}}))
          (humanizes humanizers
                     {:product {:processing-level-id "B"}}
                     {:product {:processing-level-id "B"
                                :cmr.humanized/processing-level-id "B"}})))

    (testing "ignore"
      (let [humanizers [{:type "ignore" :field "processing_level" :source_value "A" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {}))

        (testing "one"
          (humanizes humanizers
                     {:product {:processing-level-id "A"}}
                     {:product {:processing-level-id "A"
                                :cmr.humanized/processing-level-id nil}}))
          (humanizes humanizers
                     {:product {:processing-level-id "B"}}
                     {:product {:processing-level-id "B"
                                :cmr.humanized/processing-level-id "B"}}))))

  (testing "humanize project"
    (testing "trim_whitespace"
      (let [humanizers [{:type "trim_whitespace" :field "project" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {}))
        (testing "one"
          (humanizes humanizers
                     {:projects [{:short-name " TEST/value\t"}]}
                     {:projects [{:short-name " TEST/value\t"
                                  :cmr.humanized/short-name "TEST/value"}]}))

        (testing "many"
          (humanizes humanizers
                     {:projects [{:short-name " TEST/value1\t"},
                                 {:short-name " TEST/value2\t"}]}
                     {:projects [{:short-name " TEST/value1\t"
                                  :cmr.humanized/short-name "TEST/value1"},
                                 {:short-name " TEST/value2\t"
                                  :cmr.humanized/short-name "TEST/value2"}]}))

        (testing "with source value"
          (humanizes (map #(assoc %1 :source_value " TEST/value1\t") humanizers)
                     {:projects [{:short-name " TEST/value1\t"},
                                 {:short-name " TEST/value2\t"}]}
                     {:projects [{:short-name " TEST/value1\t"
                                  :cmr.humanized/short-name "TEST/value1"},
                                 {:short-name " TEST/value2\t"
                                  :cmr.humanized/short-name " TEST/value2\t"}]}))))
    (testing "capitalize"
      (let [humanizers [{:type "capitalize" :field "project" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {}))
        (testing "one"
          (humanizes humanizers
                     {:projects [{:short-name "TEST/value"}]}
                     {:projects [{:short-name "TEST/value"
                                  :cmr.humanized/short-name "Test/Value"}]}))

        (testing "many"
          (humanizes humanizers
                     {:projects [{:short-name "TEST/value1"},
                                 {:short-name "TEST/value2"}]}
                     {:projects [{:short-name "TEST/value1"
                                  :cmr.humanized/short-name "Test/Value1"},
                                 {:short-name "TEST/value2"
                                  :cmr.humanized/short-name "Test/Value2"}]}))

        (testing "with source value"
          (humanizes (map #(assoc %1 :source_value "TEST/value2") humanizers)
                     {:projects [{:short-name "TEST/value1"},
                                 {:short-name "TEST/value2"}]}
                     {:projects [{:short-name "TEST/value1"
                                  :cmr.humanized/short-name "TEST/value1"},
                                 {:short-name "TEST/value2"
                                  :cmr.humanized/short-name "Test/Value2"}]}))))
    (testing "alias"
      (let [humanizers [{:type "alias" :field "project" :source_value "A" :replacement_value "X" :priority 0}
                        {:type "alias" :field "project" :source_value "B" :replacement_value "Y" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {}))
        (testing "one"
          (humanizes humanizers
                     {:projects [{:short-name "A"}]}
                     {:projects [{:short-name "A"
                                  :cmr.humanized/short-name "X"}]}))

        (testing "many"
          (humanizes humanizers
                     {:projects [{:short-name "A"},
                                 {:short-name "B"}]}
                     {:projects [{:short-name "A"
                                  :cmr.humanized/short-name "X"},
                                 {:short-name "B"
                                  :cmr.humanized/short-name "Y"}]}))))
    (testing "ignore"
      (let [humanizers [{:type "ignore" :field "project" :source_value "A" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {}))
        (testing "one"
          (humanizes humanizers
                     {:projects [{:short-name "A"}]}
                     {:projects [{:short-name "A"
                                  :cmr.humanized/short-name nil}]}))

        (testing "many"
          (humanizes humanizers
                     {:projects [{:short-name "A"},
                                 {:short-name "B"}]}
                     {:projects [{:short-name "A"
                                  :cmr.humanized/short-name nil},
                                 {:short-name "B"
                                  :cmr.humanized/short-name "B"}]})))))

  (testing "humanize science keywords"
    (testing "trim_whitespace"
      (let [humanizers [{:type "trim_whitespace" :field "science_keyword" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {})
          (humanizes humanizers
                   {:science-keywords [{}]}
                   {:science-keywords [{:cmr.humanized/category nil
                                        :cmr.humanized/topic nil
                                        :cmr.humanized/term nil
                                        :cmr.humanized/variable-level-1 nil
                                        :cmr.humanized/variable-level-2 nil
                                        :cmr.humanized/variable-level-3 nil
                                        :cmr.humanized/detailed-variable nil
                                        }]}))
        (testing "one"
          (humanizes humanizers
                     {:science-keywords [{:category " sci cat 0\t"
                                          :topic " sci top 0\t"
                                          :term " sci ter 0\t"
                                          :variable-level-1 " sci var1 0\t"
                                          :variable-level-2 " sci var2 0\t"
                                          :variable-level-3 " sci var3 0\t"
                                          :detailed-variable " sci det 0\t"}]}
                     {:science-keywords [{:category " sci cat 0\t"
                                          :cmr.humanized/category "sci cat 0"
                                          :topic " sci top 0\t"
                                          :cmr.humanized/topic "sci top 0"
                                          :term " sci ter 0\t"
                                          :cmr.humanized/term "sci ter 0"
                                          :variable-level-1 " sci var1 0\t"
                                          :cmr.humanized/variable-level-1 "sci var1 0"
                                          :variable-level-2 " sci var2 0\t"
                                          :cmr.humanized/variable-level-2 "sci var2 0"
                                          :variable-level-3 " sci var3 0\t"
                                          :cmr.humanized/variable-level-3 "sci var3 0"
                                          :detailed-variable " sci det 0\t"
                                          :cmr.humanized/detailed-variable "sci det 0"}]}))

        (testing "many"
          (humanizes humanizers
                     {:science-keywords [{:category " sci cat 0\t"}
                                         {:category " sci cat 1\t"}]}
                     {:science-keywords [{:category " sci cat 0\t"
                                          :cmr.humanized/category "sci cat 0"
                                          :cmr.humanized/topic nil
                                          :cmr.humanized/term nil
                                          :cmr.humanized/variable-level-1 nil
                                          :cmr.humanized/variable-level-2 nil
                                          :cmr.humanized/variable-level-3 nil
                                          :cmr.humanized/detailed-variable nil}
                                         {:category " sci cat 1\t"
                                          :cmr.humanized/category "sci cat 1"
                                          :cmr.humanized/topic nil
                                          :cmr.humanized/term nil
                                          :cmr.humanized/variable-level-1 nil
                                          :cmr.humanized/variable-level-2 nil
                                          :cmr.humanized/variable-level-3 nil
                                          :cmr.humanized/detailed-variable nil}]}))

        (testing "with source value"
          (humanizes (map #(assoc %1 :source_value " sci target\t") humanizers)
                     {:science-keywords [{:category " sci target\t"}
                                         {:category " sci cat 1\t"
                                          :term " sci target\t"}]}
                     {:science-keywords [{:category " sci target\t"
                                          :cmr.humanized/category "sci target"
                                          :cmr.humanized/topic nil
                                          :cmr.humanized/term nil
                                          :cmr.humanized/variable-level-1 nil
                                          :cmr.humanized/variable-level-2 nil
                                          :cmr.humanized/variable-level-3 nil
                                          :cmr.humanized/detailed-variable nil}
                                         {:category " sci cat 1\t"
                                          :cmr.humanized/category " sci cat 1\t"
                                          :cmr.humanized/topic nil
                                          :term " sci target\t"
                                          :cmr.humanized/term "sci target"
                                          :cmr.humanized/variable-level-1 nil
                                          :cmr.humanized/variable-level-2 nil
                                          :cmr.humanized/variable-level-3 nil
                                          :cmr.humanized/detailed-variable nil}]}))))


    (testing "capitalize"
      (let [humanizers [{:type "capitalize" :field "science_keyword" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {})
          (humanizes humanizers
                   {:science-keywords [{}]}
                   {:science-keywords [{:cmr.humanized/category nil
                                        :cmr.humanized/topic nil
                                        :cmr.humanized/term nil
                                        :cmr.humanized/variable-level-1 nil
                                        :cmr.humanized/variable-level-2 nil
                                        :cmr.humanized/variable-level-3 nil
                                        :cmr.humanized/detailed-variable nil
                                        }]}))
        (testing "one"
          (humanizes humanizers
                     {:science-keywords [{:category "sci cat 0"
                                          :topic "sci top 0"
                                          :term "sci ter 0"
                                          :variable-level-1 "sci var1 0"
                                          :variable-level-2 "sci var2 0"
                                          :variable-level-3 "sci var3 0"
                                          :detailed-variable "sci det 0"}]}
                     {:science-keywords [{:category "sci cat 0"
                                          :cmr.humanized/category "Sci Cat 0"
                                          :topic "sci top 0"
                                          :cmr.humanized/topic "Sci Top 0"
                                          :term "sci ter 0"
                                          :cmr.humanized/term "Sci Ter 0"
                                          :variable-level-1 "sci var1 0"
                                          :cmr.humanized/variable-level-1 "Sci Var1 0"
                                          :variable-level-2 "sci var2 0"
                                          :cmr.humanized/variable-level-2 "Sci Var2 0"
                                          :variable-level-3 "sci var3 0"
                                          :cmr.humanized/variable-level-3 "Sci Var3 0"
                                          :detailed-variable "sci det 0"
                                          :cmr.humanized/detailed-variable "Sci Det 0"}]}))

        (testing "many"
          (humanizes humanizers
                     {:science-keywords [{:category "sci cat 0"}
                                         {:category "sci cat 1"}]}
                     {:science-keywords [{:category "sci cat 0"
                                          :cmr.humanized/category "Sci Cat 0"
                                          :cmr.humanized/topic nil
                                          :cmr.humanized/term nil
                                          :cmr.humanized/variable-level-1 nil
                                          :cmr.humanized/variable-level-2 nil
                                          :cmr.humanized/variable-level-3 nil
                                          :cmr.humanized/detailed-variable nil}
                                         {:category "sci cat 1"
                                          :cmr.humanized/category "Sci Cat 1"
                                          :cmr.humanized/topic nil
                                          :cmr.humanized/term nil
                                          :cmr.humanized/variable-level-1 nil
                                          :cmr.humanized/variable-level-2 nil
                                          :cmr.humanized/variable-level-3 nil
                                          :cmr.humanized/detailed-variable nil}]}))

        (testing "with source value"
          (humanizes (map #(assoc %1 :source_value "sci target") humanizers)
                     {:science-keywords [{:category "sci target"}
                                         {:category "sci cat 1"
                                          :term "sci target"}]}
                     {:science-keywords [{:category "sci target"
                                          :cmr.humanized/category "Sci Target"
                                          :cmr.humanized/topic nil
                                          :cmr.humanized/term nil
                                          :cmr.humanized/variable-level-1 nil
                                          :cmr.humanized/variable-level-2 nil
                                          :cmr.humanized/variable-level-3 nil
                                          :cmr.humanized/detailed-variable nil}
                                         {:category "sci cat 1"
                                          :cmr.humanized/category "sci cat 1"
                                          :cmr.humanized/topic nil
                                          :term "sci target"
                                          :cmr.humanized/term "Sci Target"
                                          :cmr.humanized/variable-level-1 nil
                                          :cmr.humanized/variable-level-2 nil
                                          :cmr.humanized/variable-level-3 nil
                                          :cmr.humanized/detailed-variable nil}]}))))

    (testing "alias"
      (let [humanizers [{:type "alias" :field "science_keyword" :source_value "A" :replacement_value "X" :priority 0}
                        {:type "alias" :field "science_keyword" :source_value "B" :replacement_value "Y" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {})
          (humanizes humanizers
                   {:science-keywords [{}]}
                   {:science-keywords [{:cmr.humanized/category nil
                                        :cmr.humanized/topic nil
                                        :cmr.humanized/term nil
                                        :cmr.humanized/variable-level-1 nil
                                        :cmr.humanized/variable-level-2 nil
                                        :cmr.humanized/variable-level-3 nil
                                        :cmr.humanized/detailed-variable nil
                                        }]}))
        (testing "one"
          (humanizes humanizers
                     {:science-keywords [{:category "A"
                                          :topic "B"
                                          :term "A"
                                          :variable-level-1 "B"
                                          :variable-level-2 "A"
                                          :variable-level-3 "B"
                                          :detailed-variable "A"}]}
                     {:science-keywords [{:category "A"
                                          :cmr.humanized/category "X"
                                          :topic "B"
                                          :cmr.humanized/topic "Y"
                                          :term "A"
                                          :cmr.humanized/term "X"
                                          :variable-level-1 "B"
                                          :cmr.humanized/variable-level-1 "Y"
                                          :variable-level-2 "A"
                                          :cmr.humanized/variable-level-2 "X"
                                          :variable-level-3 "B"
                                          :cmr.humanized/variable-level-3 "Y"
                                          :detailed-variable "A"
                                          :cmr.humanized/detailed-variable "X"}]}))

        (testing "many"
          (humanizes humanizers
                     {:science-keywords [{:category "A" :topic "C"}
                                         {:category "D" :topic "B"}]}
                     {:science-keywords [{:category "A"
                                          :cmr.humanized/category "X"
                                          :topic "C"
                                          :cmr.humanized/topic "C"
                                          :cmr.humanized/term nil
                                          :cmr.humanized/variable-level-1 nil
                                          :cmr.humanized/variable-level-2 nil
                                          :cmr.humanized/variable-level-3 nil
                                          :cmr.humanized/detailed-variable nil}
                                         {:category "D"
                                          :cmr.humanized/category "D"
                                          :topic "B"
                                          :cmr.humanized/topic "Y"
                                          :cmr.humanized/term nil
                                          :cmr.humanized/variable-level-1 nil
                                          :cmr.humanized/variable-level-2 nil
                                          :cmr.humanized/variable-level-3 nil
                                          :cmr.humanized/detailed-variable nil}]}))))

    (testing "ignore"
      (let [humanizers [{:type "ignore" :field "science_keyword" :source_value "A" :priority 0}]]

        (testing "zero"
          (humanizes humanizers {} {})
          (humanizes humanizers
                   {:science-keywords [{}]}
                   {:science-keywords [{:cmr.humanized/category nil
                                        :cmr.humanized/topic nil
                                        :cmr.humanized/term nil
                                        :cmr.humanized/variable-level-1 nil
                                        :cmr.humanized/variable-level-2 nil
                                        :cmr.humanized/variable-level-3 nil
                                        :cmr.humanized/detailed-variable nil
                                        }]}))
        (testing "one"
          (humanizes humanizers
                     {:science-keywords [{:category "A"
                                          :topic "B"
                                          :term "A"
                                          :variable-level-1 "B"
                                          :variable-level-2 "A"
                                          :variable-level-3 "B"
                                          :detailed-variable "A"}]}
                     {:science-keywords [{:category "A"
                                          :cmr.humanized/category nil
                                          :topic "B"
                                          :cmr.humanized/topic "B"
                                          :term "A"
                                          :cmr.humanized/term nil
                                          :variable-level-1 "B"
                                          :cmr.humanized/variable-level-1 "B"
                                          :variable-level-2 "A"
                                          :cmr.humanized/variable-level-2 nil
                                          :variable-level-3 "B"
                                          :cmr.humanized/variable-level-3 "B"
                                          :detailed-variable "A"
                                          :cmr.humanized/detailed-variable nil}]}))

        (testing "many"
          (humanizes humanizers
                     {:science-keywords [{:category "A" :topic "C"}
                                         {:category "D" :topic "A"}]}
                     {:science-keywords [{:category "A"
                                          :cmr.humanized/category nil
                                          :topic "C"
                                          :cmr.humanized/topic "C"
                                          :cmr.humanized/term nil
                                          :cmr.humanized/variable-level-1 nil
                                          :cmr.humanized/variable-level-2 nil
                                          :cmr.humanized/variable-level-3 nil
                                          :cmr.humanized/detailed-variable nil}
                                         {:category "D"
                                          :cmr.humanized/category "D"
                                          :topic "A"
                                          :cmr.humanized/topic nil
                                          :cmr.humanized/term nil
                                          :cmr.humanized/variable-level-1 nil
                                          :cmr.humanized/variable-level-2 nil
                                          :cmr.humanized/variable-level-3 nil
                                          :cmr.humanized/detailed-variable nil}]})))))

  (testing "humanize organization"
    (testing "trim_whitespace"
      (let [humanizers [{:type "trim_whitespace" :field "organization" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {}))
        (testing "one"
          (humanizes humanizers
                     {:organizations [{:org-name " TEST/value\t"}]}
                     {:organizations [{:org-name " TEST/value\t"
                                       :cmr.humanized/org-name "TEST/value"}]}))

        (testing "many"
          (humanizes humanizers
                     {:organizations [{:org-name " TEST/value1\t"},
                                      {:org-name " TEST/value2\t"}]}
                     {:organizations [{:org-name " TEST/value1\t"
                                       :cmr.humanized/org-name "TEST/value1"},
                                      {:org-name " TEST/value2\t"
                                       :cmr.humanized/org-name "TEST/value2"}]}))

        (testing "with source value"
          (humanizes (map #(assoc %1 :source_value " TEST/value1\t") humanizers)
                     {:organizations [{:org-name " TEST/value1\t"},
                                      {:org-name " TEST/value2\t"}]}
                     {:organizations [{:org-name " TEST/value1\t"
                                       :cmr.humanized/org-name "TEST/value1"},
                                      {:org-name " TEST/value2\t"
                                       :cmr.humanized/org-name " TEST/value2\t"}]}))))
    (testing "capitalize"
      (let [humanizers [{:type "capitalize" :field "organization" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {}))
        (testing "one"
          (humanizes humanizers
                     {:organizations [{:org-name "TEST/value"}]}
                     {:organizations [{:org-name "TEST/value"
                                       :cmr.humanized/org-name "Test/Value"}]}))

        (testing "many"
          (humanizes humanizers
                     {:organizations [{:org-name "TEST/value1"},
                                      {:org-name "TEST/value2"}]}
                     {:organizations [{:org-name "TEST/value1"
                                       :cmr.humanized/org-name "Test/Value1"},
                                      {:org-name "TEST/value2"
                                       :cmr.humanized/org-name "Test/Value2"}]}))

        (testing "with source value"
          (humanizes (map #(assoc %1 :source_value "TEST/value2") humanizers)
                     {:organizations [{:org-name "TEST/value1"},
                                      {:org-name "TEST/value2"}]}
                     {:organizations [{:org-name "TEST/value1"
                                       :cmr.humanized/org-name "TEST/value1"},
                                      {:org-name "TEST/value2"
                                       :cmr.humanized/org-name "Test/Value2"}]}))))
    (testing "alias"
      (let [humanizers [{:type "alias" :field "organization" :source_value "A" :replacement_value "X" :priority 0}
                        {:type "alias" :field "organization" :source_value "B" :replacement_value "Y" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {}))
        (testing "one"
          (humanizes humanizers
                     {:organizations [{:org-name "A"}]}
                     {:organizations [{:org-name "A"
                                       :cmr.humanized/org-name "X"}]}))

        (testing "many"
          (humanizes humanizers
                     {:organizations [{:org-name "A"},
                                      {:org-name "B"}]}
                     {:organizations [{:org-name "A"
                                       :cmr.humanized/org-name "X"},
                                      {:org-name "B"
                                       :cmr.humanized/org-name "Y"}]}))))
    (testing "ignore"
      (let [humanizers [{:type "ignore" :field "organization" :source_value "A" :priority 0}]]
        (testing "zero"
          (humanizes humanizers {} {}))
        (testing "one"
          (humanizes humanizers
                     {:organizations [{:org-name "A"}]}
                     {:organizations [{:org-name "A"
                                       :cmr.humanized/org-name nil}]}))

        (testing "many"
          (humanizes humanizers
                     {:organizations [{:org-name "A"},
                                      {:org-name "B"}]}
                     {:organizations [{:org-name "A"
                                       :cmr.humanized/org-name nil},
                                      {:org-name "B"
                                       :cmr.humanized/org-name "B"}]})))))

  (testing "from resource"
    ;; Small sanity check to ensure humanizers are pulled from the resource json by default.
    ;; Assumes that trimming whitespace is done in the humanizer
    (is (=
         {:organizations [{:org-name " TEST\t" :cmr.humanized/org-name "TEST"}]}
         (humanizer/umm-collection->umm-collection+humanizers {:organizations [{:org-name " TEST\t"}]}))))

  (testing "priority"
    (let [humanizers [{:type "alias" :field "organization" :source_value "A" :replacement_value "X" :priority 0}
                      {:type "alias" :field "organization" :source_value "X" :replacement_value "Y" :priority 1}]]
      (humanizes humanizers
                 {:organizations [{:org-name "A"}]}
                 {:organizations [{:org-name "A"
                                   :cmr.humanized/org-name "Y"}]}))
    (let [humanizers [{:type "alias" :field "organization" :source_value "A" :replacement_value "X" :priority 0}
                      {:type "alias" :field "organization" :source_value "X" :replacement_value "Y" :priority 1}]]
      (humanizes humanizers
                 {:organizations [{:org-name "A"}]}
                 {:organizations [{:org-name "A"
                                   :cmr.humanized/org-name "Y"}]}))))
