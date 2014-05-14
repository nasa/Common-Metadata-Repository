(ns cmr.spatial.lr-binary-search
  "Prototype code that finds the largest interior rectangle of a ring."
  (:require [cmr.spatial.point :as p]
            [cmr.spatial.ring :as r]
            [cmr.spatial.math :refer :all]
            [primitive-math]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.conversion :as c]
            [cmr.spatial.arc :as a]))
(primitive-math/use-primitive-operators)

;; TODO This code is stolen prototype code. The algorithm is weak and needs to be improved.
;; It also needs testing. This is being used in place to stub out most of the spatial code. We
;; should add this as a future issue to work

(defn initial-lr [ring]
  (let [mbr (:mbr ring)
        center (m/center-point mbr)
        {clon :lon clat :lat} center
        {^double n :north ^double s :south ^double e :east ^double w :west} mbr]
    (m/mbr (mid-lon w clon)
           (mid n clat)
           (mid-lon clon e)
           (mid s clat))))

(defn mid-br
  "Returns an mbr midway between inner and outer mbrs"
  [inner outer]
  (let [{^double ni :north ^double si :south ^double ei :east ^double wi :west} inner
        {^double no :north ^double so :south ^double eo :east ^double wo :west} outer]
    (m/mbr (mid-lon wo wi)
           (mid ni no)
           (mid-lon ei eo)
           (mid si so))))

(defn mbr-in-ring? [ring mbr]
  (let [{^double n :north ^double s :south ^double e :east ^double w :west} mbr
        corner-points (p/ords->points w,n e,n e,s w,s)]
    (every? #(r/covers-point? ring %) corner-points)))

(defn- can-stop-searching? [current-mbr outer-mbr ^long depth]
  (let [^double cn (:north current-mbr)
        ^double on (:north outer-mbr)]
  (or (> depth 20)
      (< (- on cn) 0.001))))

(defn find-lr [ring]
  (let [{clon :lon clat :lat} (m/center-point (:mbr ring))]
    (loop [inner-mbr (m/mbr clon clat clon clat)
           outer-mbr (:mbr ring)
           current-mbr (mid-br inner-mbr outer-mbr)
           depth 0]
      (if (mbr-in-ring? ring current-mbr)
        ;; possibly stop or grow
        (if (can-stop-searching? current-mbr outer-mbr depth)
          current-mbr
          (let [new-current (mid-br current-mbr outer-mbr)]
            (recur current-mbr outer-mbr new-current (inc depth))))
        (if (> depth 50)
          (if (mbr-in-ring? ring inner-mbr)
            inner-mbr
            nil)
          (let [new-current (mid-br inner-mbr current-mbr)] ;; shrink current-mbr
            (recur inner-mbr current-mbr new-current (inc depth))))))))


(comment

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


