(ns cmr.search.test.services.query-execution.facets.hierarchical-links-helper
  "Unit tests for facets hierarchical links helper namespace."
  (:require [clojure.test :refer :all]
            [cmr.search.services.query-execution.facets.hierarchical-links-helper :as hlh]
            [cmr.common.util :refer [are3]]
            [camel-snake-kebab.core :as csk]
            [clojure.set :as set]
            [clojure.string :as str]))

(def base-url
  "Base URL for each request."
  "http://localhost:3003/collections.json")

(deftest apply-hierarchical-link-test
  (let [param-name "science_keywords[0][topic]"
        term "BIOMASS"]
    (are3 [query-params expected-result]
       (is (= expected-result (hlh/create-link-for-hierarchical-field
                               base-url query-params param-name term)))

       "Apply link to empty params"
       {"foo" "bar"}
       {:apply (str "http://localhost:3003/collections.json?foo=bar&"
                    "science_keywords%5B0%5D%5Btopic%5D=BIOMASS")}

       "Apply link causes index to be increased by one when ancestors are not in the query params"
       {"foo" "bar"
        "science_keywords[0][category]" "EARTH SCIENCE"
        "science_keywords[0][topic]" "AGRICULTURE"
        "science_keywords[1][topic]" "ATMOSPHERE"
        "science_keywords[1][variable_level_3]" "VAR3"}
       {:apply (str "http://localhost:3003/collections.json?foo=bar&"
                     "science_keywords%5B0%5D%5Bcategory%5D=EARTH+SCIENCE&"
                     "science_keywords%5B0%5D%5Btopic%5D=AGRICULTURE&"
                     "science_keywords%5B1%5D%5Btopic%5D=ATMOSPHERE&"
                     "science_keywords%5B1%5D%5Bvariable_level_3%5D=VAR3&"
                     "science_keywords%5B2%5D%5Btopic%5D=BIOMASS")}))

  (testing "Apply hierarchical links using the same index if the ancestors are completely matched"
    (let [query-params {"science_keywords[0][category]" "CAT"
                        "science_keywords[0][topic]" "TOPIC"
                        "science_keywords[0][term]" "TERM"}
          term "VL1"
          param-name "science_keywords[0][variable_level_1]"
          expected-result {:apply (str "http://localhost:3003/collections.json?"
                                       "science_keywords%5B0%5D%5Bcategory%5D=CAT&"
                                       "science_keywords%5B0%5D%5Btopic%5D=TOPIC&"
                                       "science_keywords%5B0%5D%5Bterm%5D=TERM&"
                                       "science_keywords%5B0%5D%5Bvariable_level_1%5D=VL1")}
          ancestors-map {"category" "CAT"
                         "topic" "TOPIC"
                         "term" "TERM"}
          parent-indexes [0]
          children-tuples []
          has-siblings? false
          result (hlh/create-link-for-hierarchical-field base-url query-params param-name
                                                         ancestors-map parent-indexes term
                                                         has-siblings? children-tuples)]
      (is (= expected-result result))))

  (testing "Apply hierarchical links creates new params with a new index if the term has siblings"
    (let [query-params {"science_keywords[0][category]" "CAT"
                        "science_keywords[0][topic]" "TOPIC"
                        "science_keywords[0][term]" "TERM"}
          term "TERM2"
          param-name "science_keywords[0][term]"
          expected-result {:apply (str "http://localhost:3003/collections.json?"
                                       "science_keywords%5B0%5D%5Bcategory%5D=CAT&"
                                       "science_keywords%5B0%5D%5Btopic%5D=TOPIC&"
                                       "science_keywords%5B0%5D%5Bterm%5D=TERM&"
                                       "science_keywords%5B1%5D%5Bterm%5D=TERM2&"
                                       "science_keywords%5B1%5D%5Bcategory%5D=CAT&"
                                       "science_keywords%5B1%5D%5Btopic%5D=TOPIC")}

          ancestors-map {"category" "CAT"
                         "topic" "TOPIC"}
          parent-indexes [0]
          children-tuples []
          has-siblings? true
          result (hlh/create-link-for-hierarchical-field base-url query-params param-name
                                                         ancestors-map parent-indexes term
                                                         has-siblings? children-tuples)]
      (is (= expected-result result)))))

