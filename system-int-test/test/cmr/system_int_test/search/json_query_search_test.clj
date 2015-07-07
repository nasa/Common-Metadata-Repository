(ns cmr.system-int-test.search.json-query-search-test
  "Integration test for JSON Query specific search issues. General JSON query search tests will be
  included in other files by condition."
  (:require [clojure.test :refer :all]
            [cmr.search.services.messages.common-messages :as msg]
            [cmr.system-int-test.utils.search-util :as search]))

(deftest validation-test
  (testing "Invalid JSON condition names"
    (are [concept-type search-map error-message]
         (= {:status 400 :errors error-message}
            (search/find-refs-with-json-query concept-type {} search-map))
         :collection {:foo "bar"}
         ["/condition object instance has properties which are not allowed by the schema: [\"foo\"]"]

         :collection {:not {:or [{:provider "PROV1"} {:not-right {:another-bad-name "123"}}]}}
         ["/condition/not/or/1 object instance has properties which are not allowed by the schema: [\"not-right\"]"]

         :granule {:provider "PROV1"}
         ["Searching using JSON query conditions is not supported for granules."]))

  (testing "Invalid science keyword parameters"
    (is (= {:status 400
            :errors ["/condition/science_keywords object instance has properties which are not allowed by the schema: [\"and\",\"not\"]"]}
           (search/find-refs-with-json-query :collection {} {:science_keywords {:category "cat"
                                                                                :and "bad1"
                                                                                :not "bad2"}}))))

  (testing "Concept-id does not support case-insensitive searches"
    (is (= {:status 400
            :errors ["/condition/concept_id instance failed to match exactly one schema (matched 0 out of 2)"
                     "/condition/concept_id instance type (object) does not match any allowed primitive type (allowed: [\"string\"])"
                     "/condition/concept_id object instance has properties which are not allowed by the schema: [\"ignore_case\"]"]}
           (search/find-refs-with-json-query :collection {} {:concept_id {:value "C3-PROV1"
                                                                          :ignore_case true}})))))

(comment
  (def query-schema (slurp (clojure.java.io/resource "schema/JSONQueryLanguage.json")))
  (use 'cmr.common.validations.json-schema)
  (perform-validations query-schema {"provider" {"prov" "PROV1"
                                                 "123" "567"
                                                 "value" "44"}})

  (perform-validations query-schema {"and" [{"provider" {"prov" "PROV1"
                                                         "123" "567"
                                                         "value" "44"}
                                             "bad" "key"}]})

  (perform-validations query-schema "")

  )