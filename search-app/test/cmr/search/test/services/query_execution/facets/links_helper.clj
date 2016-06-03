(ns cmr.search.test.services.query-execution.facets.links-helper
  "Unit tests for facets links helper namespace."
  (:require [clojure.test :refer :all]
            [cmr.search.services.query-execution.facets.links-helper :as lh]))


;; TODO Add tests
;; Multiple science keywords or'ed together and and-ed together - each of the subfields in the hierarchy
;; Apply links for each of the different types
;; Remove links for each of the different types
;; Empty facets
;; Results where some facets are present and some are not

(def base-url
  "Base URL for each request."
  "http://localhost:3003/collections.json")

(deftest apply-science-keyword-link
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
        result (lh/create-hierarchical-apply-link base-url query-params param-name term)]
    (is (= expected-result result))))

(deftest remove-science-keyword-link
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
            result (lh/create-hierarchical-remove-link base-url query-params param-name term)]
        (is (= expected-result result))))
    (testing "Different index for term being matched"
      (let [term "ATMOSPHERE"
            expected-result {:remove (str "http://localhost:3003/collections.json?foo=bar&"
                                          "science_keywords%5B0%5D%5Bcategory%5D=EARTH+SCIENCE&"
                                          "science_keywords%5B0%5D%5Btopic%5D=AGRICULTURE")}
            result (lh/create-hierarchical-remove-link base-url query-params param-name term)]
        (is (= expected-result result))))))
    ; (testing "Multiple values for the term to be removed"
    ;   (let [query-params (assoc query-params "science_keywords[1][topic]" ["ATMOSPHERE" "BIOMASS"])
    ;         term "ATMOSPHERE"
    ;         expected-result {:remove (str "http://localhost:3003/collections.json?foo=bar&"
    ;                                       "science_keywords%5B0%5D%5Bcategory%5D=EARTH+SCIENCE&"
    ;                                       "science_keywords%5B1%5D%5Btopic%5D=BIOMASS"
    ;                                       "science_keywords%5B0%5D%5Btopic%5D=AGRICULTURE")}
    ;         result (lh/create-hierarchical-remove-link base-url query-params param-name term)]
    ;     (is (= expected-result result))))))
