(ns cmr.umm-spec.parse-gen
  "TODO"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [cheshire.core :as json]
            [cheshire.factory :as factory]
            [cmr.umm-spec.models.common :as cmn]
            [cmr.umm-spec.models.collection :as umm-c]
            [clojure.data.xml :as x]
            [cmr.common.util :as util]
            [cmr.umm-spec.simple-xpath :as sxp]
            [cmr.umm-spec.json-schema :as js]))

;; TODO define json schema for mappings

(def echo10-mappings (io/resource "echo10-mappings.json"))

(defn load-mappings
  "TODO"
  [mappings-resource]
  (binding [factory/*json-factory* (factory/make-json-factory
                                     {:allow-comments true})]
    (json/decode (slurp mappings-resource) true)))


(defmulti add-parse-type
  "TODO"
  (fn [schema type-name schema-type mapping-type]
    (cond
      (:type schema-type) (:type schema-type)
      (:$ref schema-type) :$ref

      ;; Allow default to fall through and find unaccounted for implementations

      )))

(defmethod add-parse-type :default
  [schema type-name schema-type mapping-type]
  (throw (Exception. (str "No method for " (pr-str schema-type) " with " (pr-str mapping-type)))))

(defmethod add-parse-type :$ref
  [schema type-name schema-type mapping-type]
  (let [[ref-schema ref-schema-type] (js/lookup-ref schema schema-type)]
    (add-parse-type ref-schema
                    (or type-name (get-in schema-type [:$ref type-name]))
                    ref-schema-type
                    mapping-type)))

;; Simple types
(doseq [simple-type ["string" "number" "integer" "boolean"]]
  (defmethod add-parse-type simple-type
    [_ _ schema-type mapping-type]
    (assoc mapping-type :parse-type (update-in schema-type [:type] keyword))))


(defmethod add-parse-type "object"
  [schema type-name schema-type mapping-type]
  (let [properties (into
                     {}
                     (for [[prop-name sub-mapping] (:properties mapping-type)
                           :let [sub-type-def (get-in schema-type [:properties prop-name])]]
                       [prop-name (add-parse-type schema nil sub-type-def sub-mapping)]))
        record-ns (js/schema-name->namespace (:schema-name schema))
        constructor-fn-var (find-var
                             (symbol (str (name record-ns)
                                          "/map->"
                                          (name type-name))))]
    (assoc mapping-type
           :properties properties
           :parse-type {:type :record
                        :constructor-fn (var-get constructor-fn-var)})))

(defn get-to-umm-mappings
  "Gets the mappings to umm with extra information to aid in parsing"
  [schema mappings]
  (let [{:keys [to-umm]} mappings
        root-type-def (get-in schema [:definitions (:root schema)])
        new-definitions (util/map-values #(add-parse-type schema (:root schema) root-type-def %)
                                         (:definitions to-umm))]
    (assoc to-umm :definitions new-definitions)))

(defn cleanup-schema
  "For debugging purposes. Removes extraneous fields"
  [schema]
  (clojure.walk/postwalk
    (fn [v]
      (if (map? v)
        (dissoc v :description :required :minItems :maxItems :minLength :maxLength)
        v))
    schema))

(comment

  (do
    (def mappings (load-mappings echo10-mappings))

    (def schema (cleanup-schema (js/load-schema-for-parsing "umm-c-json-schema.json")))

    )


  (get-to-umm-mappings schema mappings)

  (:root schema)

  (get-in schema [:definitions (:root schema)])

  (keys (get-in schema [:definitions]))



  )


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; XML Generation

(defmulti generate-value
  "TODO
  should return nil if no value"
  (fn [definitions record xml-def]
    (:type xml-def)))

(defn generate-element
  "TODO"
  [definitions record xml-def-name xml-def]
  (when-let [value (generate-value definitions record xml-def)]
    (x/element xml-def-name {} value)))

(defmethod generate-value "object"
  [definitions record {:keys [properties]}]
  (seq (for [[sub-def-name sub-def] properties
             :let [element (generate-element definitions record sub-def-name sub-def)]
             :when element]
         element)))

(defmethod generate-value "mpath"
  [definitions record {:keys [value]}]
  ;; TODO mpaths should all start with the root object. We'll ignore that when evaluating them but
  ;; then how do we ensure that?
  (get-in record (vec (drop 1 (sxp/parse-xpath value)))))

(defn generate-xml
  "TODO"
  [mappings record]
  (let [root-def-name (get-in mappings [:to-xml :root])
        definitions (get-in mappings [:to-xml :definitions])
        root-def (get definitions (keyword root-def-name))]
    ;; TODO using indent-str for readability while testing.
    (x/indent-str
      (generate-element definitions record root-def-name root-def))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; XML -> UMM

(defmulti parse-value
  ;; TODO this will probably change to a parse context
  (fn [definitions xml-root umm-def]
    (:type umm-def)))

(defmethod parse-value "object"
  [definitions xml-root {:keys [parse-type properties]}]
  (let [{:keys [constructor-fn]} parse-type]
    (constructor-fn
      (into {} (for [[prop-name sub-def] properties]
                 [prop-name (parse-value definitions xml-root sub-def)])))))


(defmethod parse-value "xpath"
  [definitions xml-root {:keys [value]}]
  ;; TODO XPaths could be parsed ahead of time when loading mappings.
  (->> (sxp/evaluate xml-root (sxp/parse-xpath value))
       ;; TODO parse the XPath value that's returned as the type from the JSON schema
       first
       :content
       first))

(defn parse-xml
  "TODO"
  [mappings xml-string]
  (let [parsed-xml (sxp/parse-xml xml-string)
        root-def-name (:root mappings)
        definitions (:definitions mappings)
        root-def (get definitions (keyword root-def-name))]
    (parse-value definitions parsed-xml root-def)))



(comment


  (def example-record
    (umm-c/map->UMM-C {:EntryTitle "The entry title V5"}))

  (:to-xml (load-mappings echo10-mappings))


  (println (generate-xml (load-mappings echo10-mappings) example-record))

  (let [mappings (load-mappings echo10-mappings)
        umm-c-schema (js/load-schema-for-parsing "umm-c-json-schema.json")]
    (add-schema-types umm-c-schema mappings))


  (let [mappings (load-mappings echo10-mappings)
        umm-c-schema (js/load-schema-for-parsing "umm-c-json-schema.json")
        umm-mappings (get-to-umm-mappings umm-c-schema mappings)]
    (parse-xml umm-mappings (generate-xml mappings example-record)))


  )












