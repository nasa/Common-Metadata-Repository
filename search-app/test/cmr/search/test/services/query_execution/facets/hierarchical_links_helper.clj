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
                               base-url query-params param-name nil nil term false nil)))

       "Apply link to empty params"
       {"foo" "bar"}
       {:apply (str "http://localhost:3003/collections.json?foo=bar&"
                    "science_keywords%5B0%5D%5Btopic%5D=BIOMASS")}

       "Apply link causes index to be increased by one"
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
                     "science_keywords%5B2%5D%5Btopic%5D=BIOMASS")})))

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
          result (hlh/create-link-for-hierarchical-field base-url query-params param-name nil nil
                                                        term false nil)]
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
      (is (= expected-result result)))))

(def get-max-index-for-field-name
  "Var to call private function in links helper namespace."
  #'hlh/get-max-index-for-field-name)

(deftest get-max-index-for-field-name-test
  (are3 [query-params expected-index]
    (is (= expected-index (get-max-index-for-field-name query-params "foo")))

    "Nil params returns -1"
    nil -1

    "Empty params returns -1"
    {} -1

    "Param not in query-params returns -1"
    {"a" true
     "b[1][c]" "abc"} -1

    "Single matching param"
    {"foo[0][a]" true} 0

    "Double digit index"
    {"foo[14][a]" true} 14

    "Large index"
    {"foo[1234567890][a]" true} 1234567890

    "Multiple matching finds largest"
    {"foo[0][a]" false
     "foo[5][a]" 1
     "foo[12][b]" "str"
     "foo[99][a]" true
     "foo[151][c]" "c"
     "foo[140][a]" 15}
    151

    "Similar query params finds correct largest term index"
    {"foo[0][a]" false
     "bar[550][foo]" 1
     "foo[12][b]" "str"
     "afoo[99][a]" true
     "food[151][c]" "c"
     "foofoo[140][a]" 15}
    12))

;; TODO Add a bunch of tests to verify the new changes to the create links functions
