(ns cmr.spatial.test.polygon
  (:require [clojure.test :refer :all]
            [cmr.common.test.test-check-ext :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]

            ;; my code
            [cmr.spatial.math :refer :all]
            [cmr.spatial.point :as p]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.derived :as d]
            [cmr.spatial.test.generators :as sgen]
            [cmr.spatial.validation :as v]
            [cmr.spatial.messages :as msg]))

(deftest polygon-validation
  (testing "valid polygon"
    (is (nil? (seq (v/validate (poly/polygon :geodetic [(rr/ords->ring :geodetic 0 0, 1 0, 0 1, 0 0)]))))))
  (testing "invalid polygon"
    (is (= [(msg/shape-point-invalid 1 (msg/point-lon-invalid 181))]
           (v/validate (poly/polygon :geodetic [(rr/ords->ring :geodetic 0 0, 181 0, 0 1, 0 0)]))))))