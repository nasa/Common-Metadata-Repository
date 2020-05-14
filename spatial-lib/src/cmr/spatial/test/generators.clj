(ns cmr.spatial.test.generators
  (:require
   [clojure.math.combinatorics :as combo]
   [clojure.string :as string]
   [clojure.test.check.generators :as gen]
   [cmr.common.test.test-check-ext :as ext-gen :refer [optional]]
   [cmr.spatial.arc :as a]
   [cmr.spatial.arc-line-segment-intersections :as asi]
   [cmr.spatial.circle :as spatial-circle]
   [cmr.spatial.derived :as d]
   [cmr.spatial.geodetic-ring :as gr]
   [cmr.spatial.line-segment :as s]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.ring-relations :as rr]
   [cmr.spatial.vector :as v]
   [pjstadig.assertions :as pj]
   [primitive-math]))

(comment
  ;; If you have trouble reloading this namespace, evaluate the
  ;; following expression:
  (primitive-math/unuse-primitive-operators))

(primitive-math/use-primitive-operators)

(def coordinate-system
  (gen/elements [:geodetic :cartesian]))

(def vectors
  (let [vector-num (ext-gen/choose-double -10 10)]
    (ext-gen/model-gen v/new-vector vector-num vector-num vector-num)))

(def lons
  (ext-gen/choose-double -180 180))

(def lats
  (ext-gen/choose-double -90 90))

(def radius
  (ext-gen/choose-double spatial-circle/MIN_RADIUS spatial-circle/MAX_RADIUS))

(def points
  (ext-gen/model-gen p/point lons lats))

(def line-segments
  (let [non-equal-point-pairs
        (gen/bind
          points
          (fn [point]
            (gen/tuple (gen/return point)
                       (gen/such-that (partial not= point) points))))]
    (gen/fmap (partial apply s/line-segment) non-equal-point-pairs)))

(defn print-failed-line-segments
  "A printer function that can be used with the defspec defined in cmr.common to print out a failed
  line segment"
  [type & lss]
  ;; Print out the line segment in a way that it can be easily copied to the test.
  (doseq [ls lss]
    (println (pr-str (concat `(s/ords->line-segment) (s/line-segment->ords ls))))))

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

(def circles
  (ext-gen/model-gen spatial-circle/circle lons lats radius))

(def lat-ranges
  "Tuples containing a latitude range from low to high"
  (gen/fmap sort (gen/tuple lats lats)))

(defn print-failed-mbrs
  [type & mbrs]
  (doseq [br mbrs :let [{:keys [west north east south]} br]]
    (println (pr-str (concat `(m/mbr) [west north east south])))))

(def mbrs
  (gen/fmap
    (fn [[[south north] west east]]
      (m/mbr west north east south))
    (gen/tuple lat-ranges lons lons)))

(def mbrs-not-crossing-antimeridian
  (gen/fmap
    (fn [[[south north] [west east]]]
      (m/mbr west north east south))
    (gen/tuple lat-ranges (gen/fmap sort (gen/tuple lons lons)))))

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

