(ns cmr.umm-spec.umm-to-xml-mappings.xml-generator
  "Contains functions for generating XML using XML Mappings and a source clojure record"
  (:require [clojure.data.xml :as x]
            [cmr.umm-spec.simple-xpath :as sxp]
            [cmr.umm-spec.umm-to-xml-mappings.dsl :as dsl]
            [cmr.common.util :as u]))

(def dsl-type
  "The namespaced keyword used to identify content generator maps type"
  :cmr.umm-spec.umm-to-xml-mappings.dsl/type)

(defmulti generate-content
  "Generates content using a content generator and values from the XPath context."
  (fn [content-generator xpath-context]
    ;; We will eventually add custom function support through (fn? content-generator) :fn
    (cond
      (vector? content-generator) :element
      (string? content-generator) :constant
      (fn? content-generator)     :fn

      ;; We could also interpret seq here in the same way that hiccup does by treating it as a
      ;; series of content generators. Add this if needed.

      (and (map? content-generator)
           (dsl-type content-generator))
      (dsl-type content-generator)

      :else :default)))

(defmethod generate-content :fn
  [content-generator-fn xpath-context]
  (content-generator-fn xpath-context))

(defmethod generate-content :default
  [content-generator _]
  (throw (Exception. (str "Unknown content generator type: " (pr-str content-generator)))))

(defn- realize-attributes
  "Returns map with function values replaced by the result of calling them."
  [m]
  (zipmap (keys m)
          (map #(if (fn? %) (%) %)
               (vals m))))

(defmethod generate-content :element
  [[tag & content-generators] xpath-context]
  (let [maybe-attributes (first content-generators)
        [attributes content-generators] (if (and (map? maybe-attributes)
                                                 (not (dsl-type maybe-attributes)))
                                          [(first content-generators) (rest content-generators)]
                                          [{} content-generators])
        attributes (realize-attributes attributes)
        content (mapcat #(generate-content % xpath-context) content-generators)]
    (when (or (seq attributes) (seq content))
      [(x/element tag attributes content)])))

(defmethod generate-content :xpath
  [{:keys [value]} xpath-context]
  (some->> (sxp/evaluate xpath-context (sxp/parse-xpath value))
             :context
             first
             str
             vector))

(defmethod generate-content :constant
  [value _]
  [value])

(defmethod generate-content :for-each
  [{:keys [xpath template]} xpath-context]
  (let [new-xpath-context (sxp/evaluate xpath-context (sxp/parse-xpath xpath))]
    (for [data (:context new-xpath-context)
          :let [single-item-xpath-context (assoc new-xpath-context :context [data])]
          item (generate-content template single-item-xpath-context)]
      item)))

(defn generate-xml
  "Generates XML using a root content generator and a source UMM record."
  [content-generator record]
  (let [xpath-context (sxp/create-xpath-context-for-data record)
        content (generate-content content-generator xpath-context)]
    (x/indent-str content)))
