(ns cmr.system-int-test.search.json-query-search-test
  "Integration test for JSON Query specific search issues. General JSON query search tests will be
  included in other files by condition."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util]
   [cmr.system-int-test.utils.search-util :as search]))

(deftest validation-test
  (testing "Invalid JSON condition names"
    (are [concept-type search-map error-message]
         (= {:status 400 :errors error-message}
            (search/find-refs-with-json-query concept-type {} search-map))
         :collection {:foo "bar"}
         ["#/condition: extraneous key [foo] is not permitted"]

         :collection {:not {:or [{:provider "PROV1"} {:not-right {:another-bad-name "123"}}]}}
         ["#/condition/not/or/1: extraneous key [not-right] is not permitted"]

         :granule {:provider "PROV1"}
         ["Searching using JSON query conditions is not supported for granules."]))


  (testing "Concept-id does not support case-insensitive searches"
    (is (= {:status 400
            :errors ["#/condition/concept_id: expected type: String, found: JSONObject"
                     "#/condition/concept_id: extraneous key [ignore_case] is not permitted"]}
           (search/find-refs-with-json-query :collection {} {:concept_id {:value "C3-PROV1"
                                                                          :ignore_case true}}))))

  (testing "Invalid NOT cases"
    (are [search errors]
         (= {:status 400 :errors errors}
            (search/find-refs-with-json-query :collection {} search))

         {:not "PROV1"} ["#/condition/not: expected type: JSONObject, found: String"]
         {:not {}} ["#/condition/not: minimum size: [1], found: [0]"]))

  (testing "Empty conditions are invalid"
    (is (= {:status 400
            :errors ["#/condition: minimum size: [1], found: [0]"]}
            (search/find-refs-with-json-query :collection {} {}))))

  (testing "Invalid bounding boxes"
    (util/are2
      [search errors]
      (= {:status 400 :errors errors}
         (search/find-refs-with-json-query :collection {} search))

      "Invalid coordinates"
      {:bounding_box [-195, -200, 350, 425]}
      ["#/condition/bounding_box: expected type: JSONObject, found: JSONArray"
       "#/condition/bounding_box/0: -195 is not greater or equal to -180"
       "#/condition/bounding_box/1: -200 is not greater or equal to -180"
       "#/condition/bounding_box/2: 350 is not less or equal to 180"
       "#/condition/bounding_box/3: 425 is not less or equal to 180"]

      "Only 3 out of 4 coordinates"
      {:bounding_box [-10, -10, 0]}
      ["#/condition/bounding_box: expected minimum item count: 4, found: 3"
       "#/condition/bounding_box: expected type: JSONObject, found: JSONArray"]

      "Not all numbers"
      {:bounding_box [-10, "foo" [14 7] 0]}
      ["#/condition/bounding_box: expected type: JSONObject, found: JSONArray"
       "#/condition/bounding_box/1: expected type: Number, found: String"
       "#/condition/bounding_box/2: expected type: Number, found: JSONArray"]

      "Missing east coordinate"
      {:bounding_box {:north 10
                      :south 0
                      :west -20}}
      ["#/condition/bounding_box: expected type: JSONArray, found: JSONObject"
       "#/condition/bounding_box: required key [east] not found"]

      "Extra invalid property"
      {:bounding_box {:north 10
                      :south 0
                      :west -20
                      :east 20
                      :north-east 15}}
      ["#/condition/bounding_box: expected type: JSONArray, found: JSONObject"
       "#/condition/bounding_box: extraneous key [north-east] is not permitted"]))

  (testing "Invalid science keyword parameters"
    (is (= {:status 400
            :errors ["#/condition/science_keywords: extraneous key [not] is not permitted"
                     "#/condition/science_keywords: extraneous key [and] is not permitted"]}
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
