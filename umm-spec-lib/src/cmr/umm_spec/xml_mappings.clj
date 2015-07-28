(ns cmr.umm-spec.xml-mappings
  "TODO"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [cheshire.core :as json]
            [cheshire.factory :as factory]
            [cmr.umm-spec.json-schema :as js]
            [cmr.common.util :as util]))

;; TODO define json schema for mappings

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mappings files

(def echo10-to-xml-file (io/resource "mappings/echo10/echo10-to-xml.json"))
(def echo10-to-umm-file (io/resource "mappings/echo10/echo10-to-umm.json"))

(defn load-json-file
  "TODO"
  [mappings-resource]
  (binding [factory/*json-factory* (factory/make-json-factory
                                     {:allow-comments true})]
    (json/decode (slurp mappings-resource) true)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing prep

;;; Code necessary for making mapping easier to parse. When we parse XML into UMM we have to convert
;;; things into the appropriate types. We splice together the schema and the mapping information in
;;; order to have enough information to parse into appropriate types.

(defmulti add-parse-type
  "TODO"
  (fn [schema type-name schema-type mapping-type]
    (cond
      (:type schema-type) (:type schema-type)
      (:$ref schema-type) :$ref
      ;; Allow default to fall through and find unaccounted for implementations
      :else :default)))

(defmethod add-parse-type :default
  [schema type-name schema-type mapping-type]
  (throw (Exception. (str "No method for " (pr-str schema-type) " with " (pr-str mapping-type)))))

(defmethod add-parse-type :$ref
  [schema type-name schema-type mapping-type]
  (let [[ref-schema ref-schema-type] (js/lookup-ref schema schema-type)]
    (add-parse-type ref-schema
                    (or type-name (get-in schema-type [:$ref :type-name]))
                    ref-schema-type
                    mapping-type)))

(defmethod add-parse-type "array"
  [schema type-name schema-type mapping-type]
  (update-in mapping-type [:items] #(add-parse-type schema type-name (:items schema-type) %)))

;; Simple types
(doseq [simple-type ["string" "number" "integer" "boolean"]]
  (defmethod add-parse-type simple-type
    [_ _ schema-type mapping-type]
    (assoc mapping-type :parse-type schema-type)))

(defmethod add-parse-type "object"
  [schema type-name schema-type mapping-type]
  (let [record-ns (js/schema-name->namespace (:schema-name schema))
        constructor-fn-var (find-var
                             (symbol (str (name record-ns)
                                          "/map->"
                                          (name type-name))))
        properties (into
                     {}
                     (for [[prop-name sub-mapping] (:properties mapping-type)
                           :let [sub-type-def (get-in schema-type [:properties prop-name])]]
                       [prop-name (add-parse-type schema nil sub-type-def sub-mapping)]))]
    (assoc mapping-type
           :properties properties
           :parse-type {:type :record
                        :constructor-fn (var-get constructor-fn-var)})))

(defn load-to-umm-mappings
  "Gets the mappings to umm with extra information to aid in parsing"
  [schema mappings]
  (let [root-type-def (get-in schema [:definitions (:root schema)])
        [root-def-name root-def] (first mappings)]
    {root-def-name (add-parse-type schema (:root schema) root-type-def root-def)}))

(defn cleanup-schema
  "For debugging purposes. Removes extraneous fields"
  [schema]
  (clojure.walk/postwalk
    (fn [v]
      (if (map? v)
        (dissoc v :description :required :minItems :maxItems :minLength :maxLength :constructor-fn)
        v))
    schema))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Defined mappings

(def echo10-to-umm (load-to-umm-mappings js/umm-c-schema (load-json-file echo10-to-umm-file)))

(def echo10-to-xml (load-json-file echo10-to-xml-file))




