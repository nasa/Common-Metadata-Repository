(ns cmr.search.test.services.query-execution.facets.links-helper
  "Unit tests for facets links helper namespace."
  (:require [clojure.test :refer :all]
            [cmr.search.services.query-execution.facets.links-helper :as lh]
            [cmr.common.util :as util]))

(def base-url
  "Base URL for each request."
  "http://localhost:3003/collections.json")

(deftest apply-link-test
  (let [param-name "foo"
        term "bar"]
    (util/are2 [query-params expected-result]
               (= expected-result (lh/create-link base-url query-params param-name term))

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
    (util/are2 [query-params expected-result]
               (= expected-result (lh/create-link base-url query-params param-name term))

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
               (lh/create-link base-url query-params param-name term))))))

  (deftest apply-hierarchical-link-test
    (let [param-name "science_keywords[0][topic]"
          term "BIOMASS"]
      (util/are2 [query-params expected-result]
                 (is (= expected-result (lh/create-link-for-hierarchical-field
                                         base-url query-params param-name term)))

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
                               "science_keywords%5B2%5D%5Btopic%5D=BIOMASS")}))))

(deftest remove-hierarchical-link-test
  (let [param-name "science_keywords[0][topic]"
        default-query-params {"foo" "bar"
                              "science_keywords[0][category]" "EARTH SCIENCE"
                              "science_keywords[0][topic]" "AGRICULTURE"
                              "science_keywords[1][topic]" "ATMOSPHERE"}]
    (util/are2 [query-params term expected-result]
               (is (= expected-result
                      (lh/create-link-for-hierarchical-field
                        base-url (or query-params default-query-params) param-name term)))

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
          result (lh/create-link-for-hierarchical-field base-url query-params param-name term)]
      (is (= expected-result result)))))

(deftest non-ascii-character-test
  (let [query-params {"foo" "El niño"
                      "umlaut" "Ü"}
        term "El niño"
        param-name "foo"
        expected-result {:remove "http://localhost:3003/collections.json?umlaut=%C3%9C"}
        result (lh/create-link base-url query-params param-name term)]
    (is (= expected-result result))))