(deftest remove-hierarchical-link-test
  (let [param-name "science_keywords[0][topic]"
        default-query-params {"foo" "bar"
                              "science_keywords[0][category]" "EARTH SCIENCE"
                              "science_keywords[0][topic]" "AGRICULTURE"
                              "science_keywords[1][topic]" "ATMOSPHERE"}]
    (are3 [query-params term expected-result]
       (is (= expected-result
              (hlh/create-link-for-hierarchical-field
                base-url (or query-params default-query-params) param-name nil nil term false nil)))
       "Same index as term being matched"
       nil
       "AGRICULTURE"
       {:remove (str "http://localhost:3003/collections.json?foo=bar&"
                     "science_keywords%5B0%5D%5Bcategory%5D=EARTH+SCIENCE&"
                     "science_keywords%5B1%5D%5Btopic%5D=ATMOSPHERE")}

       "Different index param name being matched: science_keywords[1] vs. science_keywords[0]"
       nil
       "ATMOSPHERE"
       {:remove (str "http://localhost:3003/collections.json?foo=bar&"
                     "science_keywords%5B0%5D%5Bcategory%5D=EARTH+SCIENCE&"
                     "science_keywords%5B0%5D%5Btopic%5D=AGRICULTURE")}

       "Multiple values for the term to be removed"
       (assoc default-query-params "science_keywords[1][topic]" ["ATMOSPHERE" "BIOMASS"])
       "ATMOSPHERE"
       {:remove (str "http://localhost:3003/collections.json?foo=bar&"
                     "science_keywords%5B0%5D%5Bcategory%5D=EARTH+SCIENCE&"
                     "science_keywords%5B0%5D%5Btopic%5D=AGRICULTURE&"
                     "science_keywords%5B1%5D%5Btopic%5D=BIOMASS")}

       "Term being removed is referenced multiple times"
       (assoc default-query-params "science_keywords[1][topic]" ["ATMOSPHERE" "ATMOSPHERE"])
       "ATMOSPHERE"
       {:remove (str "http://localhost:3003/collections.json?foo=bar&"
                     "science_keywords%5B0%5D%5Bcategory%5D=EARTH+SCIENCE&"
                     "science_keywords%5B0%5D%5Btopic%5D=AGRICULTURE")}))

  (testing "Remove all links and case insensitivity"
    (let [query-params {"science_keywords[0][category]" "EARTH SCIENCE"}
          term "Earth Science"
          param-name "science_keywords[0][category]"
          expected-result {:remove "http://localhost:3003/collections.json"}
          result (hlh/create-link-for-hierarchical-field base-url query-params param-name term)]
      (is (= expected-result result))))

  (testing "Remove hierarchical links removes applied params at lower levels in the hierarchy"
    (let [query-params {"science_keywords[0][category]" "CAT"
                        "science_keywords[0][topic]" "TOPIC"
                        "science_keywords[0][term]" "TERM"
                        "science_keywords[0][variable_level_1]" "VL1"
                        "science_keywords[0][variable_level_2]" "VL2"
                        "science_keywords[0][variable_level_3]" "VL3"}
          term "TOPIC"
          param-name "science_keywords[0][topic]"
          expected-result {:remove (str "http://localhost:3003/collections.json?"
                                        "science_keywords%5B0%5D%5Bcategory%5D=CAT")}
          other-params [[:term "TERM"] [:variable-level-1 "VL1"] [:variable-level-2 "VL2"]
                        [:variable-level-3 "VL3"]]
          result (hlh/create-link-for-hierarchical-field base-url query-params param-name nil nil
                                                         term false other-params)]
      (is (= expected-result result))))

  (testing "Remove hierarchical links cleans up other query param indexes that are no longer needed"
    (let [query-params {"science_keywords[0][category]" "CAT"
                        "science_keywords[0][topic]" "TOPIC"
                        "science_keywords[0][term]" "TERM"
                        "science_keywords[0][variable_level_1]" "VL1"
                        "science_keywords[0][variable_level_2]" "VL2"
                        "science_keywords[0][variable_level_3]" "VL3"
                        "science_keywords[1][category]" "CAT"
                        "science_keywords[1][topic]" "TOPIC"
                        "science_keywords[1][term]" "Another Term"
                        "science_keywords[1][variable_level_1]" "Another VL1"
                        "science_keywords[1][variable_level_2]" "Another VL2"
                        "science_keywords[1][variable_level_3]" "Another VL3"}

          term "TOPIC"
          param-name "science_keywords[0][topic]"
          expected-result {:remove (str "http://localhost:3003/collections.json?"
                                        "science_keywords%5B0%5D%5Bcategory%5D=CAT")}
          ancestors-map {"category" "CAT"}
          parent-indexes [0 1]
          children-tuples [[:term "TERM"] [:variable-level-1 "VL1"] [:variable-level-2 "VL2"]
                           [:variable-level-3 "VL3"] [:term "Another Term"]
                           [:variable-level-1 "Another VL1"] [:variable-level-2 "Another VL2"]
                           [:variable-level-3 "Another VL3"]]
          result (hlh/create-link-for-hierarchical-field base-url query-params param-name
                                                         ancestors-map parent-indexes term false
                                                         children-tuples)]
      (is (= expected-result result)))))

