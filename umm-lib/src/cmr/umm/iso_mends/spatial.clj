(ns cmr.umm.iso-mends.spatial
  "Functions for extracting spatial extent information from an
  ISO-MENDS format XML document."
  (:require [clojure.data.xml :as x]
            [clojure.string :as str]
            [camel-snake-kebab.core :as csk]
            [cmr.common.util :as util]
            [cmr.common.xml :as cx]
            [cmr.spatial.derived :as d]
            [cmr.spatial.encoding.gmd :as gmd]
            [cmr.spatial.line-string :as ls]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.point :as p]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.relations :as r]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.iso-mends.iso-mends-core :as core]
            [cmr.umm.umm-spatial :as umm-s]
            [cmr.umm.iso-mends.collection.helper :as h]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing XML

(defn- parse-geometries
  "Returns a seq of UMM geometry records from an ISO XML document."
  [xml]
  (let [id-elem   (core/id-elem xml)
        geo-elems (cx/elements-at-path id-elem [:extent :EX_Extent :geographicElement])]
    (remove nil? (flatten (map gmd/decode geo-elems)))))

(def ref-sys-path-with-ns
  "A namespaced element path sequence for the ISO MENDS coordinate system element."
  [:gmd:referenceSystemInfo :gmd:MD_ReferenceSystem :gmd:referenceSystemIdentifier :gmd:RS_Identifier :gmd:code :gco:CharacterString])

(def ref-sys-path
  "The (non-namespaced) path to access the ISO MENDS coordinate system element."
  (map #(keyword (second (.split (name %) ":"))) ref-sys-path-with-ns))

(defn- parse-coordinate-system
  [xml]
  (umm-s/->coordinate-system (cx/string-at-path xml ref-sys-path)))

(defn- xml-elem->granule-spatial-representation
  "Returns the granule spatial representation from given ISO xml element. It is parsed from the
  EX_Extent description string which looks something like:
  \"SpatialCoverageType=Horizontal, SpatialGranuleSpatialRepresentation=CARTESIAN, Temporal Range Type=Continuous Range, Time Type=UTC\""
  [xml]
  (let [description (cx/string-at-path
                      xml [:identificationInfo :MD_DataIdentification
                           :extent :EX_Extent :description :CharacterString])]
    (when description
      (some-> (re-matches #".*SpatialGranuleSpatialRepresentation=(.*)$" description)
              second
              (str/split #"," 2)
              first
              csk/->kebab-case-keyword))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Functions

(defn xml-elem->SpatialCoverage
  "Returns a UMM SpatialCoverage record from the given ISO MENDS XML element."
  [xml]
  (let [gran-spatial-rep (xml-elem->granule-spatial-representation xml)
        geometries (parse-geometries xml)
        coord-sys  (parse-coordinate-system xml)]
    (when (or gran-spatial-rep (seq geometries))
      (c/map->SpatialCoverage
        {:spatial-representation coord-sys
         :granule-spatial-representation (or gran-spatial-rep :no-spatial)
         :geometries (seq (map #(umm-s/set-coordinate-system coord-sys %) geometries))}))))

(defn spatial-coverage->coordinate-system-xml
  "Returns ISO MENDS coordinate system XML element from the given SpatialCoverage."
  [{:keys [spatial-representation]}]
  (when spatial-representation
    (reduce (fn [content tag] (x/element tag {} content))
            (.toUpperCase (name spatial-representation))
            (reverse ref-sys-path-with-ns))))

(defn spatial-coverage->extent-xml
  "Returns a sequence of ISO MENDS elements from the given SpatialCoverage."
  [{:keys [spatial-representation geometries]}]
  (->> geometries
       (map (partial umm-s/set-coordinate-system spatial-representation))
       (map d/calculate-derived)
       ;; ISO MENDS interleaves MBRs and actual spatial areas
       (mapcat (juxt r/mbr identity))
       (map gmd/encode)))

(defn spatial-coverage->extent-description-xml
  "Returns the ISO MENDS EX_Extent descirption element from the given SpatialCoverage.
  Granule spatial representation is parsed out of the description, here we only write out the
  granule spatial representation. This field is lossy in a round trip from xml to xml."
  [{:keys [granule-spatial-representation]}]
  (when granule-spatial-representation
    (h/iso-string-element
      :gmd:description
      (str "SpatialGranuleSpatialRepresentation="
           (some-> granule-spatial-representation csk/->SCREAMING_SNAKE_CASE_STRING)))))
