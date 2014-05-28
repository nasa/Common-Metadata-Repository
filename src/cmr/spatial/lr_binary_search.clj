(ns cmr.spatial.lr-binary-search
  "Prototype code that finds the largest interior rectangle of a ring."
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.ring :as r]
            [cmr.spatial.math :refer :all]
            [primitive-math]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.conversion :as c]
            [cmr.common.services.errors :as errors]
            [cmr.spatial.arc :as a]
            [cmr.spatial.derived :as d]
            [clojure.math.combinatorics :as combo]
            [cmr.common.util :as util])
  (:import cmr.spatial.mbr.Mbr))
(primitive-math/use-primitive-operators)

(def ^:const ^long max-search-depth
  "The number of times the binary search can iterate before we choose the best option."
  10)

(defn mid-br
  "Returns an mbr midway between inner and outer mbrs"
  [inner outer]
  (let [{^double ni :north ^double si :south ^double ei :east ^double wi :west} inner
        {^double no :north ^double so :south ^double eo :east ^double wo :west} outer]
    (m/mbr (mid-lon wo wi)
           (mid ni no)
           (mid-lon ei eo)
           (mid si so))))

(defn- even-lr-search
  "Searches for an LR from the given point in the ring. The LR will grown evently from the point
  given with the same shape as the outer MBR."
  [ring from-point]
  (when (r/covers-point? ring from-point) ; Only continue if the seed point is in the ring.
    (let [{:keys [lon lat]} from-point
          minv (m/mbr lon lat lon lat)
          maxv (:mbr ring)]
      (util/binary-search
        minv maxv mid-br
        (fn [current-br inner-br outer-br ^long depth]
          (let [current-in-ring (r/covers-br? ring current-br)]
            (if (> depth max-search-depth)
              ;; Exceeded the recursion depth. Take our best choice
              (if current-in-ring current-br inner-br)
              (if current-in-ring :less-than :greater-than))))))))

(defn split-mbr
  "Splits an MBR into 4 sub MBRs at their center point"
  [^Mbr mbr point]
  (let [{:keys [lon lat]} point
        w (.west mbr)
        n (.north mbr)
        e (.east mbr)
        s (.south mbr)]
    [(m/mbr w n lon lat)
     (m/mbr lon n e lat)
     (m/mbr w lat lon s)
     (m/mbr lon lat e s)]))

(defn mbr->vert-dividing-arc
  "Returns an arc dividing the MBR in two vertically"
  [^Mbr mbr]
  (let [w (.west mbr)
        n (.north mbr)
        e (.east mbr)
        s (.south mbr)
        mid (mid-lon w e)]
    (a/arc (p/point mid n) (p/point mid s))))


(def north-pole (p/point 0 90))

(def south-pole (p/point 0 -90))

(defmulti ring->lr-search-points
  "TODO"
  (fn [ring]
    (let [points (:points ring)]
      (cond
        (some p/is-pole? points) :on-pole
        :else :default))))

;; TODO may not be needed
; (defn ring->arc-points-not-on-pole
;   "Returns the points from the ring that are from arcs that do not have a point on the pole"
;   [ring]
;   (apply concat
;          (filter (fn [[west-point east-point]]
;                    (not (or (p/is-pole? west-point)
;                             (p/is-pole? east-point))))
;                  (map a/arc->points (:arcs ring)))))

(defn ring->triangles
  "Converts a ring into a series of smaller triangles. Triangles do not necessarily represent
  the area of the ring."
  [ring]
  (let [points (:points ring)]
    (if (= 4 (count points))
      ;; Already a triangle
      [ring]
      (map (fn [[p1 p2 p3]]
             (let [tri (d/calculate-derived (r/ring [p1 p2 p3 p1]))]
               (if (r/contains-both-poles? tri)
                 (d/calculate-derived (r/invert tri))
                 tri)))
           ;; If contains two poles reverse it
           (partition 3 1 (concat points [(second points)]))))))

(defn triangle->point
  "Returns a point from a ring that is a triangle. Works be creating an arc from the midpoint of one
  arc to its opposite point."
  [ring]
  (a/midpoint (a/arc (first (:points ring))
                     (a/midpoint (second (:arcs ring))))))

