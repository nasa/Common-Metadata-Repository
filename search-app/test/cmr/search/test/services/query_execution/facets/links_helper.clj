(ns cmr.search.test.services.query-execution.facets.links-helper
  "Unit tests for facets links helper namespace."
  (:require [clojure.test :refer :all]
            [cmr.search.services.query-execution.facets.links-helper :as lh]
            [cmr.common.util :refer [are3]]
            [camel-snake-kebab.core :as csk]
            [clojure.set :as set]
            [clojure.string :as str]))

(def base-url
  "Base URL for each request."
  "http://localhost:3003/collections.json")

(deftest apply-link-test
  (let [param-name "foo"
        term "bar"]
    (are3 [query-params expected-result]
      (is (= expected-result (lh/create-link base-url query-params param-name term)))

      "Apply link with multiple existing values"
      {"foo[]" ["cat" "dog"]}
      {:apply "http://localhost:3003/collections.json?foo%5B%5D=bar&foo%5B%5D=cat&foo%5B%5D=dog"}

      "Apply link with single existing value"
      {"foo[]" "alpha"}
      {:apply "http://localhost:3003/collections.json?foo%5B%5D=bar&foo%5B%5D=alpha"}

      "Apply link without any existing values"
      {}
      {:apply "http://localhost:3003/collections.json?foo%5B%5D=bar"}

      "Apply link with other params"
      {"charlie[]" "delta"}
      {:apply "http://localhost:3003/collections.json?charlie%5B%5D=delta&foo%5B%5D=bar"})

    (testing "Apply link when value is already applied"
      (let [query-params {"foo[]" "bar"}]
        (is (= {:apply "http://localhost:3003/collections.json?foo%5B%5D=bar&foo%5B%5D=bar"}
               (lh/create-apply-link base-url query-params param-name term)))))))

(deftest remove-link-test
  (let [param-name "foo"
        term "bar"]
    (are3 [query-params expected-result]
      (is (= expected-result (lh/create-link base-url query-params param-name term)))

      "Remove link with multiple existing values"
      {"foo[]" ["alpha" "bar"]}
      {:remove "http://localhost:3003/collections.json?foo%5B%5D=alpha"}

      "Remove link without any other values"
      {"foo[]" "bar"}
      {:remove "http://localhost:3003/collections.json"}

      "Remove link with other params"
      {"charlie[]" "delta" "foo[]" "bar"}
      {:remove "http://localhost:3003/collections.json?charlie%5B%5D=delta"}

      "Remove link referenced multiple times"
      {"charlie[]" "delta" "foo[]" ["bar" "soap" "bar"]}
      {:remove "http://localhost:3003/collections.json?charlie%5B%5D=delta&foo%5B%5D=soap"}

      "Remove link referenced as single param rather than array"
      {"foo" "bar"}
      {:remove "http://localhost:3003/collections.json"})

    (testing "Remove link when value does not exist creates remove link without any change"
      (is (= {:remove "http://localhost:3003/collections.json"}
             (lh/create-remove-link base-url {} param-name term))))
    (testing "Terms are matched case insensitively"
      (let [query-params {"foo" "bar"}
            term "BAR"]
        (is (= {:remove "http://localhost:3003/collections.json"}
               (lh/create-link base-url query-params param-name term)))))))

(deftest apply-hierarchical-link-test
  (let [param-name "science_keywords[0][topic]"
        term "BIOMASS"]
    (are3 [query-params expected-result]
       (is (= expected-result (lh/create-link-for-hierarchical-field
                               base-url query-params param-name term nil)))

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
              (lh/create-link-for-hierarchical-field
                base-url (or query-params default-query-params) param-name term nil)))

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
          result (lh/create-link-for-hierarchical-field base-url query-params param-name term nil)]
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
          result (lh/create-link-for-hierarchical-field base-url query-params param-name term
                                                        other-params)]
      (is (= expected-result result)))))

(deftest non-ascii-character-test
  (let [query-params {"foo" "El niño"
                      "umlaut" "Ü"}
        term "El niño"
        param-name "foo"
        expected-result {:remove "http://localhost:3003/collections.json?umlaut=%C3%9C"}
        result (lh/create-link base-url query-params param-name term)]
    (is (= expected-result result))))

(deftest and-options-test
  (doseq [field [:platform-h :instrument-h :organization-h :project-h :processing-level-id-h]]
    (testing (str "Testing AND options of " field)
      (are3 [values and-option search-term link]
        (let [snake-case-field (csk/->snake_case_string field)
              and-option-map (when-not (nil? and-option)
                              {(str "options[" snake-case-field "][and]") and-option})
              query-params (merge {(str snake-case-field "[]") values} and-option-map)
              expected-url (str/replace (first (vals link)) #"placeholder" snake-case-field)]
          (is (= {(first (keys link)) expected-url}
                (lh/create-link base-url query-params field search-term))))

        "Multiple search terms are AND'ed by default for apply links"
        ["p1" "p2"]
        nil
        "p4"
        {:apply (str "http://localhost:3003/collections.json?placeholder%5B%5D=p4&"
                     "placeholder%5B%5D=p1&placeholder%5B%5D=p2&"
                     "options%5Bplaceholder%5D%5Band%5D=true")}

        "Multiple search terms are AND'ed by default for remove links"
        ["p1" "p2" "p3"]
        nil
        "p3"
        {:remove (str "http://localhost:3003/collections.json?placeholder%5B%5D=p1&"
                      "placeholder%5B%5D=p2&options%5Bplaceholder%5D%5Band%5D=true")}

        "When explicitly requesting OR, apply links specify OR"
        ["p1" "p2"]
        false
        "p4"
        {:apply (str "http://localhost:3003/collections.json?placeholder%5B%5D=p4&"
                     "placeholder%5B%5D=p1&placeholder%5B%5D=p2&"
                     "options%5Bplaceholder%5D%5Band%5D=false")}

        "When explicitly requesting OR, remove links specify OR"
        ["p1" "p2" "p3"]
        false
        "p3"
        {:remove (str "http://localhost:3003/collections.json?placeholder%5B%5D=p1&"
                      "placeholder%5B%5D=p2&options%5Bplaceholder%5D%5Band%5D=false")}

       "AND=true option is not included in remove links when only a single term is left"
       ["p1" "p2"]
       true
       "p2"
       {:remove "http://localhost:3003/collections.json?placeholder%5B%5D=p1"}

       "AND=false option is not included in remove links when only a single term is left"
       ["p1" "p2"]
       false
       "p2"
       {:remove "http://localhost:3003/collections.json?placeholder%5B%5D=p1"}))))

(def get-max-index-for-field-name
  "Var to call private function in links helper namespace."
  #'lh/get-max-index-for-field-name)

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
