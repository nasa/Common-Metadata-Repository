(ns cmr.spatial.test.generators
  (:require [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen :refer [optional]]
            [cmr.spatial.point :as p]
            [cmr.spatial.vector :as v]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.arc :as a]))

(def vectors
  (let [vector-num (ext-gen/choose-double -10 10)]
    (ext-gen/model-gen v/new-vector vector-num vector-num vector-num)))

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

(def lat-ranges
  "Tuples containing a latitude range from low to high"
  (gen/bind
    ;; Use latitudes to pick a middle latitude
    (gen/such-that (fn [lat]
                     (and (> lat -90) (< lat 90)))
                   lats)
    (fn [middle-lat]
      ;; Generate a tuple of latitudes around middle
      (gen/tuple
        ;; Pick southern latitude somewhere between -90 and just below middle lat
        (ext-gen/choose-double -90 (- middle-lat 0.0001))
        ;; Pick northern lat from middle lat to 90
        (ext-gen/choose-double middle-lat 90)))))

(def mbrs
  (gen/fmap
    (fn [[[south north] west east]]
      (mbr/mbr west north east south))
    (gen/tuple lat-ranges lons lons)))

(def arcs
  (gen/fmap (fn [[p1 p2]] (a/arc p1 p2))
            (gen/bind
              points
              (fn [p]
                (gen/tuple (gen/return p)
                           (gen/such-that
                             (fn [p2]
                               (and (not= p p2)
                                    (not (p/antipodal? p p2))))
                             points))))))