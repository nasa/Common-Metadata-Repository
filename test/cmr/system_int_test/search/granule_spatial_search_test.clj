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
            [cmr.spatial.ring :as r]
            [cmr.spatial.derived :as derived]
            [cmr.spatial.codec]
            [clojure.string :as str]
            [cmr.common.dev.util :as dev-util]))

(use-fixtures :each (ingest/reset-fixture "PROV1"))

(comment

  (do
    (ingest/reset)
    (ingest/create-provider "PROV1"))

  (def coll (d/ingest "PROV1" (dc/collection {:spatial-coverage (dc/spatial :geodetic)})))
  (def make-gran (fn [& polygons]
                   (d/ingest "PROV1" (dg/granule coll {:spatial-coverage (apply dg/spatial polygons)}))))
  (def gran1 (make-gran polygon-ne polygon-se))


  )


(defn polygon
  "Creates a single ring polygon with the given ordinates. Points must be in counter clockwise order.
  The polygon will be closed automatically."
  [& ords]
  ;; Take first two coordinates and append to end to close the ring
  (let [ords (concat ords (take 2 ords))
        polygon (p/polygon [(apply r/ords->ring ords)])
        outer (-> polygon :rings first derived/calculate-derived)]
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

(deftest spatial-search-test
  (let [polygon-ne (polygon 20 10, 30 20, 10 20)
        polygon-se (polygon 30 -20, 20 -10, 10 -20)
        polygon-sw (polygon -20 -20, -10 -10, -30 -10)
        polygon-nw (polygon -20 10, -30 20, -30 10)
        polygon-half-earth (polygon -179.9 0, -179.9 -89.9, 0 -89.9, 0 0, 0 89.9, -179.9 89.9)
        polygon-north-pole (polygon 45, 80, 135, 80, -135, 80, -45, 80)
        polygon-south-pole (polygon -45 -80, -135 -80, 135 -80, 45 -80)
        polygon-antimeridian (polygon 135 -10, -135 -10, -135 10, 135 10)
        coll (d/ingest "PROV1" (dc/collection {:spatial-coverage (dc/spatial :geodetic)}))
        make-gran (fn [& polygons]
                    (d/ingest "PROV1" (dg/granule coll {:spatial-coverage (apply dg/spatial polygons)})))
        gran1 (make-gran polygon-ne polygon-se)
        gran2 (make-gran polygon-sw)
        gran3 (make-gran polygon-nw)
        gran11 (make-gran polygon-half-earth)
        gran6 (make-gran polygon-north-pole)
        gran7 (make-gran polygon-south-pole)
        gran8 (make-gran polygon-antimeridian)]
    (index/refresh-elastic-index)
    (is (d/refs-match?
          [gran1]
          (search/find-refs :granule {:polygon (search-poly 10 10, 30 10, 30 20, 10 20)})))))
