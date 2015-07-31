(ns cmr.umm-spec.xml-mappings
  "Contains functions for loading mappings from UMM to XML and from XML to UMM. Mappings are defined
  in JSON files. This namespace parses the JSON files and performs additional processing. The
  loaded mappings are defined in vars in this namespace."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.record-generator :as record-gen]
            [cmr.common.util :as util]
            [cmr.umm-spec.util :as spec-util]))

;; TODO define json schema for mappings and use to validate them

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mappings files

(def ^:private umm-c-to-echo10-xml-file (io/resource "mappings/echo10/umm-c-to-echo10-xml.json"))
(def ^:private echo10-xml-to-umm-c-file (io/resource "mappings/echo10/echo10-xml-to-umm-c.json"))

(def ^:private umm-c-to-mends-xml-file (io/resource "mappings/iso19115-mends/umm-c-to-mends-xml.json"))
(def ^:private umm-c-to-mends-xml2-file (io/resource "mappings/iso19115-mends/umm-c-to-mends-xml2.json"))
(def ^:private mends-xml-to-umm-c-file (io/resource "mappings/iso19115-mends/mends-xml-to-umm-c.json"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing prep

;;; Code necessary for making mapping easier to parse. When we parse XML into UMM we have to convert
;;; things into the appropriate types. We splice together the schema and the mapping information in
;;; order to have enough information to parse into appropriate types.

(defmulti ^:private add-parse-type
  "Adds an additional field to loaded mappings that defines what type to convert the XML value into.
  Parsed types could be clojure records, strings, numeric types, etc."
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
  (let [record-ns (record-gen/schema-name->namespace (:schema-name schema))
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

(defn- load-to-umm-mappings
  "Gets the mappings to umm with extra information to aid in parsing"
  [schema mappings]
  (let [root-type-def (get-in schema [:definitions (:root schema)])
        [root-def-name root-def] (first mappings)]
    {root-def-name (add-parse-type schema (:root schema) root-type-def root-def)}))

(defn- cleanup-schema
  "For debugging purposes. Removes extraneous fields for printing the mappings so it's easier to read."
  [schema]
  (clojure.walk/postwalk
    (fn [v]
      (if (map? v)
        (dissoc v :description :required :minItems :maxItems :minLength :maxLength :constructor-fn)
        v))
    schema))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; XML Generation Prep

(defmulti load-mapping
  (fn [mapping]
    (cond
      (:type mapping) (:type mapping)
      (vector? mapping) :vector
      ;; Let default fall through to unexpected types and throw an exception
      :else :default)))

(defmethod load-mapping :default
  [mapping]
  (throw (Exception. (str "No implementation for mapping " (pr-str mapping)))))

(defn load-properties
  [properties]
  (vec (for [[prop-name mapping] properties]
         [prop-name (load-mapping mapping)])))

(defmethod load-mapping "object"
  [mapping]
  (update-in mapping [:properties] load-properties))

(defmethod load-mapping "xpath"
  [mapping]
  mapping)

(defmethod load-mapping "array"
  [mapping]
  (if (:items mapping)
    (update-in mapping [:items] load-mapping)
    mapping))

;; This is syntactic sugar for specifying an object with properties
(defmethod load-mapping :vector
  [properties]
  {:type "object"
   :properties (load-properties properties)})

(defn- load-to-xml-mappings
  "TODO"
  [mappings]
  (let [[root-def-name root-def] (first mappings)]
    {root-def-name (load-mapping root-def)}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Defined mappings

(def echo10-xml-to-umm-c
  (load-to-umm-mappings js/umm-c-schema (spec-util/load-json-resource echo10-xml-to-umm-c-file)))

(def umm-c-to-echo10-xml
  (load-to-xml-mappings (spec-util/load-json-resource umm-c-to-echo10-xml-file)))

(def mends-xml-to-umm-c
  (load-to-umm-mappings js/umm-c-schema (spec-util/load-json-resource mends-xml-to-umm-c-file)))

(def umm-c-to-mends-xml
  (load-to-xml-mappings (spec-util/load-json-resource umm-c-to-mends-xml-file)))

;; Temporary to test smaller representation
(def umm-c-to-mends-xml2
  (load-to-xml-mappings (spec-util/load-json-resource umm-c-to-mends-xml2-file)))

(comment

  ;; They should be equal
  (= umm-c-to-mends-xml2 umm-c-to-mends-xml)


  )




