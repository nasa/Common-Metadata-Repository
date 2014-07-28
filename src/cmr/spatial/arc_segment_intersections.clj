(ns cmr.spatial.arc-segment-intersections
  "Provides intersection functions for finding the intersection of spherical arcs and cartesian segments"
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.arc :as a]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.segment :as s]
            [cmr.common.services.errors :as errors]
            [cmr.spatial.math :refer :all]
            [primitive-math]
            [pjstadig.assertions :as pj])
  (:import cmr.spatial.arc.Arc
           cmr.spatial.segment.LineSegment
           cmr.spatial.point.Point))
(primitive-math/use-primitive-operators)


;; TODO performance test the intersections
;; The line mbr check may help improve things

(defn create-gc-fn
  "TODO"
  [^Arc arc]
  (let [{:keys [^Point west-point ^Point east-point]} arc
        lon1 (.lon-rad west-point)
        lon2 (.lon-rad east-point)
        lat1 (.lat-rad west-point)
        lat2 (.lat-rad east-point)
        cos-lat1 (cos lat1)
        cos-lat2 (cos lat2)
        ;; From http://williams.best.vwh.net/avform.htm#Int
        ;; top = sin(lat1) * cos(lat2) * sin(x - lon2) - sin(lat2) * cos(lat1) * sin(x - lon1)
        ;; bottom = cos(lat1) * cos(lat2) * sin(lon1 - lon2)
        ;; y = atan(top/bottom)
        ;; Constants factored out:
        ;; y = atan( ( a * sin(x - lon2) - b * sin(x - lon1) ) / bottom )
        ;; Constants:
        a (* (sin lat1) cos-lat2)
        b (* (sin lat2) cos-lat1)
        bottom (* cos-lat1 cos-lat2 (sin (- lon1 lon2)))]

    (fn ^double [^double x]
      (let [x-rad (radians x)
            a-part (* a (sin (- x-rad lon2)))
            b-part (* b (sin (- x-rad lon1)))
            top (- a-part b-part)]
        (degrees (atan (/ top bottom)))))))

(defn create-line-fn
  "TODO"
  [ls]
  (let [{:keys [^double m ^double b]} ls]
    (fn ^double [^double x]
      (+ (* m x) b))))

(defn create-diff-fn
  "TODO"
  [ls arc]
  (let [gc-fn (create-gc-fn arc)
        line-fn (create-line-fn ls)]
    (fn ^double [^double x]
      (- ^double (gc-fn x) ^double (line-fn x)))))

(defn create-gc-derivative-fn
  "Creates a function that is the derivative of the great circle function."
  [^Arc arc]
  (let [{:keys [^Point west-point ^Point east-point]} arc
        lon1 (.lon-rad west-point)
        lon2 (.lon-rad east-point)
        lat1 (.lat-rad west-point)
        lat2 (.lat-rad east-point)
        cos-lat1 (cos lat1)
        cos-lat2 (cos lat2)
        ;; Constants:
        q (* (sin lat1) cos-lat2)
        w (* (sin lat2) cos-lat1)
        g (* cos-lat1 cos-lat2 (sin (- lon1 lon2)))]

    (fn ^double [^double x]
      (let [x-rad (radians x)
            ;; TODO document in infix notation what the derivative is.
            top (- (* q (cos (- lon2 x-rad))) (* w (cos (- lon1 x-rad))))
            inner (- (* w (sin (- lon1 x-rad))) (* q (sin (- lon2 x-rad))))
            bottom (+ g (/ (sq inner) g))]
        (/ top bottom)))))

(defn create-diff-derivative-fn
  "TODO"
  [ls arc]
  (let [;; The derivative of y = m * x + b is y = m
        ^double m (:m ls)
        gc-deriv (create-gc-derivative-fn arc)]
    (fn ^double [^double x]
      ;; The derivative of gc(x) - line(x) = gc'(x) - line'(x)
      (- ^double (gc-deriv x) m))))

