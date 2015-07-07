(ns cmr.umm-spec.parse-gen
  "TODO"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [cheshire.core :as json]
            [cheshire.factory :as factory]
            [cmr.umm-spec.models.common :as cmn]
            [cmr.umm-spec.models.collection :as umm-c]
            [clojure.data.xml :as x]
            [cmr.umm-spec.simple-xpath :as sxp]))

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
  [definitions xml-root {:keys [properties]}]
  ;; TODO Construct clojure record from map.
  ;; We need to determine the type from the json schema
  (into {} (for [[prop-name sub-def] properties]
             [prop-name (parse-value definitions xml-root sub-def)])))


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
        root-def-name (get-in mappings [:to-umm :root])
        definitions (get-in mappings [:to-umm :definitions])
        root-def (get definitions (keyword root-def-name))]
    (parse-value definitions parsed-xml root-def)))



(comment


  (def example-record
    (umm-c/map->UMM-C {:EntryTitle "The entry title V5"}))

  (:to-xml (load-mappings echo10-mappings))


  (println (generate-xml (load-mappings echo10-mappings) example-record))

  (let [mappings (load-mappings echo10-mappings)]
    (parse-xml mappings (generate-xml mappings example-record)))


  )