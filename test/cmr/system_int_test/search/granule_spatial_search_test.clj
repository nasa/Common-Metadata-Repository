(ns cmr.system-int-test.search.granule-spatial-search-test
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.spatial.polygon :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.ring :as r]
            [cmr.spatial.derived :as derived]
            [cmr.spatial.codec]
            [clojure.string :as str]
            [cmr.spatial.dev.viz-helper :as viz-helper]
            [cmr.spatial.serialize :as srl]
            [cmr.common.dev.util :as dev-util]
            [cmr.spatial.lr-binary-search :as lbs]))

(use-fixtures :each (ingest/reset-fixture "PROV1"))

(defn polygon
  "Creates a single ring polygon with the given ordinates. Points must be in counter clockwise order.
  The polygon will be closed automatically."
  [& ords]
  (let [polygon (derived/calculate-derived (p/polygon [(apply r/ords->ring ords)]))
        outer (-> polygon :rings first)]
    (when (and (:contains-north-pole outer)
               (:contains-south-pole outer))
      (throw (Exception. "Polygon can not contain both north and south pole. Points are likely backwards.")))
    polygon))

(defn search-poly
  "Returns a url encoded polygon for searching"
  [& ords]
  (cmr.spatial.codec/url-encode (apply polygon ords)))

(defn cat-rest->native
  "Helper for creating tests that will output the command to create a polygon from a catalog rest test"
  [& ords]
  (let [pairs (->> ords
                   (partition 2)
                   reverse)
        pair-strs (map (fn [[lon lat]]
                         (str lon " " lat))
                       pairs)
        ordinates-str (str/join ", " pair-strs)
        polygon-str (str "(polygon " ordinates-str ")")]
    (dev-util/copy-to-clipboard polygon-str)
    (println "Copied to clipboard:" polygon-str)))

(def spatial-viz-enabled
  "Set this to true to debug test failures with the spatial visualization."
  false)

(defn display-indexed-granules
  "Displays the spatial areas of granules on the map."
  [granules]
  (let [geometries (mapcat (comp :geometries :spatial-coverage) granules)
        geometries (map derived/calculate-derived geometries)
        geometries (mapcat (fn [g]
                             [g
                              (srl/shape->mbr g)
                              (srl/shape->lr g)])
                           geometries)]
    (viz-helper/add-geometries geometries)))

(defn display-search-area
  "Displays a spatial search area on the map"
  [geometry]
  (let [geometry (derived/calculate-derived geometry)]
    (viz-helper/add-geometries [geometry
                                (srl/shape->mbr geometry)
                                (srl/shape->lr geometry)])))

(deftest spatial-search-test
  (let [polygon-ne (polygon 20 10, 30 20, 10 20, 20 10)
        polygon-se (polygon 30 -20, 20 -10, 10 -20, 30 -20)
        polygon-sw (polygon -20 -20, -10 -10, -30 -10,-20 -20)
        polygon-nw (polygon -20 10, -30 20, -30 10, -20 10)
        polygon-half-earth (polygon -179.9 0, -179.9 -89.9, 0 -89.9, 0 0, 0 89.9, -179.9 89.9, -179.9 0)
        polygon-north-pole (polygon 45, 80, 135, 80, -135, 80, -45, 80,  45 80)
        polygon-south-pole (polygon -45 -80, -135 -80, 135 -80, 45 -80, -45 -80)
        polygon-antimeridian (polygon 135 -10, -135 -10, -135 10, 135 10, 135 -10)
        polygon-near-sp (polygon 168.1075 -78.0047, 170.1569,-78.4112, 172.019,-78.0002, 169.9779,-77.6071, 168.1075 -78.0047)

        ;; polygon with holes
        outer (r/ords->ring -5.26,-2.59, 11.56,-2.77, 10.47,8.71, -5.86,8.63, -5.26,-2.59)
        hole1 (r/ords->ring 6.95,2.05, 2.98,2.06, 3.92,-0.08, 6.95,2.05)
        hole2 (r/ords->ring 5.18,6.92, -1.79,7.01, -2.65,5, 4.29,5.05, 5.18,6.92)
        polygon-with-holes  (p/polygon [outer hole1 hole2])

        coll (d/ingest "PROV1" (dc/collection {:spatial-coverage (dc/spatial :geodetic)}))
        make-gran (fn [& polygons]
                    (d/ingest "PROV1" (dg/granule coll {:spatial-coverage (apply dg/spatial polygons)})))
        gran1 (make-gran polygon-ne polygon-se)
        gran2 (make-gran polygon-sw)
        gran3 (make-gran polygon-nw)
        gran4 (make-gran polygon-ne)
        gran11 (make-gran polygon-half-earth)
        gran6 (make-gran polygon-north-pole)
        gran7 (make-gran polygon-south-pole)
        gran8 (make-gran polygon-antimeridian)
        gran9 (make-gran polygon-near-sp)
        gran10 (make-gran polygon-with-holes)]
    (index/refresh-elastic-index)
    (are [ords items]
         (let [found (search/find-refs :granule {:polygon (apply search-poly ords) })
               matches? (d/refs-match? items found)]
           (when (and (not matches?) spatial-viz-enabled)
             (println "Displaying failed granules and search area")
             (println "Found: " (pr-str found))
             (viz-helper/clear-geometries)
             (display-indexed-granules items)
             (display-search-area (apply polygon ords)))
           matches?)

         ;; Related the polygon with the hole
         ;; Inside holes
         [4.1,0.64,4.95,0.97,6.06,1.76,3.8,1.5,4.1,0.64] []
         [1.41,5.12,3.49,5.52,2.66,6.11,0.13,6.23,1.41,5.12] []
         ;; Partially inside a hole
         [3.58,-1.34,4.95,0.97,6.06,1.76,3.8,1.5,3.58,-1.34] [gran10]
         ;; Covers a hole
         [3.58,-1.34,5.6,0.05,7.6,2.33,2.41,2.92,3.58,-1.34] [gran10]
         ;; points inside both holes
         [4.44,0.66,5.4,1.35,2.66,6.11,0.13,6.23,4.44,0.66] [gran10]
         ;; completely covers the polygon with holes
         [-6.45,-3.74,12.34,-4.18,12,9.45,-6.69,9.2,-6.45,-3.74] [gran10 gran11]

         ;; Normal searching
         [10 10, 30 10, 30 20, 10 20, 10 10] [gran1 gran4]
         [173.34,-77.17, 171.41,-77.08, 170.64,-78.08, 173.71,-78.05, 173.34,-77.17] [gran9])))


