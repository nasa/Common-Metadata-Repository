(ns cmr.umm.core
  "Functions to transform concepts between formats."
  (:require [cmr.umm.echo10.core :as umm]))

(defmulti parse-concept
  "Convert a metadata db concept map into a umm record by parsing its metadata."
  (fn [concept]
    [(keyword (get concept "concept-type")) (get concept "format")]))

(defmulti umm->xml
  "Convert a umm record into xml of a given format."
  (fn [umm format]
    format))

(defmethod umm->xml :echo10
  [umm format]
  (umm/umm->echo10-xml umm))