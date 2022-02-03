(ns cmr.search.test.results-handlers.stac-spatial-results-handler
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.search.results-handlers.stac-spatial-results-handler :as ssrh]
   [cmr.spatial.cartesian-ring :as cr]
   [cmr.spatial.derived :as d]
   [cmr.spatial.geodetic-ring :as gr]
   [cmr.spatial.ring-relations :as rr]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]))

(deftest shapes-to-stac-geometry-bbox-test
  (testing "shapes are written out in expanded notation format"
    (are3 [shapes stac-geometry stac-bbox]
      (do
        (is (= stac-geometry (ssrh/shapes->stac-geometry shapes)))
        (is (= stac-bbox (ssrh/shapes->stac-bbox shapes))))

      "a signle point to stac geometry and bbox"
      [(p/point 0.00000567994760508036 0.0000000123456)]
      {:type "Point"
       :coordinates [0.00000567994760508036 0.0000000123456]}
      [0.00000567994760508036 0.0000000123456 0.00000567994760508036 0.0000000123456]

      "multiple points to stac geometry and bbox"
      [(p/point 0.00000567994760508036 0.0000000123456) (p/point 120.0 30.5)]
      {:type "MultiPoint"
       :coordinates [[0.00000567994760508036 0.0000000123456] [120.0 30.5]]}
      [0.00000567994760508036 0.0000000123456 120.0 30.5]

      "a single line to stac geometry and bbox"
      [(l/ords->line-string
        :geodetic [0.60508036 0.123456, 8.0508036 9.123456])]
      {:type "LineString"
       :coordinates [[0.60508036 0.123456] [8.0508036 9.123456]]}
      [0.60508036 0.123456 8.0508036 9.123456]

      "Multiple lines to stac geometry and bbox"
      [(l/ords->line-string :cartesian [0.60508036 0.123456, 8.0508036 9.123456])
       (l/ords->line-string :cartesian [10.6 5.12, 128.36 9.56])]
      {:type "MultiLineString"
       :coordinates [[[0.60508036 0.123456] [8.0508036 9.123456]]
                     [[10.6 5.12] [128.36 9.56]]]}
      [0.60508036 0.123456 128.36 9.56]

      "a single bounding box to stac geometry and bbox"
      [(m/mbr -20.0, 10.0, 20.0, -10.0)]
      {:type "Polygon"
       :coordinates [[[-20.0, -10.0]
                      [20.0, -10.0]
                      [20.0, 10.0]
                      [-20.0, 10.0]
                      [-20.0, -10.0]]]}
      [-20.0 -10.0, 20.0, 10.0]

      "multiple bounding boxes to stac geometry and bbox"
      [(m/mbr -20.0, 10.0, 20.0, -10.0)
       (m/mbr 0.00000567994760508036 9.0000000123456, 8.00000567994760508036 0.0000000123456)]
      {:type "MultiPolygon"
       :coordinates [
                     [[[-20.0, -10.0]
                       [20.0, -10.0]
                       [20.0, 10.0]
                       [-20.0, 10.0]
                       [-20.0, -10.0]]]
                     [[[0.00000567994760508036, 0.0000000123456]
                       [8.00000567994760508036, 0.0000000123456]
                       [8.00000567994760508036, 9.0000000123456]
                       [0.00000567994760508036, 9.0000000123456]
                       [0.00000567994760508036, 0.0000000123456]]]
                     ]}
      [-20.0 -10.0, 20.0, 10.0]

      "a single polygon to stac geometry and bbox"
      [(poly/polygon :geodetic
                     [(rr/ords->ring
                       :geodetic
                       [-5.26,-2.59, 11.56,-2.77, 10.47,8.71, -5.86,8.63, -5.26,-2.59])])]
      {:type "Polygon"
       :coordinates [[[-5.26 -2.59] [11.56 -2.77] [10.47 8.71] [-5.86 8.63] [-5.26 -2.59]]]}
      [-5.86 -2.77 11.56 8.76201563120705]

      "polygon with holes to stac geometry and bbox"
      [(poly/polygon :geodetic
                     [(rr/ords->ring
                       :geodetic
                       [-5.26,-2.59, 11.56,-2.77, 10.47,8.71, -5.86,8.63, -5.26,-2.59])
                      (rr/ords->ring :geodetic [6.95,2.05, 2.98,2.06, 3.92,-0.08, 6.95,2.05])
                      (rr/ords->ring :geodetic [5.18,6.92, -1.79,7.01, -2.65,5, 4.29,5.05, 5.18,6.92])
                      ])]
      {:type "Polygon"
       :coordinates [[[-5.26 -2.59] [11.56 -2.77] [10.47 8.71] [-5.86 8.63] [-5.26 -2.59]]
                     [[6.95 2.05] [2.98 2.06] [3.92 -0.08] [6.95 2.05]]
                     [[5.18 6.92] [-1.79 7.01] [-2.65 5.0] [4.29 5.05] [5.18 6.92]]]}
      [-5.86 -2.77 11.56 8.76201563120705]

      "another single polygon to stac geometry and bbox"
      [(poly/polygon :cartesian
                     [(rr/ords->ring
                       :cartesian
                       [-9.86 49.84, -10.45 45.256, -19.48 46.46, -9.86 49.84])])]
      {:type "Polygon"
       :coordinates [[[-9.86 49.84]
                      [-10.45 45.256]
                      [-19.48 46.46]
                      [-9.86 49.84]]]}
      [-19.48 45.256 -9.86 49.84]

      "multiple polygons to stac geometry and bbox"
      [(poly/polygon :geodetic
                     [(rr/ords->ring
                       :geodetic
                       [-5.26,-2.59, 11.56,-2.77, 10.47,8.71, -5.86,8.63, -5.26,-2.59])])
       (poly/polygon :cartesian
                     [(rr/ords->ring
                       :cartesian
                       [-9.86 49.84, -10.45 45.256, -19.48 46.46, -9.86 49.84])])]
      {:type "MultiPolygon"
       :coordinates [
                     [[[-5.26 -2.59]
                       [11.56 -2.77]
                       [10.47 8.71]
                       [-5.86 8.63]
                       [-5.26 -2.59]]]
                     [[[-9.86 49.84]
                       [-10.45 45.256]
                       [-19.48 46.46]
                       [-9.86 49.84]]]
                     ]}
      [-19.48 -2.77 11.56 49.84]

      "no spatial"
      nil
      nil
      nil)))
