(ns cmr.umm.core
  "Functions to transform concepts between formats."
  (:require [cmr.umm.echo10.core :as echo10]
            [cmr.umm.echo10.collection :as echo10-c]
            [cmr.umm.echo10.granule :as echo10-g]
            [cmr.umm.dif.core :as dif]
            [cmr.umm.dif.collection :as dif-c]
            [cmr.umm.iso-mends.core :as iso-mends]
            [cmr.umm.iso-mends.collection :as iso-mends-c]))

(defmulti parse-concept
  "Convert a metadata db concept map into a umm record by parsing its metadata."
  (fn [concept]
    [(keyword (:concept-type concept)) (:format concept)]))

(defmethod parse-concept [:collection "application/echo10+xml"]
  [concept]
  (echo10-c/parse-collection (:metadata concept)))

(defmethod parse-concept [:granule "application/echo10+xml"]
  [concept]
  (echo10-g/parse-granule (:metadata concept)))

(defmethod parse-concept [:collection "application/dif+xml"]
  [concept]
  (dif-c/parse-collection (:metadata concept)))

(defmethod parse-concept [:collection "application/iso-mends+xml"]
  [concept]
  (iso-mends-c/parse-collection (:metadata concept)))

(defmulti umm->xml
  "Convert a umm record into xml of a given format."
  (fn [umm format]
    format))

(defmethod umm->xml :echo10
  [umm format]
  (echo10/umm->echo10-xml umm))

(defmethod umm->xml :dif
  [umm format]
  (dif/umm->dif-xml umm))

(defmethod umm->xml :iso-mends
  [umm format]
  (iso-mends/umm->iso-mends-xml umm))
