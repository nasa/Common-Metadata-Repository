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

(deftest non-ascii-character-test
  (let [query-params {"foo" "El niño"
                      "umlaut" "Ü"}
        term "El niño"
        param-name "foo"
        expected-result {:remove "http://localhost:3003/collections.json?umlaut=%C3%9C"}
        result (lh/create-link base-url query-params param-name term)]
    (is (= expected-result result))))

(deftest and-options-test
  (doseq [field [:platform-h :instrument-h :data-center-h :project-h :processing-level-id-h]]
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
