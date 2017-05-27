(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2.tiling-system
  "Functions for generating ISO XML tiling system elements."
  (:require [cmr.common.xml.gen :refer :all]
            [cmr.umm-spec.util :refer [char-string]]))

(defn- tiling-system-string
  "Returns an encoded ISO tiling system coordinate string from the given UMM tiling system."
  [tiling-system]
  (let [{{c1-min :MinimumValue
          c1-max :MaximumValue} :Coordinate1
         {c2-min :MinimumValue
          c2-max :MaximumValue} :Coordinate2 } tiling-system]
      (let [buildString ""]
        (with-out-str
          (when c1-min
            (print "c1-min:" c1-min)
            (when (or c1-max c2-min c2-max)
              (print " ")))
          (when c1-max
            (print "c1-max:" c1-max)
            (when (or c2-min c2-max)
              (print " ")))
          (when c2-min
            (print "c2-min:" c2-min)
            (when c2-max
              (print " ")))
          (when c2-max
            (print "c2-max:" c2-max))))))

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
