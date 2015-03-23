(ns cmr.spatial.test.encoding.gml
  "Tests for the GML spatial encoding lib."
  (:require [clojure.data.xml :as x]
            [clojure.test :refer :all]
            [clojure.test.check :refer :all]
            [clojure.test.check.clojure-test :refer :all]
            [clojure.test.check.properties :refer :all]
            [cmr.common.xml :as cx]
            [cmr.spatial.encoding.gml :refer :all]
            [cmr.spatial.line-string :as line]
            [cmr.spatial.point :as p]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.test.generators :as spatial-gen]))

;; example XML document with valid GML elements

(def gml-xml
  "<root xmlns:gml=\"http://www.opengis.net/gml\">
     <gml:Point>
       <gml:pos>45.256 -110.45</gml:pos>
     </gml:Point>
     <gml:LineString>
       <gml:posList>
         45.256 -110.45 46.46 -109.48 43.84 -109.86 45.8 -109.2
       </gml:posList>
     </gml:LineString>
     <gml:Polygon>
       <gml:exterior>
         <gml:LinearRing>
           <gml:posList>
             45.256 -110.45 46.46 -109.48 43.84 -109.86 45.256 -110.45
           </gml:posList>
         </gml:LinearRing>
       </gml:exterior>
     </gml:Polygon>
   </root>")

(defn- emit-gml-str
  "Helper for emitting an XML document string with an xmlns attribtue
  for the gml prefix."
  [element]
  (x/emit-str (assoc-in element [:attrs :xmlns:gml] "http://www.opengis.net/gml")))

(deftest test-parse-lat-lon-string
  (testing "one point"
    (is (= (parse-lat-lon-string "9 10")
           [(p/point 10 9 true)])))
  (testing "multiple points"
    (is (= (parse-lat-lon-string "2 1.03 -4 3")
           [(p/point 1.03 2 true) (p/point 3 -4 true)]))))

(deftest test-decode-point
  (testing "decoding points from GML"
    (is (= (p/point -110.45 45.256)
           (decode (cx/element-at-path (x/parse-str gml-xml) [:Point]))))))

(deftest test-decode-line-string
  (testing "decoding GML line strings"
    (is (= (line/ords->line-string :cartesian -110.45 45.256, -109.48 46.46, -109.86 43.84, -109.2 45.8)
           (decode (cx/element-at-path (x/parse-str gml-xml) [:LineString]))))))

(deftest test-decode-polygon
  (testing "decoding GML polygons"
    (is (= (poly/polygon [(rr/ords->ring :cartesian -110.45 45.256, -109.48 46.46, -109.86 43.84, -110.45 45.256)])
           (decode (cx/element-at-path (x/parse-str gml-xml) [:Polygon]))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Property-Based Tests

(defspec check-gml-point-round-trip 100
  (for-all [p spatial-gen/points]
    (let [element (-> (encode p) emit-gml-str x/parse-str)]
      (= p (decode element)))))

(defspec check-gml-line-string-round-trip 100
  (for-all [l spatial-gen/cartesian-lines]
    (let [element (-> (encode l) emit-gml-str x/parse-str)]
      (= l (decode element)))))

(defspec check-gml-polygon-round-trip 100
  (for-all [polygon spatial-gen/cartesian-polygons-with-holes]
    (let [element (-> (encode polygon) emit-gml-str x/parse-str)]
      (= (:points polygon) (:points (decode element))))))
