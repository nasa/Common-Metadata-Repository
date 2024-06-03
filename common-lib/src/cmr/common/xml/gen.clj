(ns cmr.common.xml.gen
  "Contains functions for generating XML using a Hiccup-style syntax."
  (:require
   [clojure.data.xml :as xml]
   [cmr.common.xml :as cx]
   [cmr.common.xml.simple-xpath :refer [select]]))

(defprotocol GenerateXML
  "Generates an XML document"
  (generate [x]))

(extend-protocol GenerateXML
  clojure.lang.PersistentVector
  (generate [[tag maybe-attrs & content]]
            (let [[attrs content] (if (map? maybe-attrs)
                                      [maybe-attrs content]
                                      [{} (cons maybe-attrs content)])
                  content         (generate content)]
              (when (or (seq content) (seq attrs))
                (apply xml/element tag attrs content))))

  clojure.lang.ISeq
  (generate [xs] (seq (keep generate xs)))

  java.lang.Number
  (generate [n] (str n))

  clojure.data.xml.Element
  (generate [el] el)

  String
  (generate [s] s)

  org.joda.time.DateTime
  (generate [d] (str d))

  java.lang.Boolean
  (generate [b] (str b))

  clojure.lang.Keyword
  (generate [k] (str k))

  nil
  (generate [_] nil))

(defn xml
  "Returns XML string from structure describing XML elements."
  [structure]
  (cx/remove-xml-processing-instructions
   (xml/emit-str (generate structure))))

;;; Helpers

(defn element-from
  "just one element"
  [context kw]
  [kw {} (select context (name kw))])

(defn elements-from
  "multiple elements"
  [context & kws]
  (map (partial element-from context) kws))
