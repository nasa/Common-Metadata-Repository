(ns cmr.umm-spec.util
  "This contains utilities for the UMM Spec code."
  (:require [cheshire.core :as json]
            [cheshire.factory :as factory]
            [clojure.data.xml :as x]))

(defn load-json-resource
  "Loads a json resource from the classpath. The JSON file may contain comments which are ignored"
  [json-resource]
  (binding [factory/*json-factory* (factory/make-json-factory
                                     {:allow-comments true})]
    (json/decode (slurp json-resource) true)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Temporary code that can convert UMM to XML. We may or may not need to use this depending on
;; whether we decide to use XSLT for conversion of UMM to XML formats.

(defn umm-to-xml
  [element-name obj]
  (cond
    (map? obj)
    (x/element element-name {}
               (for [[k v] obj
                     :when (some? v)]
                 (umm-to-xml k v)))

    (sequential? obj)
    (map #(umm-to-xml element-name %) obj)

    :else
    (x/element element-name {} (str obj))))


(defn umm-c-to-xml
  [umm-c]
  (x/emit-str (umm-to-xml :UMM-C umm-c)))