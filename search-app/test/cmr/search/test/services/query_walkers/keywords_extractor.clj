(ns cmr.search.test.services.query-walkers.keywords-extractor
 (:require
  [clojure.test :refer :all]
  [cmr.common-app.services.search.query-model :as qm]
  [cmr.search.services.query-walkers.keywords-extractor :as ke]
  [cmr.search.test.models.helpers :as h :refer :all]))

(defn keyword-cond
  [text]
  (qm/text-condition :keyword text))

(defn query
  [condition]
  (qm/query {:condition condition}))

(deftest extract-keywords-test
  (let [query (query
               (and-conds
                (keyword-cond "foo bar")
                (other 1)
                (and-conds
                 (qm/string-condition :platform "platform1 platform2")
                 (qm/string-condition :project "project")
                 (qm/string-condition :instrument "instrument")
                 (qm/string-condition :sensor "sensor")
                 (qm/string-condition :science-keywords.category "category")
                 (qm/string-condition :science-keywords.topic "topic")
                 (qm/string-condition :science-keywords.term "term")
                 (qm/string-condition :science-keywords.variable-level-1 "variable-level-1")
                 (qm/string-condition :science-keywords.variable-level-2 "variable-level-2")
                 (qm/string-condition :science-keywords.variable-level-3 "variable-level-3")
                 (qm/string-condition :science-keywords.any "any")
                 (qm/string-condition :two-d-coordinate-system-name "two-d-coordinate-system-name")
                 (qm/string-condition :processing-level-id "processing-level-id")
                 (qm/string-condition :data-center "data-center")
                 (qm/string-condition :archive-center "archive-center"))))
        expected-keywords {:keywords ["foo" "bar"]
                           :field-keywords ["platform1" "platform2" "project" "instrument" "sensor"
                                            "category" "topic" "term" "variable-level-1" "variable-level-2"
                                            "variable-level-3" "any" "two-d-coordinate-system-name"
                                            "processing-level-id" "data-center" "archive-center"]}]
    (ke/extract-keywords query)))

(deftest contains-keyword-condition-test
  (testing "Works with keyword condition"
    (is (ke/contains-keyword-condition? (query (keyword-cond "foo")))))
  (testing "Works with grouped keyword conditions"
    (is (ke/contains-keyword-condition? (query (and-conds
                                                (other)
                                                (keyword-cond "foo")
                                                (other))))))
  (testing "Does not work for other scoring conditions"
    (is (not (ke/contains-keyword-condition? (query (qm/string-condition :platform "foo")))))
    (is (not (ke/contains-keyword-condition? (query (qm/string-condition :science-keywords.any "foo")))))))
