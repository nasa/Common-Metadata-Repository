(ns cmr.spatial.test.generators
  (:require [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen :refer [optional]]
            [pjstadig.assertions :as pj]
            [cmr.spatial.point :as p]
            [cmr.spatial.vector :as v]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.ring :as r]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.line :as l]
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

(def lines
  (ext-gen/model-gen l/line (gen/bind (gen/choose 2 20) non-antipodal-points)))


(def rings
  "Generates rings that are not valid but could be used for testing where validity is not important"
  (gen/fmap
    (fn [points]
      (r/ring (concat points [(first points)])))
    (gen/bind (gen/choose 3 10) non-antipodal-points)))

(def polygons
  "Generates polygons that are not valid but could be used for testing where validity is not important"
  (ext-gen/model-gen poly/polygon (gen/vector rings 1 4)))

(def polygons-without-holes
  "Generates polygons with only an outer ring. The polygons will not be valid."
  (ext-gen/model-gen poly/polygon (gen/tuple rings)))


(def geometries
  "A generator returning individual points, bounding rectangles, lines, and polygons.
  The spatial areas generated will not necessarily be valid."
  (gen/one-of [points mbrs lines polygons]))


;; TODO to properly test rings code it would be ideal if we could generate random rings and validate things related to them
;; Waiting until we add ring validation as this will make this process easier.

; (defn insert-point-at
;   [ring point n]
;   (let [points (:points ring)
;         front (take n points)
;         tail (drop n points)]
;     (r/ring (conj front point tail))))

; (defn self-intersections
;   "Returns a list of ring self intersections"
;   [ring]
;   (let [arcs (:arcs ring)]

;     ))

; (defn ring-valid?
;   [ring]
;   (let [{:keys [points arcs contains-north-pole contains-south-pole]} ring]
;     (and
;       ;; no duplicate points
;       (= (count (distict points)) (count points))
;       ;; contains 1 or fewer poles
;       (not (and contains-north-pole contains-south-pole))
;       ;; starts and ends with same point
;       (= (first points) (last points))
;       ;; doesn't cross itself

;       )))


; (defn add-point-to-ring
;   [ring uniq-points]



;   )


; (def create-ring
;   "Takes an infinite sequence of points and returns a new ring with the number of points given."
;   [points-seq num-points]
;   (pj/assert (> num-points >= 3))
;   (let [uniq-points (distinct points-seq)
;         first-three (take 3 uniq-points)
;         ;; Ring must be closed
;         points-to-add (concat first-three [(first first-three)])
;         ring (r/ring points-to-add)
;         ;; Make sure the ring only contains a single pole
;         ring (if (and (:contains-north-pole ring)
;                       (:contains-south-pole ring))
;                (r/ring (reverse points-to-add))
;                ring)]
;     (loop [ring ring uniq-points (drop 3 uniq-points) points-left (- num-points 3)]
;       (if (points-left <= 0)
;         ring
;         (let [[ring uniq-points] (add-point-to-ring ring uniq-points)]
;           (recur ring uniq-points (dec num-points)))))


; (def rings
;   )


