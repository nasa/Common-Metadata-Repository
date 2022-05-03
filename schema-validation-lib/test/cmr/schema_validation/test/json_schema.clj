(ns cmr.schema-validation.test.json-schema
  "Tests to verify JSON schema validation."
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.schema-validation.json-schema :as json-schema])
  (:import
   (org.everit.json.schema SchemaException)))

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
    (are [invalid-json errors]
         (= errors
            (json-schema/validate-json sample-json-schema (json/generate-string invalid-json)))

         {"alpha" "omega"}
         ["#: required key [bar] not found"]

         {"bar" true :subfield {"gamma" "ray"}}
         ["#/subfield: required key [zeta] not found"]

         {"alpha" 17 "bar" true}
         ["#/alpha: expected type: String, found: Integer"]

         {"foo" true "bar" true}
         ["#/foo: expected type: String, found: Boolean"
          "#/foo: expected type: Integer, found: Boolean"]

         {"bad-property" "bad-value" "bar" true}
         ["#: extraneous key [bad-property] is not permitted"]))

  (testing "Invalid JSON structure"
    (is (= ["Missing value at 7 [character 8 line 1]"]
           (json-schema/validate-json sample-json-schema "{\"bar\":}")))

    (are [invalid-json]
         (string/includes?
          (json-schema/validate-json sample-json-schema invalid-json)
          "Trailing characters are not permitted.")

         "{}random garbage."

         "{}             random garbage."

         "{}\n\n{}{}[][]random garbage."

         "{}\n\r  random garbage[][][][]"))

  (testing "Valid JSON Structure"
    (are [valid-json]
         (nil? (json-schema/validate-json sample-json-schema valid-json))

         "{\"bar\": false}                     "

         "{\"bar\": false}\n\n\n\n\n\n\n\n\n"

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
