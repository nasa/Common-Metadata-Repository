(ns cmr.common.validations.json-schema
  (:require [cheshire.core :as json])
  (:import com.github.fge.jsonschema.main.JsonSchemaFactory
           com.github.fge.jackson.JsonLoader))

(defn parse-validation-report
  "TODO"
  [report]
  (when-let [error-reports (seq (.asJson report))]
    (flatten (map (fn [error-report]
              (let [json-error (json/decode (.toString error-report))]
                (if-let [report-keys (keys (get json-error "reports"))]
                  (let [one-of-errors (filter #(re-matches #".*oneOf.*" %) report-keys)]
                    (mapcat #(for [error-map (get (get json-error "reports") %)]
                               (str (get (get error-map "instance") "pointer") " "
                                    (get error-map "message")))
                            one-of-errors))
                  (str (get (get json-error "instance") "pointer") " " (get json-error "message")))))
            error-reports))))

#_(defn parse-validation-report
    "TODO"
    [report]
    (when-let [error-reports (seq (.asJson report))]
      (flatten (for [error-report error-reports
                     :let [json-error (json/decode (.toString error-report))]]
                 (if-let [report-keys (keys (get json-error "reports"))]
                   (let [one-of-errors (filter #(re-matches #".*oneOf.*" %) report-keys)]
                     (mapcat #(for [error-map (get (get json-error "reports") %)]
                                (str (get (get error-map "instance") "pointer") " "
                                     (get error-map "message")))
                             one-of-errors))
                   (str (get (get json-error "instance") "pointer") " " (get json-error "message")))))))

(defn validate-against-json-schema
  "Performs schema validation using the provided JSON schema and the given json string to validate.
  Uses com.github.fge.jsonschema to perform the validation."
  [json-schema-str json-to-validate-str]
  (let [json-schema (->> json-schema-str
                         JsonLoader/fromString
                         (.getJsonSchema (JsonSchemaFactory/byDefault)))
        json-to-validate (JsonLoader/fromString (json/generate-string json-to-validate-str))
        validation-report (.validate json-schema json-to-validate)]
    (parse-validation-report validation-report)))

(comment
  (def query-schema (slurp (clojure.java.io/resource "schema/JSONQueryLanguage.json")))
  (validate-against-json-schema query-schema {"provider" {"prov" "PROV1"
                                                          "123" "567"
                                                          "value" "44"}})
  (validate-against-json-schema query-schema {"provider" "PROV1"
                                              "abc" 2})
  (validate-against-json-schema query-schema {"provider" "PROV1"
                                              "abc" 2
                                              [] []})

  (validate-against-json-schema query-schema {"provider" "PROV1"})

  (def successful (cmr.common.dev.capture-reveal/reveal validation-report))
  (def failure (cmr.common.dev.capture-reveal/reveal validation-report))
  )