(defn create-barrier-fn
  "TODO"
  [wrapped-fn mbr]
  (let [{:keys [^double west ^double east]} mbr
        ;; The barrier function is x^2
        normal-barrier-fn sq
        inverted-barrier-fn #(* -1.0 ^double (normal-barrier-fn %))
        ;; If the result is less than 0 we invert the barrier function result to slope down
        east-barrier-fn (if (< ^double (wrapped-fn east) 0.0) inverted-barrier-fn normal-barrier-fn)
        west-barrier-fn (if (< ^double (wrapped-fn west) 0.0) inverted-barrier-fn normal-barrier-fn)]
    (fn [^double x]
      (let [^double existing-result (wrapped-fn x)]
        (cond
          (> x east)
          ;; east is subtracted to shift result to that longitude.
          (+ existing-result ^double (east-barrier-fn (- x east)))

          (< x west)
          ;; west is subtracted to shift result to that longitude.
          (+ existing-result ^double (west-barrier-fn (- x west)))

          :else
          existing-result)))))

(defn create-barrier-derivative-fn
  "TODO"
  [wrapped-fn wrapped-deriv-fn mbr]
  (let [{:keys [^double west ^double east]} mbr
        ;; The barrier function is x^2 so it's derivative is 2*x
        normal-d-barrier-fn #(* 2.0 ^double %)
        ;; If the result is less than 0 we invert the barrier function result to slope down
        inverted-d-barrier-fn #(* -1.0 ^double (normal-d-barrier-fn %))
        ;; If the result is less than 0 we invert the barrier function result to slope down
        east-barrier-fn (if (< ^double (wrapped-fn east) 0.0) inverted-d-barrier-fn normal-d-barrier-fn)
        west-barrier-fn (if (< ^double (wrapped-fn west) 0.0) inverted-d-barrier-fn normal-d-barrier-fn)]
    (fn [^double x]
      (let [^double existing-result (wrapped-deriv-fn x)]
        (cond
          (> x east)
          ;; east is subtracted to shift result to that longitude.
          (+ existing-result ^double (east-barrier-fn (- x east)))

          (< x west)
          ;; west is subtracted to shift result to that longitude.
          (+ existing-result ^double (west-barrier-fn (- x west)))

          :else
          existing-result)))))

(def ^:const ^double CONVERGENCE_DIFF
  "The difference to use when determining if Newton's method has converged."
  0.000001)

(defn- intersection-with-newtons-method
  "TODO"
  [ls arc mbrs]
  (let [diff-fn (create-diff-fn ls arc)
        diff-deriv-fn (create-diff-derivative-fn ls arc)
        xs (for [mbr mbrs]
             (let [b-diff-fn (create-barrier-fn diff-fn mbr)
                   b-diff-deriv-fn (create-barrier-derivative-fn diff-fn diff-deriv-fn mbr)
                   next-x (fn ^double [^double xn]
                            (- xn (/ ^double (b-diff-fn xn)
                                     ^double (b-diff-deriv-fn xn))))
                   x0 (mid-lon (:west mbr) (:east mbr))]
               (loop [xn x0 depth 0]
                 (when (< depth 50) ; for values greater than this we consider that they don't intersect
                   (let [^double xn+1 (next-x xn)
                         diff (abs (- xn+1 xn))]
                     (if (< diff CONVERGENCE_DIFF)
                       xn+1
                       ;; This double cast here is to avoid warning:  recur arg for primitive local: xn is not matching primitive, had: Object, needed: double
                       ;; I'm not sure how to avoid it otherwise.
                       (recur (double xn+1) (inc depth))))))))]
    (filter identity (map (fn [lon]
                            (when-let [lat (s/segment+lon->lat ls lon)]
                              (p/point lon lat)))
                          (filter identity xs)))))