(def get-max-index-for-field-name
  "Var to call private function in links helper namespace."
  #'hlh/get-max-index-for-field-name)

(def get-indexes-for-field-name
  "Var to call private get-indexes-for-field-name function."
  #'hlh/get-indexes-for-field-name)

(deftest get-max-index-for-field-name-test
  (are3 [query-params expected-indexes expected-max]
    (do
      (is (= expected-max (get-max-index-for-field-name query-params "foo")))
      (is (= expected-indexes (get-indexes-for-field-name query-params "foo"))))

    "Nil params returns -1"
    nil #{} -1

    "Empty params returns -1"
    {} #{} -1

    "Param not in query-params returns -1"
    {"a" true
     "b[1][c]" "abc"} #{} -1

    "Single matching param"
    {"foo[0][a]" true} #{0} 0

    "Double digit index"
    {"foo[14][a]" true} #{14} 14

    "Large index"
    {"foo[1234567890][a]" true} #{1234567890} 1234567890

    "Multiple matching finds largest"
    {"foo[0][a]" false
     "foo[5][a]" 1
     "foo[12][b]" "str"
     "foo[99][a]" true
     "foo[151][c]" "c"
     "foo[140][a]" 15}
    #{0 5 12 99 140 151} 151

    "Similar query params finds correct largest term index"
    {"foo[0][a]" false
     "bar[550][foo]" 1
     "foo[12][b]" "str"
     "afoo[99][a]" true
     "food[151][c]" "c"
     "foofoo[140][a]" 15}
    #{0 12} 12))

(def split-into-base-field-and-subfield
  "Var to call private split-into-base-field-and-subfield function."
  #'hlh/split-into-base-field-and-subfield)

(deftest split-into-base-field-and-subfield-test
  (are3 [param-name expected-results]
    (is (= expected-results (split-into-base-field-and-subfield param-name)))

    "Nominal case"
    "foo[0][bar]" ["foo" "bar"]

    "Large index"
    "foo[1234567890][bar]" ["foo" "bar"]

    "Number in subfield"
    "foo[1][variable_level_1]" ["foo" "variable_level_1"]

    "Number in base field"
    "foo_1[0][bar]" ["foo_1" "bar"]

    "Missing index and subfield"
    "foo" [nil nil]

    "Missing index"
    "foo[bar]" [nil nil]))

(def get-keys-to-update
  "Var to call private get-keys-to-update function."
  #'hlh/get-keys-to-update)

(def get-keys-to-remove
  "Var to call private get-keys-to-remove function."
  #'hlh/get-keys-to-remove)

(deftest get-keys-to-update-and-remove-test
  (are3 [query-params value expected-update-keys expected-remove-keys]
    (do
      (is (= expected-update-keys (get-keys-to-update query-params value)))
      (is (= expected-remove-keys (get-keys-to-remove query-params value))))

    "Nominal remove case"
    {:a "found"} "found" [] [:a]

    "Nominal update case"
    {:a ["found" "bar"]} "found" [:a] []

    "Mix of single and multi-values"
    {:a "found"
     :b ["cat" "found"]} "found" [:b] [:a]

    "Multiple matches"
    {:a ["found" "bar"]
     :b ["cat" "found"]
     :c "found"} "found" [:a :b] [:c]

    "Case insensitive matching"
    {:a ["FoUND" "apple"]
     :b "found"} "foUNd" [:a] [:b]

    "Handles empty query params"
    {} "not-found" [] []

    "Handles nil query params"
    nil "not-found" [] []))

(def get-potential-matching-query-params
  "Var to call private get-potential-matching-query-params function."
  #'hlh/get-potential-matching-query-params)

(deftest get-potential-matching-query-params-test
  (are3 [query-params expected-result]
    (is (= expected-result (get-potential-matching-query-params query-params "foo[0][bar]")))

    "Nominal case"
    {"foo[0][bar]" "a"} {"foo[0][bar]" "a"}

    "Large index"
    {"foo[1234567890][bar]" "a"} {"foo[1234567890][bar]" "a"}

    "Multiple matches"
    {"foo[0][bar]" "a", "foo[15][bar]" "b"}
    {"foo[0][bar]" "a", "foo[15][bar]" "b"}

    "Different subfields do not match"
    {"foo[0][charlie]" "a"} nil

    "Different base fields do not match"
    {"alpha[0][bar]" "a"} nil

    "Combination of matching and not matching"
    {"foo[0][bar]" "a"
     "foo[0][charlie]" "a"
     "foo[15][bar]" "b"
     "alpha[0][bar]" "a"}
    {"foo[0][bar]" "a", "foo[15][bar]" "b"}

    "Handles empty query params"
    {} nil

    "Handles nil query params"
    nil nil))

(def remove-value-from-query-params-for-hierachical-field
  "Var to call private remove-value-from-query-params-for-hierachical-field function."
  #'hlh/remove-value-from-query-params-for-hierachical-field)

(deftest remove-value-from-query-params-for-hierachical-field-test
  (are3 [query-params potential-matches expected-result]
    (is (= expected-result (remove-value-from-query-params-for-hierachical-field
                            query-params potential-matches "found")))

    "Remove a parameter"
    {"foo" "found"}
    {"foo" "found"}
    {}

    "Update a parameter"
    {"foo" ["found" "extra"]}
    {"foo" ["found" "extra"]}
    {"foo" ["extra"]}

    "Parameters for removing and updating are compared case insensitively"
    {"foo" ["FOUND" "extra"]
     "bar" "FoUnD"}
    {"foo" ["FOUND" "extra"]
     "bar" "FoUnD"}
    {"foo" ["extra"]}

    "Params outside of potential-matches are not modified"
    {"foo" "found"
     "bar" "found"
     "charlie" 15}
    {"foo" "found"}
    {"bar" "found"
     "charlie" 15}

    "Handles nil potential matches"
    {"foo" "found"}
    nil
    {"foo" "found"}

    "Handles nil query params"
    nil
    {"foo" "found"}
    nil))

(def remove-index-from-params
  "Var to call private remove-index-from-params function."
  #'hlh/remove-index-from-params)

(deftest remove-index-from-params-test
  (are3 [query-params expected-results]
    (is (= expected-results (remove-index-from-params query-params)))

    "Nominal case"
    {"foo[0][bar]" "a"}
    {"foo[bar]" "a"}

    "Large index"
    {"foo[1234567890][bar]" ["a" "b"]}
    {"foo[bar]" ["a" "b"]}

    "Number in subfield"
    {"foo[1][variable_level_1]" "a"}
    {"foo[variable_level_1]" "a"}

    "Number in base field"
    {"foo_1[0][bar]" "a"}
    {"foo_1[bar]" "a"}

    "Missing index"
    {"foo[bar]" "a"}
    {"foo[bar]" "a"}

    "Nil query params"
    nil nil))

(def remove-duplicate-params
  "Var to call private remove-duplicate-params function."
  #'hlh/remove-duplicate-params)

(deftest remove-duplicate-params-test
  (are3 [query-params expected-results]
    (is (= expected-results (remove-duplicate-params query-params "foo")))

    "Single duplicate param"
    {"foo[0][bar]" "a"
     "foo[1][bar]" "a"}
    {"foo[0][bar]" "a"}

    "No duplicate values"
    {"foo[0][bar]" "a"
     "foo[1][bar]" "b"}
    {"foo[0][bar]" "a"
     "foo[1][bar]" "b"}

    "One subset of another"
    {"foo[0][bar]" "a"
     "foo[1][charlie]" "c"
     "foo[1][bar]" "a"}
    {"foo[1][charlie]" "c"
     "foo[1][bar]" "a"}

    "Many exact duplicate params leaves exactly one duplicate with the smallest index"
    {"foo[0][bar]" "a"
     "foo[1][bar]" "a"
     "foo[2][bar]" "a"
     "foo[15][bar]" "a"
     "foo[147][bar]" "a"
     "foo[3][bar]" "a"
     "foo[6][bar]" "a"
     "foo[8][bar]" "a"}
    {"foo[0][bar]" "a"}

    (str "Single exact duplicates with multiple subfields leaves exactly one duplicate of all "
         "subfields with the smallest index")
    {"foo[0][bar]" "a"
     "foo[0][charlie]" "c"
     "foo[0][delta]" "d"
     "foo[1][bar]" "a"
     "foo[1][charlie]" "c"
     "foo[1][delta]" "d"}
    {"foo[0][bar]" "a"
     "foo[0][charlie]" "c"
     "foo[0][delta]" "d"}

    "Partial match between multiple indexes does not remove anything"
    {"foo[0][bar]" "a"
     "foo[0][delta]" "d"
     "foo[1][bar]" "a"
     "foo[1][charlie]" "c"}
    {"foo[0][bar]" "a"
     "foo[0][delta]" "d"
     "foo[1][bar]" "a"
     "foo[1][charlie]" "c"}

    "One superset causes all subsets to be removed."
    {"foo[0][bar]" "a"
     "foo[1][bar]" "a"
     "foo[2][bar]" "a"
     "foo[15][bar]" "a"
     "foo[15][charlie]" "c"
     "foo[147][bar]" "a"
     "foo[3][bar]" "a"
     "foo[6][bar]" "a"
     "foo[8][bar]" "a"}
    {"foo[15][bar]" "a"
     "foo[15][charlie]" "c"}

    "Param name is subset of another param does not get removed"
    {"foofoo[0][bar]" "a"
     "foofoo[1][bar]" "a"}
    {"foofoo[0][bar]" "a"
     "foofoo[1][bar]" "a"}))

(def get-matching-ancestors
  "Var to call private get-matching-ancestors function."
  #'hlh/get-matching-ancestors)

(deftest get-matching-ancestors-test
  (are3 [query-params indexes ancestors-map expected-results]
    (is (= expected-results (get-matching-ancestors "foo" query-params indexes ancestors-map)))

    "Single index two matching ancestors"
    {"foo[0][bar]" "a"
     "foo[0][charlie]" "c"}
    [0]
    {"bar" "a"
     "charlie" "c"}
    {"foo[0][bar]" "a"
     "foo[0][charlie]" "c"}

    "Single index only partial matching ancestors returns empty results"
    {"foo[0][bar]" "a"}
    [0]
    {"bar" "a"
     "charlie" "c"}
    {}

    "Multiple potential indexes, only one fully matching"
    {"foo[0][bar]" "a"
     "foo[0][charlie]" "c"
     "foo[1][bar]" "different"
     "foo[1][charlie]" "c"}
    [0 1]
    {"bar" "a"
     "charlie" "c"}
    {"foo[0][bar]" "a"
     "foo[0][charlie]" "c"}

    "Multiple potential indexes, both fully matching"
    {"foo[0][bar]" "a"
     "foo[0][charlie]" "c"
     "foo[1][bar]" "a"
     "foo[1][charlie]" "c"}
    [0 1]
    {"bar" "a"
     "charlie" "c"}
    {"foo[0][bar]" "a"
     "foo[0][charlie]" "c"
     "foo[1][bar]" "a"
     "foo[1][charlie]" "c"}

    "Case insensitive matches"
    {"foo[0][bar]" "A"}
    [0]
    {"bar" "a"}
    {"foo[0][bar]" "a"}

    "Different index does not find a match"
    {"foo[0][bar]" "a"}
    [99]
    {"bar" "a"}
    {}

    "Different value does not find a match"
    {"foo[0][bar]" "a"}
    [0]
    {"bar" "not-found"}
    {}

    "Nil params are handled"
    nil [] nil {}

    "Multiple scenarios"
    {"foo[0][bar]" "a"
     "foo[0][charlie]" "c"
     "foo[904][bar]" "A"
     "foo[904][charlie]" "C"
     "foo[19][bar]" "different"
     "foo[19][charlie]" "c"}
    [0 19 904]
    {"bar" "a"
     "charlie" "c"}
    {"foo[0][bar]" "a"
     "foo[0][charlie]" "c"
     "foo[904][bar]" "a"
     "foo[904][charlie]" "c"}))

(def generate-query-params
  "Var to call private generate-query-params function."
  #'hlh/generate-query-params)

(deftest generate-query-params-test
  (are3 [indexes tuples expected-results]
    (is (= expected-results (generate-query-params "foo" indexes tuples)))

    "Single index, single tuple"
    [0] [[:bar "b"]]
    {"foo[0][bar]" "b"}

    "Single index, multiple tuples"
    [5] [[:bar "b"]
         [:charlie 7]]
    {"foo[5][bar]" "b"
     "foo[5][charlie]" 7}

    "Multiple indexes, single tuple"
    [0 14] [[:bar "b"]]
    {"foo[0][bar]" "b"
     "foo[14][bar]" "b"}

    "Multiple indexes, multiple tuples"
    [0 14] [[:bar "b"]
            [:charlie 7]]
    {"foo[0][bar]" "b"
     "foo[0][charlie]" 7
     "foo[14][bar]" "b"
     "foo[14][charlie]" 7}

    "Numbers and dashes in subfield"
    [1] [[:variable-level-1 "VL1"]]
    {"foo[1][variable_level_1]" "VL1"}

    "Handles empty indexes"
    [] [[:bar "b"]]
    {}

    "Handles empty tuples"
    [0] []
    {}))
