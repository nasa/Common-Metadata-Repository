(ns cmr.search.test.services.query-execution.facets.links-helper
  "Unit tests for facets links helper namespace."
  (:require [clojure.test :refer :all]
            [cmr.search.services.query-execution.facets.links-helper :as lh]))

;; TODO Add tests
;; Apply links for each of the different types
;; Remove links for each of the different types
;; Empty facets
;; Results where some facets are present and some are not

(def base-url
  "Base URL for each request."
  "http://localhost:3003/collections.json")

(deftest apply-link-test
  (let [param-name "foo"
        term "bar"]
    (testing "Apply link with multiple existing values"
      (let [query-params {"foo[]" ["alpha" "bravo"]}
            expected-result {:apply (str "http://localhost:3003/collections.json?"
                                         "foo%5B%5D=bar&foo%5B%5D=alpha&foo%5B%5D=bravo")}
            result (lh/create-apply-link base-url query-params param-name term)
            [type result2] (lh/create-links base-url query-params param-name term)]
        (is (= :apply type))
        (is (= expected-result result))
        (is (= expected-result result2))))
    (testing "Apply link with single existing value"
      (let [query-params {"foo[]" "alpha"}
            expected-result {:apply (str "http://localhost:3003/collections.json?"
                                         "foo%5B%5D=bar&foo%5B%5D=alpha")}
            result (lh/create-apply-link base-url query-params param-name term)
            [type result2] (lh/create-links base-url query-params param-name term)]
        (is (= :apply type))
        (is (= expected-result result))
        (is (= expected-result result2))))
    (testing "Apply link when value is already applied"
      (let [query-params {"foo[]" "bar"}
            expected-result {:apply (str "http://localhost:3003/collections.json?"
                                         "foo%5B%5D=bar&foo%5B%5D=bar")}
            result (lh/create-apply-link base-url query-params param-name term)
            [type result2] (lh/create-links base-url query-params param-name term)]
        (is (= :remove type))
        (is (= expected-result result))
        (is (= {:remove "http://localhost:3003/collections.json"} result2))))
    (testing "Apply link without any existing values"
      (let [query-params {}
            expected-result {:apply (str "http://localhost:3003/collections.json?"
                                         "foo%5B%5D=bar")}
            result (lh/create-apply-link base-url query-params param-name term)
            [type result2] (lh/create-links base-url query-params param-name term)]
        (is (= :apply type))
        (is (= expected-result result))
        (is (= expected-result result2))))
    (testing "Apply link with other params"
      (let [query-params {"charlie[]" "delta"}
            expected-result {:apply (str "http://localhost:3003/collections.json?"
                                         "charlie%5B%5D=delta&foo%5B%5D=bar")}
            result (lh/create-apply-link base-url query-params param-name term)
            [type result2] (lh/create-links base-url query-params param-name term)]
        (is (= :apply type))
        (is (= expected-result result))
        (is (= expected-result result2))))))

(deftest remove-link-test
  (let [param-name "foo"
        term "bar"]
    (testing "Remove link with multiple existing values"
      (let [query-params {"foo[]" ["alpha" "bar"]}
            expected-result {:remove (str "http://localhost:3003/collections.json?"
                                          "foo%5B%5D=alpha")}
            result (lh/create-remove-link base-url query-params param-name term)
            [type result2] (lh/create-links base-url query-params param-name term)]
        (is (= :remove type))
        (is (= expected-result result))
        (is (= expected-result result2))))
    (testing "Remove link without any other values"
      (let [query-params {"foo[]" "bar"}
            expected-result {:remove "http://localhost:3003/collections.json"}
            result (lh/create-remove-link base-url query-params param-name term)
            [type result2] (lh/create-links base-url query-params param-name term)]
        (is (= :remove type))
        (is (= expected-result result))
        (is (= expected-result result2))))
    (testing "Remove link when value does not exist creates remove link without any change"
      (let [query-params {}
            expected-result {:remove (str "http://localhost:3003/collections.json")}
            result (lh/create-remove-link base-url query-params param-name term)]
        (is (= expected-result result))))
    (testing "Remove link with other params"
      (let [query-params {"charlie[]" "delta"
                          "foo[]" "bar"}
            expected-result {:remove (str "http://localhost:3003/collections.json?"
                                          "charlie%5B%5D=delta")}
            result (lh/create-remove-link base-url query-params param-name term)
            [type result2] (lh/create-links base-url query-params param-name term)]
        (is (= :remove type))
        (is (= expected-result result))
        (is (= expected-result result2))))
    (testing "Remove link referenced multiple times"
      (let [query-params {"charlie[]" "delta"
                          "foo[]" ["bar" "soap" "bar"]}
            expected-result {:remove (str "http://localhost:3003/collections.json?"
                                          "charlie%5B%5D=delta&"
                                          "foo%5B%5D=soap")}
            result (lh/create-remove-link base-url query-params param-name term)
            [type result2] (lh/create-links base-url query-params param-name term)]
        (is (= :remove type))
        (is (= expected-result result))
        (is (= expected-result result2))))
    (testing "Remove link referenced as single param rather than array"
      (let [query-params {"foo" "bar"}
            expected-result {:remove "http://localhost:3003/collections.json"}
            result (lh/create-remove-link base-url query-params param-name term)
            [type result2] (lh/create-links base-url query-params param-name term)]
        (is (= :remove type))
        (is (= expected-result result))
        (is (= expected-result result2))))))

