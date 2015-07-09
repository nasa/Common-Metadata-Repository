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
            [cmr.common.date-time-parser :as dtp]
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; XML Generation

(defmulti generate-element
  "TODO
  should return nil if no value"
  (fn [xpath-context element-name xml-def]
    (:type xml-def)))

(defmethod generate-element "object"
  [xpath-context element-name {:keys [properties]}]
  (when-let [content (seq (for [[sub-def-name sub-def] properties
                                :let [element (generate-element xpath-context sub-def-name sub-def)]
                                :when element]
                            element))]
    (x/element element-name {} content)))

(defmethod generate-element "mpath"
  [xpath-context element-name {:keys [value]}]
  (when-let [value (->> (sxp/parse-xpath value)
                        (sxp/evaluate xpath-context)
                        :context
                        first)]
    (x/element element-name {} (str value))))

(defmethod generate-element "array"
  [xpath-context element-name {:keys [items-mpath items]}]
  (let [new-xpath-context (sxp/evaluate xpath-context (sxp/parse-xpath items-mpath))]
    (for [data (:context new-xpath-context)
          :let [single-item-xpath-context (assoc new-xpath-context :context [data])]]
      (if items
        (generate-element single-item-xpath-context element-name items)
        (x/element element-name {} (str data))))))

(defmethod generate-element "matches-umm"
  [xpath-context element-name _]
  (letfn [(record-to-xml
            [record]
            (for [[prop-name prop-value] record]
              (x/element prop-name {}
                         (if (map? prop-value)
                           (record-to-xml prop-value)
                           (str prop-value)))))]
    (x/element element-name {} (record-to-xml (-> xpath-context :context first)))))

(defn generate-xml
  "TODO"
  [mappings record]
  (let [root-def-name (get-in mappings [:to-xml :root])
        definitions (get-in mappings [:to-xml :definitions])
        root-def (get definitions (keyword root-def-name))]
    ;; TODO using indent-str for readability while testing.
    (x/indent-str
      (generate-element (sxp/wrap-data-for-xpath record) root-def-name root-def))))

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
      :else :default
      ;; Allow default to fall through and find unaccounted for implementations

      )))

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
        (dissoc v :description :required :minItems :maxItems :minLength :maxLength :constructor-fn)
        v))
    schema))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing

(defmulti parse-value
  "TODO"
  ;; TODO this will probably change to a parse context
  (fn [xpath-context umm-def]
    (:type umm-def)))

(defmethod parse-value "object"
  [xpath-context {:keys [parse-type properties]}]
  (let [{:keys [constructor-fn]} parse-type]
    (constructor-fn
      (into {} (for [[prop-name sub-def] properties]
                 [prop-name (parse-value xpath-context sub-def)])))))

(defmulti parse-xpath-results
  "TODO"
  (fn [parse-type xpath-context]
    (or (:format parse-type) (:type parse-type))))

(defn extract-xpath-context-value
  [xpath-context]
  (->> xpath-context :context first :content first))

(defmethod parse-xpath-results "string"
  [_ xpath-context]
  (extract-xpath-context-value xpath-context))

(defmethod parse-xpath-results "date-time"
  [_ xpath-context]
  (dtp/parse-datetime (extract-xpath-context-value xpath-context)))

(defmethod parse-xpath-results "integer"
  [parse-type xpath-context]
  (Long/parseLong ^String (extract-xpath-context-value xpath-context)))

(defmethod parse-xpath-results "boolean"
  [parse-type xpath-context]
  (= "true" (extract-xpath-context-value xpath-context)))

(defmethod parse-value "xpath"
  [xpath-context {:keys [parse-type value]}]
  ;; TODO XPaths could be parsed ahead of time when loading mappings.
  (parse-xpath-results parse-type (sxp/evaluate xpath-context (sxp/parse-xpath value))))

(defmethod parse-value "concat"
  [xpath-context {:keys [parts]}]
  (->> parts
       (mapv #(assoc % :parse-type {:type "string"}))
       (mapv #(parse-value xpath-context %))
       str/join))

(defmethod parse-value "array"
  [xpath-context {:keys [items-xpath items]}]
  (let [new-xpath-context (sxp/evaluate xpath-context (sxp/parse-xpath items-xpath))]
    (for [element (:context new-xpath-context)
          :let [single-item-xpath-context (assoc new-xpath-context :context [element])]]
      (if (and items (:type items))
        (parse-value single-item-xpath-context items)
        (parse-xpath-results (:parse-type items "string") single-item-xpath-context)))))

(defmethod parse-value "matches-xml"
  [xpath-context _]
  (letfn [(content-to-data
            [content]
            (cond
              (map? content)
              {(:tag content) (content-to-data (:content content))}

              (sequential? content)
              (if (= 1 (count content))
                (content-to-data (first content))
                (mapv content-to-data content))

              :else
              content))]
    (content-to-data (:context xpath-context))))


(defmethod parse-value "constant"
  [_ {:keys [value]}]
  value)

(defn parse-xml
  "TODO"
  [mappings xml-string]
  (let [xpath-context (sxp/parse-xml xml-string)
        root-def-name (:root mappings)
        definitions (:definitions mappings)
        root-def (get definitions (keyword root-def-name))]
    (parse-value xpath-context root-def)))



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












