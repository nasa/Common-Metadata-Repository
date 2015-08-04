(ns cmr.umm-spec.xml-generation
  "Contains functions for generating XML using XML Mappings and a source clojure record"
  (:require [clojure.data.xml :as x]
            [cmr.umm-spec.simple-xpath :as sxp]
            [cmr.common.util :as u]))

;; TODO move to cmr.umm-spec.xml-mappings.xml-generation

(defmulti generate-content
  "TODO"
  (fn [content-generator xpath-context]
    ;; We will eventually add custom function support through (fn? content-generator) :fn
    (cond
      (vector? content-generator) :element
      (string? content-generator) :constant

      ;; We could also interpret seq here in the same way that hiccup does by treating it as a
      ;; series of content generators. Add this if needed.

      (and (map? content-generator)
           (:type content-generator)) (:type content-generator)
      :else :default)))

(defmethod generate-content :default
  [content-generator _]
  (throw (Exception. (str "Unknown content generator type: " (pr-str content-generator)))))

(defmethod generate-content :element
  [[tag & content-generators] xpath-context]

  ;; TODO add code comments

  (let [{attrib-content-generators true
         other-content-generators false} (group-by #(= (:type %) :attribs) content-generators)
        attributes (reduce (fn [attribs attrib-cg]
                             (into attribs
                                   (for [[k content-gen] (:value attrib-cg)]
                                     [k (generate-content content-gen xpath-context)])))
                           {}
                           attrib-content-generators)]
    (x/element
      tag attributes
      (map #(generate-content % xpath-context) other-content-generators))))

(defmethod generate-content :xpath
  [{:keys [value]} xpath-context]
  (some->> (sxp/evaluate xpath-context (sxp/parse-xpath value))
           :context
           first
           str))

(defmethod generate-content :constant
  [value _]
  value)

(defmethod generate-content :for-each
  [{:keys [xpath template]} xpath-context]
  (let [new-xpath-context (sxp/evaluate xpath-context (sxp/parse-xpath xpath))]
    (for [data (:context new-xpath-context)
          :let [single-item-xpath-context (assoc new-xpath-context :context [data])]]
      (generate-content template single-item-xpath-context))))

(defn generate-xml
  "TODO"
  [content-generator record]
  (let [xpath-context (sxp/create-xpath-context-for-data record)
        content (generate-content content-generator xpath-context)]
    ;; TODO temporary indent str
    (x/indent-str content)))
