(deftest apply-hierarchical-link-test
  (testing "Apply link to empty params"
    (let [query-params {"foo" "bar"}
          param-name "science_keywords[0][topic]"
          term "BIOMASS"
          expected-result {:apply (str "http://localhost:3003/collections.json?foo=bar&"
                                       "science_keywords%5B0%5D%5Btopic%5D=BIOMASS")}
          result (lh/create-hierarchical-apply-link base-url query-params param-name term)
          [type result2] (lh/create-hierarchical-links base-url query-params param-name term)]
      (is (= :apply type))
      (is (= expected-result result))
      (is (= expected-result result2))))
  (testing "Apply link causes index to be increased by one"
    (let [query-params {"foo" "bar"
                        "science_keywords[0][category]" "EARTH SCIENCE"
                        "science_keywords[0][topic]" "AGRICULTURE"
                        "science_keywords[1][topic]" "ATMOSPHERE"}
          param-name "science_keywords[0][topic]"
          term "BIOMASS"
          expected-result {:apply (str "http://localhost:3003/collections.json?foo=bar&"
                                       "science_keywords%5B0%5D%5Bcategory%5D=EARTH+SCIENCE&"
                                       "science_keywords%5B0%5D%5Btopic%5D=AGRICULTURE&"
                                       "science_keywords%5B1%5D%5Btopic%5D=ATMOSPHERE&"
                                       "science_keywords%5B2%5D%5Btopic%5D=BIOMASS")}
          result (lh/create-hierarchical-apply-link base-url query-params param-name term)
          [type result2] (lh/create-hierarchical-links base-url query-params param-name term)]
      (is (= :apply type))
      (is (= expected-result result))
      (is (= expected-result result2)))))


(deftest remove-hierarchical-link-test
  (let [query-params {"foo" "bar"
                      "science_keywords[0][category]" "EARTH SCIENCE"
                      "science_keywords[0][topic]" "AGRICULTURE"
                      "science_keywords[1][topic]" "ATMOSPHERE"}
        param-name "science_keywords[0][topic]"]
    (testing "Same index as term being matched"
      (let [term "AGRICULTURE"
            expected-result {:remove (str "http://localhost:3003/collections.json?foo=bar&"
                                          "science_keywords%5B0%5D%5Bcategory%5D=EARTH+SCIENCE&"
                                          "science_keywords%5B1%5D%5Btopic%5D=ATMOSPHERE")}
            result (lh/create-hierarchical-remove-link base-url query-params param-name term)
            [type result2] (lh/create-hierarchical-links base-url query-params param-name term)]
        (is (= :remove type))
        (is (= expected-result result))
        (is (= expected-result result2))))
    (testing "Different index param name being matched: science_keywords[1] vs. science_keywords[0]"
      (let [term "ATMOSPHERE"
            expected-result {:remove (str "http://localhost:3003/collections.json?foo=bar&"
                                          "science_keywords%5B0%5D%5Bcategory%5D=EARTH+SCIENCE&"
                                          "science_keywords%5B0%5D%5Btopic%5D=AGRICULTURE")}
            result (lh/create-hierarchical-remove-link base-url query-params param-name term)
            [type result2] (lh/create-hierarchical-links base-url query-params param-name term)]
        (is (= :remove type))
        (is (= expected-result result))
        (is (= expected-result result2))))
    (testing "Multiple values for the term to be removed"
      (let [query-params (assoc query-params "science_keywords[1][topic]" ["ATMOSPHERE" "BIOMASS"])
            term "ATMOSPHERE"
            expected-result {:remove (str "http://localhost:3003/collections.json?foo=bar&"
                                          "science_keywords%5B0%5D%5Bcategory%5D=EARTH+SCIENCE&"
                                          "science_keywords%5B0%5D%5Btopic%5D=AGRICULTURE&"
                                          "science_keywords%5B1%5D%5Btopic%5D=BIOMASS")}
            result (lh/create-hierarchical-remove-link base-url query-params param-name term)
            [type result2] (lh/create-hierarchical-links base-url query-params param-name term)]
        (is (= :remove type))
        (is (= expected-result result))
        (is (= expected-result result2))))
    (testing "Term being removed is referenced multiple times"
      (let [query-params (assoc query-params "science_keywords[1][topic]" ["ATMOSPHERE" "ATMOSPHERE"])
            term "ATMOSPHERE"
            expected-result {:remove (str "http://localhost:3003/collections.json?foo=bar&"
                                          "science_keywords%5B0%5D%5Bcategory%5D=EARTH+SCIENCE&"
                                          "science_keywords%5B0%5D%5Btopic%5D=AGRICULTURE")}
            result (lh/create-hierarchical-remove-link base-url query-params param-name term)
            [type result2] (lh/create-hierarchical-links base-url query-params param-name term)]
        (is (= :remove type))
        (is (= expected-result result))
        (is (= expected-result result2)))))
  (testing "Remove all links"
    (let [query-params {"science_keywords[0][category]" "EARTH SCIENCE"}
          term "Earth Science"
          param-name "science_keywords[0][category]"
          expected-result {:remove (str "http://localhost:3003/collections.json")}
          result (lh/create-hierarchical-remove-link base-url query-params param-name term)
          [type result2] (lh/create-hierarchical-links base-url query-params param-name term)]
      (is (= :remove type))
      (is (= expected-result result))
      (is (= expected-result result2)))))
