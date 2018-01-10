(ns cmr.common.test.validations.json-schema
  "Tests to verify JSON schema validation."
  (:require [clojure.test :refer :all]
            [cheshire.core :as json]
            [cmr.common.validations.json-schema :as js]
            [cmr.common.test.test-util :as tu]
            [cmr.common.util :as u])
  (:import com.github.fge.jsonschema.core.exceptions.InvalidSchemaException
           clojure.lang.ExceptionInfo))

(def sample-json-schema
  "Schema to test validation against"
  (js/parse-json-schema
    {:type :object
     :additionalProperties false
     :properties {:foo {:oneOf [{:type :string}
                                {:type :integer}]}
                  :bar {:type :boolean}
                  :alpha {:type :string}
                  :subfield {:$ref "#/definitions/omega"}}
     :required [:bar]
     :definitions {:omega {:type :object
                           :properties {:zeta {:type :integer}}
                           :required [:zeta]}}}))

(comment

  ;; This is handy for trying out different capabilities of JSON schema.

  (js/validate-json
    (js/parse-json-schema
      {:definitions {:omega {:type "object"
                             :properties {:a {:type :integer}
                                          :b {:type :integer}
                                          :c {:type :integer}}
                             :oneOf [{:required [:a]}
                                     {:required [:b]}
                                     {:required [:c]}]}}
       :type "object"
       :additionalProperties false
       :properties {:omega {"$ref" "#/definitions/omega"}}
       :required ["omega"]})

    (json/generate-string {:omega {:b 2}})))




(deftest validate-json-test
  (testing "Valid json"
    (are [json]
         (nil? (seq (js/validate-json sample-json-schema (json/generate-string json))))

         {"bar" true}
         {"bar" true :subfield {"zeta" 123 "gamma" "ray"}}))

  (testing "Validation failures"
    (u/are2 [invalid-json errors]
            (= errors
               (js/validate-json sample-json-schema (json/generate-string invalid-json)))

            "Missing required property"
            {"alpha" "omega"}
            ["object has missing required properties ([\"bar\"])"]

            "Missing required property from subfield"
            {"bar" true :subfield {"gamma" "ray"}}
            ["/subfield object has missing required properties ([\"zeta\"])"]

            "Wrong type for property with single type"
            {"alpha" 17 "bar" true}
            ["/alpha instance type (integer) does not match any allowed primitive type (allowed: [\"string\"])"]

            "Wrong type for property which can have multiple types"
            {"foo" true "bar" true}
            ["/foo instance failed to match exactly one schema (matched 0 out of 2)"
             "/foo instance type (boolean) does not match any allowed primitive type (allowed: [\"string\"])"
             "/foo instance type (boolean) does not match any allowed primitive type (allowed: [\"integer\"])"]

            "Unknown property"
            {"bad-property" "bad-value" "bar" true}
            ["object instance has properties which are not allowed by the schema: [\"bad-property\"]"]))

  (testing "Invalid JSON structure"
    (is (thrown-with-msg?
          ExceptionInfo
          #"Invalid JSON: Unexpected character \('\}' \(code 125\)\):"
          (js/validate-json sample-json-schema "{\"bar\":}"))))

  (testing "Invalid schema - description cannot be an array"
    (is (thrown-with-msg?
          InvalidSchemaException
          #"value has incorrect type \(found array, expected one of \[string\]\)"
          (js/validate-json
            (js/parse-json-schema
              {"title" "The title"
               "description" ["A description" "B description"]})
            "{}")))))
