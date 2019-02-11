(ns cmr.common.test.validations.json-schema
  "Tests to verify JSON schema validation."
  (:require [clojure.test :refer :all]
            [cheshire.core :as json]
            [cmr.common.validations.json-schema :as json-schema]
            [cmr.common.util :as util])
  (:import org.everit.json.schema.SchemaException
           clojure.lang.ExceptionInfo))

(def sample-json-schema
  "Schema to test validation against"
  (json-schema/parse-json-schema
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

  (json-schema/validate-json
    (json-schema/parse-json-schema
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
         (nil? (seq (json-schema/validate-json sample-json-schema (json/generate-string json))))

         {"bar" true}
         {"bar" true :subfield {"zeta" 123 "gamma" "ray"}}))

  (testing "Validation failures"
    (util/are2 [invalid-json errors]
               (= errors
                  (json-schema/validate-json sample-json-schema (json/generate-string invalid-json)))

               "Missing required property"
               {"alpha" "omega"}
               ["#: required key [bar] not found"]

               "Missing required property from subfield"
               {"bar" true :subfield {"gamma" "ray"}}
               ["#/subfield: required key [zeta] not found"]

               "Wrong type for property with single type"
               {"alpha" 17 "bar" true}
               ["#/alpha: expected type: String, found: Integer"]

               "Wrong type for property which can have multiple types"
               {"foo" true "bar" true}
               ["#/foo: expected type: String, found: Boolean"
                "#/foo: expected type: Number, found: Boolean"]

               "Unknown property"
               {"bad-property" "bad-value" "bar" true}
               ["#: extraneous key [bad-property] is not permitted"]))

  (testing "Invalid JSON structure"
    (is (thrown-with-msg?
          ExceptionInfo
          #"Invalid JSON: Missing value at 7 \[character 8 line 1\]"
          (json-schema/validate-json sample-json-schema "{\"bar\":}")))

    (util/are3 [invalid-json]
               (is (thrown-with-msg?
                    ExceptionInfo
                    #"Invalid JSON: Trailing characters are not permitted\."
                    (json-schema/validate-json sample-json-schema invalid-json)))

               "No new line or trailing space"
               "{}random garbage."

               "With trailing spaces"
               "{}             random garbage."

               "With trailing new lines"
               "{}\n\n{}{}[][]random garbage."

               "Combination of spaces and newlines"
               "{}\n\r  random garbage[][][][]"))


  (testing "Valid JSON Structure"
    (util/are3 [valid-json]
               (is (nil? (json-schema/validate-json sample-json-schema valid-json)))

               "Valid JSON with trailing spaces"
               "{\"bar\": false}                     "

               "Valid JSON With trailing new lines"
               "{\"bar\": false}\n\n\n\n\n\n\n\n\n"

               "Valid JSON with combination of new lines, spaces, tabs"
               "{\"bar\": true}\t\n\t\t\n\r\r       \r\n\t\t\n\n\t\n"))


  (testing "Invalid schema - description cannot be an array"
    (is (thrown-with-msg?
          SchemaException
          #"#/description: expected type: String, found: JsonArray"
          (json-schema/validate-json
            (json-schema/parse-json-schema
              {"title" "The title"
               "description" ["A description" "B description"]})
            "{}")))))
