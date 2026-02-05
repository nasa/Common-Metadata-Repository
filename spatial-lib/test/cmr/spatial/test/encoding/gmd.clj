(ns cmr.spatial.test.encoding.gmd
  "Tests for the GML spatial encoding lib."
  (:require
   [clojure.data.xml :as xml]
   [clojure.test :refer [deftest is testing]]
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.properties :refer [for-all]]
   [clojure.test.check.generators :as gen]
   [cmr.common.xml :as cx]
   [cmr.spatial.encoding.gmd :as gmd]
   [cmr.spatial.point :as p]
   [cmr.spatial.mbr :as mbr]
   [cmr.spatial.test.generators :as spatial-gen]))

;; known example XML document with valid GML elements from NASA docs

(def gmd-xml-mbr
  "<root xmlns:gml=\"http://www.opengis.net/gml\"
         xmlns:gmd=\"http://www.isotc211.org/2005/gmd\"
         xmlns:gco=\"http://www.isotc211.org/2005/gco\">
     <gmd:geographicElement>
       <gmd:EX_GeographicBoundingBox>
         <gmd:extentTypeCode>
           <gco:Boolean>true</gco:Boolean>
         </gmd:extentTypeCode>
         <gmd:westBoundLongitude>
           <gco:Decimal>-178</gco:Decimal>
         </gmd:westBoundLongitude>
         <gmd:eastBoundLongitude>
           <gco:Decimal>180</gco:Decimal>
         </gmd:eastBoundLongitude>
         <gmd:southBoundLatitude>
           <gco:Decimal>-78</gco:Decimal>
         </gmd:southBoundLatitude>
         <gmd:northBoundLatitude>
           <gco:Decimal>75</gco:Decimal>
         </gmd:northBoundLatitude>
       </gmd:EX_GeographicBoundingBox>
     </gmd:geographicElement>
     <gmd:geographicElement>
       <gmd:EX_BoundingPolygon>
         <gmd:polygon>
           <gml:Point>
             <gml:pos>45.256 -110.45</gml:pos>
           </gml:Point>
         </gmd:polygon>
       </gmd:EX_BoundingPolygon>
     </gmd:geographicElement>
   </root>")

(defn- emit-gmd-str
  "Helper for emitting an XML document string with an xmlns attribtue
  for the gml prefix."
  [element]
  (xml/emit-str (update-in element [:attrs] assoc
                         :xmlns:gml "http://www.opengis.net/gml"
                         :xmlns:gmd "http://www.isotc211.org/2005/gmd"
                         :xmlns:gco "http://www.isotc211.org/2005/gco")))

(deftest test-decode-gmd-xml
  (testing "GMD decode"
    (testing "Minimum bounding box"
      (is (= (flatten (map gmd/decode (cx/elements-at-path (cx/parse-str gmd-xml-mbr) [:geographicElement])))
             [(mbr/mbr -178.0 75.0 180.0 -78.0)
              (p/point -110.45 45.256)])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Property-Based Tests

(declare check-gmd-round-trip)
(defspec check-gmd-round-trip 1000
  (for-all [spatial (gen/one-of [spatial-gen/mbrs
                                 spatial-gen/points
                                 spatial-gen/cartesian-lines
                                 spatial-gen/cartesian-polygons-with-holes])]
    (let [decoded (first (-> spatial gmd/encode emit-gmd-str cx/parse-str gmd/decode))]
      (or (= spatial decoded)
          ;; special fudge for polygons
          (= (:points spatial) (:points decoded))))))
