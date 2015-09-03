(ns cmr.system-int-test.search.json-query-search-test
  "Integration test for JSON Query specific search issues. General JSON query search tests will be
  included in other files by condition."
  (:require [clojure.test :refer :all]
            [cmr.search.services.messages.common-messages :as msg]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.common.util :as util]))

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


  (testing "Concept-id does not support case-insensitive searches"
    (is (= {:status 400
            :errors ["/condition/concept_id instance failed to match exactly one schema (matched 0 out of 2)"
                     "/condition/concept_id instance type (object) does not match any allowed primitive type (allowed: [\"string\"])"
                     "/condition/concept_id object instance has properties which are not allowed by the schema: [\"ignore_case\"]"]}
           (search/find-refs-with-json-query :collection {} {:concept_id {:value "C3-PROV1"
                                                                          :ignore_case true}}))))

  (testing "Invalid NOT cases"
    (are [search errors]
         (= {:status 400 :errors errors}
            (search/find-refs-with-json-query :collection {} search))

         {:not "PROV1"} ["/condition/not instance type (string) does not match any allowed primitive type (allowed: [\"object\"])"]
         {:not {}} ["/condition/not object has too few properties (found 0 but schema requires at least 1)"]))

  (testing "Empty conditions are invalid"
    (is (= {:status 400
            :errors ["/condition object has too few properties (found 0 but schema requires at least 1)"]}
            (search/find-refs-with-json-query :collection {} {}))))

  (testing "Invalid bounding boxes"
    (util/are2
      [search errors]
      (= {:status 400 :errors errors}
         (search/find-refs-with-json-query :collection {} search))

      "Invalid coordinates"
      {:bounding_box [-195, -200, 350, 425]}
      ["/condition/bounding_box instance failed to match exactly one schema (matched 0 out of 2)"
       "/condition/bounding_box/0 numeric instance is lower than the required minimum (minimum: -180, found: -195)"
       "/condition/bounding_box/1 numeric instance is lower than the required minimum (minimum: -180, found: -200)"
       "/condition/bounding_box/2 numeric instance is greater than the required maximum (maximum: 180, found: 350)"
       "/condition/bounding_box/3 numeric instance is greater than the required maximum (maximum: 180, found: 425)"
       "/condition/bounding_box instance type (array) does not match any allowed primitive type (allowed: [\"object\"])"]

      "Only 3 out of 4 coordinates"
      {:bounding_box [-10, -10, 0]}
      ["/condition/bounding_box instance failed to match exactly one schema (matched 0 out of 2)"
       "/condition/bounding_box array is too short: must have at least 4 elements but instance has 3 elements"
       "/condition/bounding_box instance type (array) does not match any allowed primitive type (allowed: [\"object\"])"]

      "Not all numbers"
      {:bounding_box [-10, "foo" [14 7] 0]}
      ["/condition/bounding_box instance failed to match exactly one schema (matched 0 out of 2)"
       "/condition/bounding_box/1 instance type (string) does not match any allowed primitive type (allowed: [\"integer\",\"number\"])"
       "/condition/bounding_box/2 instance type (array) does not match any allowed primitive type (allowed: [\"integer\",\"number\"])"
       "/condition/bounding_box instance type (array) does not match any allowed primitive type (allowed: [\"object\"])"]

      "Missing east coordinate"
      {:bounding_box {:north 10
                      :south 0
                      :west -20}}
      ["/condition/bounding_box instance failed to match exactly one schema (matched 0 out of 2)"
       "/condition/bounding_box instance type (object) does not match any allowed primitive type (allowed: [\"array\"])"
       "/condition/bounding_box object has missing required properties ([\"east\"])"]

      "Extra invalid property"
      {:bounding_box {:north 10
                      :south 0
                      :west -20
                      :east 20
                      :north-east 15}}
      ["/condition/bounding_box instance failed to match exactly one schema (matched 0 out of 2)"
       "/condition/bounding_box instance type (object) does not match any allowed primitive type (allowed: [\"array\"])"
       "/condition/bounding_box object instance has properties which are not allowed by the schema: [\"north-east\"]"]))

  (testing "Invalid science keyword parameters"
    (is (= {:status 400
            :errors ["/condition/science_keywords object instance has properties which are not allowed by the schema: [\"and\",\"not\"]"]}
           (search/find-refs-with-json-query :collection {} {:science_keywords {:category "cat"
                                                                                :and "bad1"
                                                                                :not "bad2"}}))))

  (testing "Science keywords must contain one of the subfields as part of the search"
    (is (= {:status 400
            :errors ["Invalid Science Keywords query condition {:ignore-case true}. Must contain at least one subfield."]}
            (search/find-refs-with-json-query :collection {} {:science_keywords {:ignore_case true}}))))

  (testing "Platform must contain one of the subfields as part of the search"
    (is (= {:status 400
            :errors ["Invalid Platform query condition {:ignore-case true}. Must contain at least one subfield."]}
            (search/find-refs-with-json-query :collection {} {:platform {:ignore_case true}}))))

  (testing "Instrument must contain one of the subfields as part of the search"
    (is (= {:status 400
            :errors ["Invalid Instrument query condition {:ignore-case true}. Must contain at least one subfield."]}
            (search/find-refs-with-json-query :collection {} {:instrument {:ignore_case true}}))))

  (testing "Archive center must contain one of the subfields as part of the search"
    (is (= {:status 400
            :errors ["Invalid Archive Center query condition {:ignore-case true}. Must contain at least one subfield."]}
            (search/find-refs-with-json-query :collection {} {:archive_center {:ignore_case true}})))))


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