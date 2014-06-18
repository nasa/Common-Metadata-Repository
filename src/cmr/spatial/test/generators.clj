(ns cmr.spatial.test.generators
  (:require [clojure.test.check.generators :as gen]
            [clojure.string :as str]
            [cmr.common.test.test-check-ext :as ext-gen :refer [optional]]
            [pjstadig.assertions :as pj]
            [primitive-math]
            [cmr.spatial.point :as p]
            [cmr.spatial.vector :as v]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.ring :as r]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.line :as l]
            [cmr.spatial.arc :as a]
            [cmr.spatial.derived :as d]
            [clojure.math.combinatorics :as combo]
            [cmr.spatial.dev.viz-helper :as viz-helper]))

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

(defn non-antipodal-points
  "Returns a generator returning tuples of points of the given size that are not antipodal or equal
  to each other. Optionally takes a point generator to use."
  ([num]
   (non-antipodal-points num points))
  ([num points-gen]
   (gen/such-that (fn [points]
                    (let [point-set (set points)]
                      (and
                        (= num (count point-set))
                        (every? #(not (point-set (p/antipodal %))) points))))
                  (gen/vector points-gen num))))


(def lat-ranges
  "Tuples containing a latitude range from low to high"
  (gen/fmap sort (gen/tuple lats lats)))

(def mbrs
  (gen/fmap
    (fn [[[south north] west east]]
      (m/mbr west north east south))
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

(def polygons-invalid
  "Generates polygons that are not valid but could be used for testing where validity is not important"
  (ext-gen/model-gen poly/polygon (gen/vector rings-invalid 1 4)))

(def polygons-without-holes
  "Generates polygons with only an outer ring. The polygons will not be valid."
  (ext-gen/model-gen poly/polygon (gen/tuple rings-invalid)))

(def geometries
  "A generator returning individual points, bounding rectangles, lines, and polygons.
  The spatial areas generated will not necessarily be valid."
  (gen/one-of [points mbrs lines polygons-invalid]))

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

(defn rings-3-point
  "Generator for 3 point rings. This is used as a base for which to add additional points.
  Takes the point generator function to use for generating points. The function passed in should take
  the number of points requested. Defaults to non-antipodal-points"
  ([]
   (rings-3-point non-antipodal-points))
  ([points-gen-fn]
   (gen/such-that (fn [{:keys [mbr]}]
                    ;; Limit it to rings with MBRs covering less than 99% of the world
                    ;; The reason I'm doing this is because if the whole world is covered except very close
                    ;; to a pole then the MBR of the ring will consider the pole covered.
                    ;; TODO this should be a requirement of the CMR spatial validation.
                    (< (m/percent-covering-world mbr) 99.999))
                  (gen/fmap (fn [points]
                              (let [points (concat points [(first points)])]
                                (reverse-if-both-poles (r/ring points))))
                            (points-gen-fn 3)))))

(defn rings
  "Generator for rings of 3 to 6 points. Takes the point generator function to use for generating
  points. The function passed in should take the number of points requested. Defaults to
  non-antipodal-points"
  ([]
   (rings non-antipodal-points))
  ([points-gen-fn]
   (gen/fmap
     (fn [[ring new-points]]
       (reduce (fn [ring point]
                 (if-let [new-ring (add-point-to-ring ring point)]
                   (reverse-if-both-poles new-ring)
                   ring))
               ring
               new-points))
     (gen/tuple (rings-3-point points-gen-fn) (points-gen-fn 3)))))

(defn print-failed-ring
  "A printer function that can be used with the defspec defined in cmr.common to print out a failed
  ring."
  [type ring]
  ;; Print out the ring in a way that it can be easily copied to the test.
  (println (pr-str (concat `(r/ords->ring) (r/ring->ords ring))))

  (println (str "http://testbed.echo.nasa.gov/spatial-viz/ring_self_intersection?test_point_ordinates=2,2"
                "&ring_ordinates="
                (str/join "," (r/ring->ords ring)))))

(defn print-failed-polygon
  "A printer function that can be used with the defspec defined in cmr.common to print out a failed
  polygon"
  [type polygon]
  ;; Print out the polygon in a way that it can be easily copied to the test.
  (println (pr-str (concat `(poly/polygon)
                           [(vec (map (fn [ring]
                                        (concat `(r/ords->ring) (r/ring->ords ring)))
                                      (:rings polygon)))]))))

(defn points-in-mbr
  "Returns a generator of points in the MBR"
  [mbr]
  (let [double-gen (fn [^double l ^double u]
                     (let [li (int (Math/floor l))
                           ui (int (Math/floor u))
                           ;; Bring in the MBR slightly if possible
                           ^long li (if (< (inc li) ui)
                                      (inc li)
                                      li)
                           ^long ui (if (> (dec ui) li)
                                      (dec ui)
                                      ui)
                           diff (- ui li)
                           [li ui] (cond
                                     (> diff 2) [li ui]
                                     (> ui 0) [(- li (- 3 diff)) ui]
                                     :else [li (+ ui (- 3 diff))])]
                       (ext-gen/choose-double li ui)))
        lon-gen (if (m/crosses-antimeridian? mbr)
                  (gen/one-of (map #(double-gen (:west %) (:east %))
                                   (m/split-across-antimeridian mbr)))
                  (double-gen (:west mbr) (:east mbr)))
        lat-gen (double-gen (:south mbr) (:north mbr))]
    (gen/such-that (partial m/covers-point? mbr)
                   (gen/fmap (partial apply p/point)
                             (gen/tuple lon-gen lat-gen))
                   50)))

(defn rings-in-ring
  "Creates a generator of rings within the given ring. Useful for creating holes in a polygon. This is
  an expensive generator. It works by generating points in the MBR of the ring. It uses those
  points to build valid internal rings. Then each ring is only included if none of the arcs
  of the ring intersect."
  [ring]
  (let [{ring-arcs :arcs
         contains-np :contains-north-pole
         contains-sp :contains-south-pole} ring]
    (gen/such-that
      (fn [potential-ring]
        (and
          ;; Checks that arcs do not intersect
          (not-any? (fn [a1]
                      (some (partial a/intersects? a1) ring-arcs))
                    (:arcs potential-ring))
          ;; and that pole containment is allowed
          (or (and (not contains-np)
                   (not contains-sp)
                   (not (:contains-north-pole potential-ring))
                   (not (:contains-south-pole potential-ring)))
              ;; The potential ring can't contain the same pole as the boundary.
              (and contains-np
                   (not (:contains-north-pole potential-ring)))
              (and contains-sp
                   (not (:contains-south-pole potential-ring))))))
      (rings
        ;; The function passed in builds sets of points that are non-antipodal and in the ring.
        (fn [num-points]
          (non-antipodal-points
            num-points
            ;; points in ring
            (gen/such-that (partial r/covers-point? ring)
                           (points-in-mbr (:mbr ring))
                           ;; Number of points such-that will try in a row before giving up.
                           500))))

      ;; Number of rings in a row we could attempt to generate that don't intersect the parent
      ;; before giving up.
      1000)))

(def polygons-with-holes
  "Generator for polygons with holes"
  (gen/fmap (fn [[outer-boundary potential-holes]]
              ;; The holes can go in the polygon if they don't intersect any of the other holes
              (let [[h1 h2 h3] potential-holes
                    ;; h2 can be used if it doesn't intersect h1
                    h2-valid? (not (r/intersects-ring? h1 h2))
                    ;; h3 can be used if it doesn't intersect h1 or h2 (if h2 is valid)
                    h3-valid? (and (not (r/intersects-ring? h1 h3))
                                   (or (not h2-valid?)
                                       (not (r/intersects-ring? h2 h3))))
                    holes (cond
                            (and h2-valid? h3-valid?) [h1 h2 h3]
                            h2-valid? [h1 h2]
                            h3-valid? [h1 h3]
                            :else [h1])]
                (poly/polygon (cons outer-boundary holes))))
            ;; Generates tuples of outer boundaries along with holes that are in the boundary.
            (gen/bind
              (rings)
              (fn [outer-boundary]
                (gen/tuple (gen/return outer-boundary)
                           ;; potential holes
                           (gen/vector (rings-in-ring outer-boundary) 3))))))