(defn print-failed-arc
  "A printer function that can be used with the defspec defined in cmr.common"
  [type arc]
  ;; Print out the ring in a way that it can be easily copied to the test.
  (println (pr-str (concat `(a/ords->arc) (a/arc->ords arc)))))

(def lines
  (gen/fmap (partial apply l/line-string)
            (gen/bind coordinate-system
                      (fn [coord-sys]
                        (gen/tuple (gen/return coord-sys) (gen/bind (gen/choose 2 6) non-antipodal-points))))))

(def cartesian-lines
  (gen/fmap (partial l/line-string :cartesian)
            (gen/bind (gen/choose 2 6) non-antipodal-points)))

(defn rings-invalid
  "Generates rings that are not valid but could be used for testing where validity is not important"
  [coord-sys]
  (gen/fmap
    (fn [points]
      (rr/ring coord-sys (concat points [(first points)])))
    (gen/bind (gen/choose 3 10) non-antipodal-points)))

(def polygons-invalid
  "Generates polygons that are not valid but could be used for testing where validity is not important"
  (gen/fmap (partial apply poly/polygon)
            (gen/bind coordinate-system
                      (fn [coord-sys]
                        (gen/tuple (gen/return coord-sys) (gen/vector (rings-invalid coord-sys) 1 4))))))

(def geodetic-polygons-without-holes
  "Generates polygons with only an outer ring. The polygons will not be valid."
  (gen/fmap (partial apply poly/polygon :geodetic)
            (gen/tuple (gen/tuple (rings-invalid :geodetic)))))

(def geometries
  "A generator returning individual points, bounding rectangles, lines, and polygons.
  The spatial areas generated will not necessarily be valid."
  (gen/one-of [points mbrs lines polygons-invalid]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Ring generation functions

(defn reverse-if-inside-out
  "Takes a ring and reverses it if it is inside out."
  [ring]
  (let [ring (d/calculate-derived ring)]
    (if (rr/inside-out? ring)
      (d/calculate-derived (rr/invert ring))
      ring)))

(defmulti valid-consecutive-points?
  "Returns true if the points are valid consecutive points in the given coordinate system."
  (fn [coord-sys p1 p2]
    coord-sys))

(defmethod valid-consecutive-points? :geodetic
  [coord-sys p1 p2]
  (and (not= p1 p2)
       (not (p/antipodal? p1 p2))
       (< (p/angular-distance p1 p2) 180.0)))

(defmethod valid-consecutive-points? :cartesian
  [coord-sys p1 p2]
  (not= p1 p2))

(defn insert-point-at
  "Inserts a point at the specified location in the ring if the point to be added is not equal to
  or antipodal to the points it's between. Returns nil if it would be invalid"
  [ring point n]
  (let [coord-sys (rr/coordinate-system ring)
        points (drop-last (:points ring))
        front (take n points)
        tail (drop n points)
        points (concat front [point] tail)]
    (when (and (valid-consecutive-points? coord-sys point (last front))
               (valid-consecutive-points? coord-sys point (or (first tail)
                                                              (first points))))
      (rr/ring coord-sys (concat points [(first points)])))))

(defn add-point-to-ring
  "Tries to add the point to the ring. If it can't returns nil"
  [ring point]
  (let [points (:points ring)]
    ;; Only use this point if it's not already in the ring
    (when-not (some (partial = point) points)
      (loop [pos 1]
        ;; If we can't find a spot to add the point, return nil
        (when-not (= pos (count points))
          (if-let [new-ring (insert-point-at ring point pos)]
            (let [new-ring (d/calculate-derived new-ring)
                  self-intersections (rr/self-intersections new-ring)]
              (if (and (empty? self-intersections)
                       (not (rr/inside-out? new-ring)))
                new-ring
                (recur (inc pos))))
            (recur (inc pos))))))))

(defn rings-3-point
  "Generator for 3 point rings. This is used as a base for which to add additional points.
  Takes the point generator function to use for generating points. The function passed in should take
  the number of points requested. Defaults to non-antipodal-points"
  ([coord-sys]
   (rings-3-point coord-sys non-antipodal-points))
  ([coord-sys points-gen-fn]
   (gen/such-that (fn [{:keys [mbr]}]
                    ;; Limit it to rings with MBRs covering less than 99% of the world
                    ;; The reason I'm doing this is because if the whole world is covered except very close
                    ;; to a pole then the MBR of the ring will consider the pole covered.
                    ;; This should be a requirement of the CMR spatial validation.
                    (< (m/percent-covering-world mbr) 99.999))
                  (gen/fmap (fn [points]
                              (let [points (concat points [(first points)])]
                                (reverse-if-inside-out (rr/ring coord-sys points))))
                            (points-gen-fn 3)))))

(defn rings
  "Generator for rings of 3 to 6 points. Takes the point generator function to use for generating
  points. The function passed in should take the number of points requested. Defaults to
  non-antipodal-points"
  ([coord-sys]
   (rings coord-sys non-antipodal-points))
  ([coord-sys points-gen-fn]
   (gen/fmap
     (fn [[ring new-points]]
       (reduce (fn [ring point]
                 (if-let [new-ring (add-point-to-ring ring point)]
                   (reverse-if-inside-out new-ring)
                   ring))
               ring
               new-points))
     (gen/tuple (rings-3-point coord-sys points-gen-fn) (points-gen-fn 3)))))

(defn print-failed-ring
  "A printer function that can be used with the defspec defined in cmr.common to print out a failed
  ring."
  [type ring]
  (let [coord-sys (rr/coordinate-system ring)]
    ;; Print out the ring in a way that it can be easily copied to the test.
    (println (pr-str (concat `(rr/ords->ring ~coord-sys) (rr/ring->ords ring))))

    (when (= coord-sys :geodetic)
      (println (str "http://testbed.echo.nasa.gov/spatial-viz/ring_self_intersection?test_point_ordinates=2,2"
                    "&ring_ordinates="
                    (string/join "," (rr/ring->ords ring)))))))