(defmethod ring->lr-search-points :on-pole
  [ring]
  (map triangle->point (ring->triangles ring)))

;; TODO may not need these
; (defmethod ring->lr-search-points :on-north-pole
;   [ring]
;   (map (comp a/midpoint (partial a/arc north-pole))
;        (ring->arc-points-not-on-pole ring)))

; (defmethod ring->lr-search-points :on-south-pole
;   [ring]
;   (map (comp a/midpoint (partial a/arc south-pole))
;        (ring->arc-points-not-on-pole ring)))

(defmethod ring->lr-search-points :default
  [ring]
  (let [{:keys [mbr contains-north-pole contains-south-pole]} ring
        vert-arc (mbr->vert-dividing-arc mbr)

        ;; Find the intersections of the arc through the ring. It will intersect an even number of times.
        intersections (mapcat (partial a/intersections vert-arc) (:arcs ring))

        ;; Add pole to intersections if it contains a pole
        intersections (set (concat intersections
                                   (when contains-north-pole [north-pole])
                                   (when contains-south-pole [south-pole])))]
    ;; Between at least 2 of the points there will be a midpoint in the ring.
    ;; Create a list of test points from midpoints of all combinations of points.
    (map (fn [[p1 p2]]
           (a/midpoint (a/arc p1 p2)))
         (combo/combinations intersections 2))))



(defn find-lr
  "TODO"
  [ring]
  (let [ring (d/calculate-derived ring)
        {:keys [mbr contains-north-pole contains-south-pole]} ring]
    (or
      ;; Find an LR from the center point of the ring
      (even-lr-search ring (m/center-point mbr))
      ;; Return the first LR that we can find using a test point
      (first (filter identity
                     (map (partial even-lr-search ring)
                          (ring->lr-search-points ring)))))))


(defn mbr-point-search-tree
  "Creates a potentially infinitely deep tree of points within the  "
  [mbr]
  (let [center (m/center-point mbr)]
    {:br mbr
     :center center
     :children (lazy-seq (map mbr-point-search-tree (split-mbr mbr center)))}))


(defn find-lr-from-point-search-tree
  "TODO"
  [ring]
  (let [ring (d/calculate-derived ring)
        mbr (:mbr ring)
        pst (mbr-point-search-tree mbr)]

    (loop [pst pst depth 0]
      (when (< depth 50)
        (if-let [lr (even-lr-search ring (:center pst))]
          lr
          (let [pst (first (filter (comp (partial r/intersects-br? ring) :br) (:children pst)))]
            (when-not pst
              (errors/internal-error!
                (str "None of the child brs of the ring intersected it. "
                     (pr-str ring))))
            (recur pst (inc depth))))))))


(defn lr-search-points
  "Returns a lazy sequence of series of points from which to search for LRs. The search points are
  obtained by finding the center point of the MBR and then subdiving the MBR into 4 smaller MBRs
  and recursively finding center points for those."
  [mbr]
  (letfn [(lazy-search-points
            [mbrs ^long iterations-left]
            (println "Calculating search points at level" iterations-left)
            (let [mbrs (map #(assoc % :center-point (m/center-point %)) mbrs)
                  center-points (map :center-point mbrs)]
              (if (= iterations-left 0)
                center-points
                (concat center-points
                        ;; Lazily recurse to find center points of lower levels.
                        (lazy-seq
                          (lazy-search-points
                            (mapcat #(split-mbr % (:center-point %)) mbrs)
                            (dec iterations-left)))))))]
    (lazy-search-points [mbr] 5)))


(comment
  (def sp (lr-search-points (m/mbr 0 10 10 0)))
  (count sp)
  (+ 1 4 16 64 256 1024)

  (require '[cmr.spatial.dev.viz-helper :as viz-helper])

  (defn animate-in-points
    [points]
    (doseq [p points]
      (Thread/sleep 200)
      (viz-helper/add-geometries [p])))

  (animate-in-points (take 100 sp))

)



(defn find-lr-from-center [ring]
  "Finds the largest interior rectangle of the ring."
  (let [ring (d/calculate-derived ring)
        center-point (m/center-point (:mbr ring))]
    (even-lr-search ring center-point)))


