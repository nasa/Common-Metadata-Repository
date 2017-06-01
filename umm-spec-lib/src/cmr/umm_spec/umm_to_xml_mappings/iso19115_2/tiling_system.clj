(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2.tiling-system
  "Functions for generating ISO XML tiling system elements."
  (:require
   [clojure.string :as string]
   [cmr.common.xml.gen :refer :all]
   [cmr.umm-spec.util :refer [char-string]]))

(defn- accumulate-pairs
  [acc [k v]]
  (if v
    (conj acc (str k ":") v)
    acc))

(defn- tiling-system-string
  "Returns an encoded ISO tiling system coordinate string from the given
  UMM tiling system."
  [{c1 :Coordinate1 c2 :Coordinate2}]
  (let [{c1-min :MinimumValue c1-max :MaximumValue} c1
        {c2-min :MinimumValue c2-max :MaximumValue} c2]
    (->> [["c1-min" c1-min] ["c1-max" c1-max]
          ["c2-min" c2-min] ["c2-max" c2-max]]
         (reduce accumulate-pairs [])
         (string/join " "))))

(defn- tiling-system-subelements
  "Returns the ISO representation for tiling identification systems."
  [tiling-system]
  [:gmd:geographicElement
   [:gmd:EX_GeographicDescription
    [:gmd:geographicIdentifier
     [:gmd:MD_Identifier
      [:gmd:code
       (char-string (tiling-system-string tiling-system))]
      [:gmd:codeSpace
       (char-string (str "gov.nasa.esdis.umm.tilingidentificationsystem"))]
      [:gmd:description
       (char-string (:TilingIdentificationSystemName tiling-system))]]]]])

(defn tiling-system-elements
  "Returns the ISO representation for tiling identification systems. The full UMM
   record is passed in. For every tiling identification system translate it to ISO."
  [c]
  (map tiling-system-subelements (:TilingIdentificationSystems c)))
