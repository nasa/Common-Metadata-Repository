(ns cmr.umm-spec.json-schema
  "TODO
  May want to rellocate this "
  (:require [cheshire.core :as json]
            [cheshire.factory :as factory]
            [clojure.java.io :as io]
            [clojure.string :as str]))


(def umm-cmn-schema-file (io/resource "json-schemas/umm-cmn-json-schema.json"))

(def umm-c-schema-file (io/resource "json-schemas/umm-c-json-schema.json"))

;; TODO move this to the record generators
(def schema-name->namespace
  "A map of schema names to the namespace they should be placed in"
  {"umm-cmn-json-schema.json" 'cmr.umm-spec.models.common
   "umm-c-json-schema.json" 'cmr.umm-spec.models.collection})

;; TODO this should go into a util.
(defn parse-json-with-comments
  "TODO"
  [json-str]
  (binding [factory/*json-factory* (factory/make-json-factory
                                     {:allow-comments true})]
    (json/decode json-str true)))


(defn load-schema
  "TODO"
  [schema-resource]
  (json/decode (slurp schema-resource) true))

(defn reference-processor-selector
  "TODO"
  [type-def]
  (or (:type type-def)
      (cond
        (:$ref type-def) :$ref
        (:allOf type-def) :allOf
        ;; This is needed for a nested properties def in an allOf
        (:properties type-def) "object"

        ;; This will trigger an error
        :else
        (throw (Exception. (str "Unable to resolve ref on " (pr-str type-def)))))))

(defmulti resolve-ref
  (fn [schema-name type-def]
    (reference-processor-selector type-def)))

(defn resolve-ref-deflist
  [schema-name definition-map]
  (into {} (for [[n type-def] definition-map]
             [n (resolve-ref schema-name type-def)])))

(defmethod resolve-ref :$ref
  [schema-name type-def]
  (let [[ref-schema-name _ type-name] (str/split (:$ref type-def) #"/")]
    (assoc type-def :$ref (if (= ref-schema-name "#")
                            {:schema-name schema-name
                             :type-name (keyword type-name)}
                            {:schema-name (str/replace ref-schema-name "#" "")
                             :type-name (keyword type-name)}))))

(defmethod resolve-ref :allOf
  [schema-name type-def]
  (update-in type-def [:allOf] #(map (partial resolve-ref schema-name) %)))

(defmethod resolve-ref "object"
  [schema-name type-def]
  (update-in type-def [:properties] (partial resolve-ref-deflist schema-name)))

(defmethod resolve-ref "array"
  [schema-name type-def]
  (update-in type-def [:items] (partial resolve-ref schema-name)))

;; No resolution
(doseq [t ["string" "integer" "number" "boolean" :empty-map]]
  (defmethod resolve-ref t [_ type-def] type-def))

(defmulti referenced-schema-names
  "Returns a list of the referenced schema names from a loaded schema"
  #'reference-processor-selector)

(defmethod referenced-schema-names :default
  [_]
  nil)

(defmethod referenced-schema-names :$ref
  [type-def]
  (when-let [schema-name (get-in type-def [:$ref :schema-name])]
    [schema-name]))

(defmethod referenced-schema-names :allOf
  [type-def]
  (mapcat referenced-schema-names (:allOf type-def)))

(defmethod referenced-schema-names "object"
  [type-def]
  (mapcat referenced-schema-names (vals (:properties type-def))))

(defmethod referenced-schema-names "array"
  [type-def]
  (referenced-schema-names (:items type-def)))


(defn load-schema-for-parsing
  "TODO this could be used for record generation as well.
  Rename later"
  [schema-name]
  (let [parsed (parse-json-with-comments (slurp (io/resource schema-name)))
        definitions (resolve-ref-deflist schema-name (get parsed :definitions))
        root-def (when (:title parsed)
                   (resolve-ref schema-name (dissoc parsed :definitions :$schema :title)))
        definitions (if root-def
                      (assoc definitions (keyword (:title parsed)) root-def)
                      definitions)
        referenced-schemas (into #{} (concat (mapcat referenced-schema-names (vals definitions))
                                             (when root-def
                                               (referenced-schema-names root-def))))
        ;; Remove this schema
        referenced-schemas (disj referenced-schemas schema-name)]
    {:definitions definitions
     :schema-name schema-name
     :root (keyword (:title parsed))
     :ref-schemas (into {} (for [ref-schema-name referenced-schemas]
                             [ref-schema-name (load-schema-for-parsing ref-schema-name)]))}))

(defn lookup-ref
  "Looks up a ref in a loaded schema. Returns the schema containing the referenced type and the ref."
  [schema the-ref]
  (let [{:keys [schema-name type-name]} (:$ref the-ref)
        result (if (= schema-name (:schema-name schema))
                 ;; Refers to this schema
                 [schema (get-in schema [:definitions type-name])]
                 ;; Refers to a referenced schema
                 (lookup-ref (get-in schema [:ref-schemas schema-name]) the-ref))]
    (or result
        (throw (Exception. (str "Unable to load ref " (pr-str the-ref)))))))



(comment
  (load-schema-for-parsing "umm-cmn-json-schema.json")

  (def loaded (load-schema-for-parsing "umm-c-json-schema.json"))

  (:definitions loaded)

  )