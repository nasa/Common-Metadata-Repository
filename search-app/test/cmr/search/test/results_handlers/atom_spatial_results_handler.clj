(ns cmr.search.test.results-handlers.atom-spatial-results-handler
  (:require [clojure.test :refer :all]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.line-string :as l]
            [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.cartesian-ring :as cr]
            [cmr.search.results-handlers.atom-spatial-results-handler :as asrh]))

(deftest shape-decimal-expanded-notation-test
  (testing "shapes are written out in expanded notation format"
    (are [shape shape-str]
         (= shape-str (asrh/shape->string shape))

         (p/point 0.00000567994760508036 0.0000000123456) "0.0000000123456 0.00000567994760508036"

         (l/ords->line-string
           :geodetic [0.00000567994760508036 0.0000000123456, 8.00000567994760508036 9.0000000123456])
         "0.0000000123456 0.00000567994760508036 9.0000000123456 8.000005679947606"

         (m/mbr
           0.00000567994760508036 0.0000000123456, 8.00000567994760508036 9.0000000123456)
         "9.0000000123456 0.00000567994760508036 0.0000000123456 8.000005679947606"

         (gr/map->GeodeticRing {:points [(p/point 1 2)
                                         (p/point 0.00000567994760508036 0.0000000123456)
                                         (p/point 1 2)]})
         "2 1 0.0000000123456 0.00000567994760508036 2 1"

         (cr/map->CartesianRing {:points [(p/point 1 2)
                                          (p/point 0.00000567994760508036 0.0000000123456)
                                          (p/point 1 2)]})
         "2 1 0.0000000123456 0.00000567994760508036 2 1")))
