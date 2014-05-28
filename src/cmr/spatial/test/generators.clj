(ns cmr.spatial.test.generators
  (:require [clojure.test.check.generators :as gen]
            [clojure.string :as str]
            [cmr.common.test.test-check-ext :as ext-gen :refer [optional]]
            [pjstadig.assertions :as pj]
            [primitive-math]
            [cmr.spatial.point :as p]
            [cmr.spatial.vector :as v]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.ring :as r]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.line :as l]
            [cmr.spatial.arc :as a]
            [cmr.spatial.derived :as d]
            [clojure.math.combinatorics :as combo]))

(primitive-math/use-primitive-operators)

(def vectors
  (let [vector-num (ext-gen/choose-double -10 10)]
    (ext-gen/model-gen v/new-vector vector-num vector-num vector-num)))

(def lons
  (ext-gen/choose-double -180 180))

(def lats
  (ext-gen/choose-double -90 90))

(def points
  (ext-gen/model-gen p/point lons lats))

(defn non-antipodal-points [num]
  "A tuple of two points that aren't equal or antipodal to one another"
  (gen/such-that (fn [points]
                   (let [point-set (set points)]
                     (and
                       (= num (count point-set))
                       (every? #(not (point-set (p/antipodal %))) points)
                       ()
                       )))
                 (apply gen/tuple (repeat num points))))

(def lat-ranges
  "Tuples containing a latitude range from low to high"
  (gen/fmap sort (gen/tuple lats lats)))

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

(def rings-invalid
  "Generates rings that are not valid but could be used for testing where validity is not important"
  (gen/fmap
    (fn [points]
      (r/ring (concat points [(first points)])))
    (gen/bind (gen/choose 3 10) non-antipodal-points)))

(def polygons
  "Generates polygons that are not valid but could be used for testing where validity is not important"
  (ext-gen/model-gen poly/polygon (gen/vector rings-invalid 1 4)))

(def polygons-without-holes
  "Generates polygons with only an outer ring. The polygons will not be valid."
  (ext-gen/model-gen poly/polygon (gen/tuple rings-invalid)))

(def geometries
  "A generator returning individual points, bounding rectangles, lines, and polygons.
  The spatial areas generated will not necessarily be valid."
  (gen/one-of [points mbrs lines polygons]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Ring generation functions

(defn reverse-if-both-poles
  "Takes a ring and reverses it if it contains both poles"
  [ring]
  (let [ring (d/calculate-derived ring)]
    (if (r/contains-both-poles? ring)
      (d/calculate-derived (r/invert ring))
      ring)))

(defn insert-point-at
  "Inserts a point at the specified location in the ring if the point to be added is not equal to
  or antipodal to the points it's between. Returns nil if it would be invalid"
  [ring point n]
  (let [valid-consecutive (fn [p1 p2]
                            (and (not= p1 p2)
                                 (not (p/antipodal? p1 p2))
                                 (< (p/angular-distance p1 p2) 180.0)))
        points (drop-last (:points ring))
        front (take n points)
        tail (drop n points)
        points (concat front [point] tail)]
    (when (and (valid-consecutive point (last front))
               (valid-consecutive point (or (first tail)
                                            (first points))))
      (r/ring (concat points [(first points)])))))

(defn add-point-to-ring
  "Tries to add the point to the ring. If it can't returns nil"
  [ring point]
  (let [points (:points ring)]
    ;; Only use this point if it's not already in the ring
    (when-not (some (partial = point) points)
      (loop [pos 1]
        (if (= pos (count points))
          ;; We couldn't find a spot to add the point. Return nil
          nil
          (if-let [new-ring (insert-point-at ring point pos)]
            (let [new-ring (d/calculate-derived new-ring)
                  self-intersections (r/self-intersections new-ring)]
              (if (and (empty? self-intersections)
                       (not (r/contains-both-poles? new-ring)))
                new-ring
                (recur (inc pos))))
            (recur (inc pos))))))))

(def rings-3-point
  "Generator for 3 point rings. This is used as a base for which to add additional points"
  (gen/such-that (fn [{:keys [mbr]}]
                   ;; Limit it to rings with MBRs covering less than 99% of the world
                   ;; The reason I'm doing this is because if the whole world is covered except very close
                   ;; to a pole then the MBR of the ring will consider the pole covered.
                   ;; TODO this should be a requirement of the CMR spatial validation.
                   (< (mbr/percent-covering-world mbr) 99.999))
                 (gen/fmap (fn [points]
                             (let [points (concat points [(first points)])]
                               (reverse-if-both-poles (r/ring points))))
                           (non-antipodal-points 3))))

(def rings
  "Generator for rings of 3 to 6 points"
  (gen/fmap
    (fn [[ring new-points]]
      (reduce (fn [ring point]
                (if-let [new-ring (add-point-to-ring ring point)]
                  (reverse-if-both-poles new-ring)
                  ring))
              ring
              new-points))
    (gen/tuple rings-3-point (non-antipodal-points 3))))

(defn print-failed-ring
  "A printer function that can be used with the defspec defined in cmr.common to print out a failed
  ring."
  [type ring]
  ;; Print out the ring in a way that it can be easily copied to the test.
  (println (pr-str (concat '(r/ring)
                           [(vec (map
                                   #(list 'p/point (:lon %) (:lat %))
                                   (:points ring)))])))

  (println (str "http://testbed.echo.nasa.gov/spatial-viz/ring_self_intersection?test_point_ordinates=2,2"
                "&ring_ordinates="
                (str/join "," (r/ring->ords ring)))))
