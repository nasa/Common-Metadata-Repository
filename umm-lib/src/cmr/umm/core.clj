(ns cmr.umm.core
  "Functions to transform concepts between formats."
  (:require [cmr.common.mime-types :as mt]
            [cmr.umm.echo10.core :as echo10]
            [cmr.umm.echo10.collection :as echo10-c]
            [cmr.umm.echo10.granule :as echo10-g]
            [cmr.umm.dif.core :as dif]
            [cmr.umm.dif10.core :as dif10]
            [cmr.umm.dif.collection :as dif-c]
            [cmr.umm.dif10.collection :as dif10-c]
            [cmr.umm.iso-mends.core :as iso-mends]
            [cmr.umm.iso-mends.collection :as iso-mends-c]
            [cmr.umm.iso-mends.granule :as iso-mends-g]
            [cmr.umm.iso-smap.core :as iso-smap]
            [cmr.umm.iso-smap.collection :as iso-smap-c]
            [cmr.umm.iso-smap.granule :as iso-smap-g]))

(defmulti validate-concept-xml
  "Validates the concept metadata against its xml schema."
  (fn [concept]
    [(keyword (:concept-type concept)) (:format concept)]))

(defmethod validate-concept-xml [:collection mt/echo10]
  [concept]
  (echo10-c/validate-xml (:metadata concept)))

(defmethod validate-concept-xml [:granule mt/echo10]
  [concept]
  (echo10-g/validate-xml (:metadata concept)))

(defmethod validate-concept-xml [:collection mt/dif]
  [concept]
  (dif-c/validate-xml (:metadata concept)))

(defmethod validate-concept-xml [:collection mt/dif10]
  [concept]
  (dif10-c/validate-xml (:metadata concept)))

(defmethod validate-concept-xml [:collection mt/iso]
  [concept]
  (iso-mends-c/validate-xml (:metadata concept)))

(defmethod validate-concept-xml [:collection mt/iso-smap]
  [concept]
  (iso-smap-c/validate-xml (:metadata concept)))

(defmethod validate-concept-xml [:granule mt/iso-smap]
  [concept]
  (iso-smap-g/validate-xml (:metadata concept)))

(defmulti parse-concept
  "Convert a metadata db concept map into a umm record by parsing its metadata."
  (fn [concept]
    [(keyword (:concept-type concept)) (:format concept)]))

(defmethod parse-concept [:collection mt/echo10]
  [concept]
  (echo10-c/parse-collection (:metadata concept)))

(defmethod parse-concept [:granule mt/echo10]
  [concept]
  (echo10-g/parse-granule (:metadata concept)))

(defmethod parse-concept [:collection mt/dif]
  [concept]
  (dif-c/parse-collection (:metadata concept)))

(defmethod parse-concept [:collection mt/dif10]
  [concept]
  (dif10-c/parse-collection (:metadata concept)))

(defmethod parse-concept [:collection mt/iso]
  [concept]
  (iso-mends-c/parse-collection (:metadata concept)))

(defmethod parse-concept [:collection mt/iso-smap]
  [concept]
  (iso-smap-c/parse-collection (:metadata concept)))

(defmethod parse-concept [:granule mt/iso-smap]
  [concept]
  (iso-smap-g/parse-granule (:metadata concept)))

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

(defmethod umm->xml :dif10
  [umm format]
  (dif10/umm->dif10-xml umm))

(defmethod umm->xml :iso19115
  [umm format]
  (iso-mends/umm->iso-mends-xml umm))

(defmethod umm->xml :iso-smap
  [umm format]
  (iso-smap/umm->iso-smap-xml umm))