(defn intersection-with-densification
  "Performs the intersection between a line segment and the arc using densification of the line segment"
  [ls arc mbrs]
  (let [line-segments (filter identity (map (partial s/subselect ls) mbrs))
        lines (mapv s/line-segment->line line-segments)
        arcs (map (partial apply a/arc) (mapcat #(partition 2 1 (:points %)) lines))]
    (mapcat (partial a/intersections arc) arcs)))

(comment

(do
  (def arc (cmr.spatial.arc/ords->arc -180.0 84.0000029239881 -29.944444444444443, -25.833333333333332))
  (def ls (cmr.spatial.segment/ords->line-segment 106.96, -40.0, -135.1, 4.95))

  (def mbr (first (m/intersections (:mbr ls) (:mbr1 arc))))

  (def diff-fn (create-diff-fn ls arc))
  (def diff-deriv-fn (create-diff-derivative-fn ls arc))

  (def b-diff-fn (create-barrier-fn diff-fn mbr))
  (def b-diff-deriv-fn (create-barrier-derivative-fn diff-fn diff-deriv-fn mbr))
  (def next-x (fn ^double [^double xn]
                (- xn (/ ^double (b-diff-fn xn)
                         ^double (b-diff-deriv-fn xn)))))
  (def x0 (mid-lon (:west mbr) (:east mbr))))

(loop [x x0 results [] depth 50]
  (if (= depth 0)
    results
    (let [x (next-x x)
          results (conj results x)]
      (recur x results (dec depth)))))

(intersection-with-newtons-method ls arc [mbr])

(diff-fn -82.52222222222221)
(diff-deriv-fn -82.52222222222221)
(b-diff-fn -82.52222222222221)
(b-diff-deriv-fn -82.52222222222221)

(next-x x0)

(- x0 (/ ^double (b-diff-fn x0)
         ^double (b-diff-deriv-fn x0)))

(def x1 (next-x x0))

(b-diff-fn x1)
(b-diff-deriv-fn x1)

)


(defn- vertical-arc-line-segment-intersections
  "Determines the intersection points of a vertical arc and a line segment"
  [ls arc]
  (let [;; convert the arc into a set of equivalent line segments.
        point1 (:west-point arc)
        point2 (:east-point arc)
        arc-segments (cond
                       ;; A vertical arc could cross a pole. It gets divided in half at the pole in that case.
                       (a/crosses-north-pole? arc)
                       [(s/line-segment point1 p/north-pole)
                        (s/line-segment point2 p/north-pole)]

                       (a/crosses-south-pole? arc)
                       [(s/line-segment point1 p/south-pole)
                        (s/line-segment point2 p/south-pole)]

                       :else
                       [(s/line-segment point1 point2)])]
    (filter identity (map (partial s/intersection ls) arc-segments))))

(defn intersections
  "Returns a list of the points where the line segment intersects the arc."
  [ls arc]

  (let [ls-mbr (:mbr ls)
        arc-mbrs (mapcat m/split-across-antimeridian (a/mbrs arc))
        intersecting-mbrs (seq (filter (partial m/intersects-br? ls-mbr)
                                       arc-mbrs))]
    (when intersecting-mbrs
      (cond

        (s/vertical? ls)
        ;; Treat as line segment as a vertical arc.
        (a/intersections arc (a/arc (:point1 ls) (:point2 ls)))


        (s/horizontal? ls)
        ;; Use arc and latitude segment intersection implementation
        (let [lat (-> ls :point1 :lat)
              [west east] (p/order-longitudes (get-in ls [:point1 :lon]) (get-in ls [:point2 :lon]))]
          (a/lat-segment-intersections arc lat west east))

        (a/vertical? arc)
        (vertical-arc-line-segment-intersections ls arc)

        :else
        (intersection-with-densification
          ls arc
          ;; Compute the intersections of the intersecting mbrs. Smaller mbrs around the intersection
          ;; point will result in better bounding for newton's method.
          (mapcat (partial m/intersections ls-mbr) intersecting-mbrs))

        ;; Disabling newton's method due to issues with it
        #_(intersection-with-newtons-method
            ls arc
            ;; Compute the intersections of the intersecting mbrs. Smaller mbrs around the intersection
            ;; point will result in better bounding for newton's method.
            (mapcat (partial m/intersections ls-mbr) intersecting-mbrs))))))



