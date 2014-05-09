(ns cmr.spatial.test.generators
  (:require [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen :refer [optional]]
            [cmr.spatial.point :as p]))

(def lons
  (ext-gen/choose-double -180 180))

(def lats
  (ext-gen/choose-double -90 90))

(def points
  (ext-gen/return-then [(p/point 0 0)]
                       (ext-gen/model-gen p/point lons lats)))

(defn non-antipodal-points [num]
  "A tuple of two points that aren't equal or antipodal to one another"
  (gen/such-that (fn [points]
                   (let [point-set (set points)]
                     (and
                       (= num (count point-set)))
                     (every? #(not (point-set (p/antipodal %))) points)))
                 (apply gen/tuple (repeat num points))))