(defn print-failed-polygon
  "A printer function that can be used with the defspec defined in cmr.common to print out a failed
  polygon"
  [type polygon]
  ;; Print out the polygon in a way that it can be easily copied to the test.
  (println (pr-str (concat `(poly/polygon ~(:coordinate-system polygon))
                           [(vec (map (fn [ring]
                                        (concat `(rr/ords->ring ~(rr/coordinate-system ring))
                                                (rr/ring->ords ring)))
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
                                     (pos? ui) [(- li (- 3 diff)) ui]
                                     :else [li (+ ui (- 3 diff))])]
                       (ext-gen/choose-double li ui)))
        lon-gen (if (m/crosses-antimeridian? mbr)
                  (gen/one-of (map #(double-gen (:west %) (:east %))
                                   (m/split-across-antimeridian mbr)))
                  (double-gen (:west mbr) (:east mbr)))
        lat-gen (double-gen (:south mbr) (:north mbr))]
    (gen/such-that (partial m/geodetic-covers-point? mbr)
                   (gen/fmap (partial apply p/point)
                             (gen/tuple lon-gen lat-gen))
                   50)))

(defn rings-in-ring
  "Creates a generator of rings within the given ring. Useful for creating holes in a polygon. This is
  an expensive generator. It works by generating points in the MBR of the ring. It uses those
  points to build valid internal rings. Then each ring is only included if none of the arcs
  of the ring intersect."
  [ring]
  (let [{contains-np :contains-north-pole
         contains-sp :contains-south-pole} ring]
    (gen/such-that
      (fn [potential-ring]
        (and
          ;; Checks that lines do not intersect
          (not-any? (fn [a1]
                      (some (partial asi/intersects? a1) (rr/segments ring)))
                    (rr/segments potential-ring))
          ;; and that pole containment matches
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
        (rr/coordinate-system ring)
        ;; The function passed in builds sets of points that are non-antipodal and in the ring.
        (fn [num-points]
          (non-antipodal-points
            num-points
            ;; points in ring
            (gen/such-that (partial rr/covers-point? ring)
                           (points-in-mbr (:mbr ring))
                           ;; Number of points such-that will try in a row before giving up.
                           500))))

      ;; Number of rings in a row we could attempt to generate that don't intersect the parent
      ;; before giving up.
      1000)))

(defn polygons-with-holes'
  [coord-sys]
  (gen/fmap (fn [[outer-boundary potential-holes]]
              ;; The holes can go in the polygon if they don't intersect any of the other holes
              (let [[h1 h2 h3] potential-holes
                  ;; h2 can be used if it doesn't intersect h1
                    h2-valid? (not (rr/intersects-ring? h1 h2))
                  ;; h3 can be used if it doesn't intersect h1 or h2 (if h2 is valid)
                    h3-valid? (and (not (rr/intersects-ring? h1 h3))
                                   (or (not h2-valid?)
                                       (not (rr/intersects-ring? h2 h3))))
                    holes (cond
                            (and h2-valid? h3-valid?) [h1 h2 h3]
                            h2-valid? [h1 h2]
                            h3-valid? [h1 h3]
                            :else [h1])]
               (poly/polygon coord-sys (cons outer-boundary holes))))
          ;; Generates tuples of outer boundaries along with holes that are in the boundary.
          (gen/bind
           (rings coord-sys)
           (fn [outer-boundary]
             (gen/tuple (gen/return outer-boundary)
                        ;; potential holes
                        (gen/vector (rings-in-ring outer-boundary) 3))))))

(def polygons-with-holes
  "Generator for polygons with holes"
  (gen/bind coordinate-system polygons-with-holes'))

(def cartesian-polygons-with-holes
  (gen/bind (gen/return :cartesian) polygons-with-holes'))
