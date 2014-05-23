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
            [cmr.common.util :as util]))
(primitive-math/use-primitive-operators)

(defn mid-br
  "Returns an mbr midway between inner and outer mbrs"
  [inner outer]
  (let [{^double ni :north ^double si :south ^double ei :east ^double wi :west} inner
        {^double no :north ^double so :south ^double eo :east ^double wo :west} outer]
    (m/mbr (mid-lon wo wi)
           (mid ni no)
           (mid-lon ei eo)
           (mid si so))))

(defn find-lr [ring]
  (let [ring (d/calculate-derived ring)
        {clon :lon clat :lat} (m/center-point (:mbr ring))
        minv (m/mbr clon clat clon clat)
        maxv (:mbr ring)]
    (util/binary-search
      minv maxv mid-br
      (fn [current-br inner-br outer-br ^long depth]
        (let [current-in-ring (r/covers-br? ring current-br)]
          (if (> depth 50)
            ;; Exceeded the recursion depth. Take our best choice
            (if current-in-ring current-br inner-br)
            (if current-in-ring :less-than :greater-than)))))))


(comment

(defn- can-stop-searching? [current-br outer-br ^long depth]
  (let [^double cn (:north current-br)
        ^double on (:north outer-br)]
  (or (> depth 20)
      (< (- on cn) 0.001))))

(defn find-lr-orig [ring]
  (let [{clon :lon clat :lat} (m/center-point (:mbr ring))]
    (loop [inner-br (m/mbr clon clat clon clat)
           outer-br (:mbr ring)
           current-br (mid-br inner-br outer-br)
           depth 0]
      (if (r/covers-br? ring current-br)
        ;; possibly stop or grow
        (if (can-stop-searching? current-br outer-br depth)
          current-br
          (let [new-current (mid-br current-br outer-br)]
            (recur current-br outer-br new-current (inc depth))))
        (if (> depth 50)
          (if (r/covers-br? ring inner-br)
            inner-br
            nil)
          (let [new-current (mid-br inner-br current-br)] ;; shrink current-br
            (recur inner-br current-br new-current (inc depth))))))))

    (defn find-and-display-lr [ring]
      (let [lr (find-lr ring)]
        (viz/clear-geometries)
        (viz/add-geometries [ring #_(:mbr ring)])
        (if lr
          (viz/add-geometries [lr])
          (println "No lr found"))))

    (find-and-display-lr (r/ords->ring -110.17,-64.978 -119.557,-80.219 -59.624,-80.072 -80.169,-65 -110.17,-64.978))
    (find-and-display-lr (r/ords->ring -92.795184,-69.441759, -92.814511,-69.579957, -92.409809,-69.586403,
                  -92.393254,-69.448158, -92.795184,-69.441759))

    (find-and-display-lr (r/ords->ring 0,0 90,0 180,0 -90,0 0,0))



  )


