(ns cmr.umm.core
  "Functions to transform concepts between formats."
  (:require [cmr.umm.echo10.core :as umm]
            [cmr.umm.echo10.collection :as c]
            [cmr.umm.echo10.granule :as g]))

(defmulti parse-concept
  "Convert a metadata db concept map into a umm record by parsing its metadata."
  (fn [concept]
    [(keyword (:concept-type concept)) (:format concept)]))

(defmethod parse-concept [:collection "application/echo10+xml"]
  [concept]
  (c/parse-collection (:metadata concept)))

(defmethod parse-concept [:granule "application/echo10+xml"]
  [concept]
  (g/parse-granule (:metadata concept)))

(defmulti umm->xml
  "Convert a umm record into xml of a given format."
  (fn [umm format]
    format))

(defmethod umm->xml :echo10
  [umm format]
  (umm/umm->echo10-xml umm))