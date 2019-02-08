(ns cmr.spatial.test.arc-intersections
  (:require
   ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
   ;; [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.set]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :refer [for-all]]
   [cmr.common.test.test-check-ext :refer [defspec]]
   [cmr.spatial.arc :as a]
   [cmr.spatial.math :refer :all]
   [cmr.spatial.point :as p]
   [cmr.spatial.test.generators :as sgen]))

(declare example-arcs example-intersections example-non-intersections)

(defn arc-intersection-failure-msg
  "Returns an error message to help debug arc intersection failures."
  [base-msg arc-name1 arc-name2]
  (let [a1 (example-arcs arc-name1)
        a2 (example-arcs arc-name2)
        run-it-msg (str "Run it: "
                        (pr-str `(a/intersections (example-arcs ~arc-name1) (example-arcs ~arc-name2))))
        ords1 (str/join "," (a/arc->ords a1))
        ords2 (str/join "," (a/arc->ords a2))
        url (str "http://testbed.echo.nasa.gov/spatial-viz/arc_intersection?arc1_ordinates="
                 ords1 "&arc2_ordinates=" ords2)]
    (str base-msg " " run-it-msg " " url)))

(deftest test-example-intersections
  (testing "example intersections"
    (doseq [[arc-name1 arc-name2 num-intersections] example-intersections
            :let [a1 (example-arcs arc-name1)
                  a2 (example-arcs arc-name2)
                  intersections (a/intersections a1 a2)
                  num-intersections (if num-intersections num-intersections 1)]]
      (is (= num-intersections (count intersections))
          (arc-intersection-failure-msg (str arc-name1 " did not intersect " arc-name2 ". Intersections: " (vec intersections))
                                        arc-name1 arc-name2))))
  (testing "example non-intersections"
    (doseq [[arc-name1 arc-name2] example-non-intersections
            :let [a1 (example-arcs arc-name1)
                  a2 (example-arcs arc-name2)
                  intersections (a/intersections a1 a2)]]
      (is (= (count intersections) 0)
          (arc-intersection-failure-msg (str arc-name1 " should not intersect " arc-name2 ".")
                                        arc-name1 arc-name2)))))

(comment
  ;; The following example code will find how close two arcs can get before they are considered equivalent

  (defn degrees->meters
    "Converts angular degrees to meters on the surface of the earth."
    ^double [^double num-degs]
    (let [earth-circumference (* TAU EARTH_RADIUS_METERS)
          meters-per-degree (/ earth-circumference 360.0)]
      (* meters-per-degree num-degs)))

  (defn point-distance
    [p1 p2]
    (degrees->meters (degrees (p/angular-distance p1 p2))))


  (let [a1 (a/ords->arc -45.19377 68.63509 -44.67717 68.63509)]
    (loop [a2 (a/ords->arc -45.19377 68.63526 -44.67717 68.63526)]
      (if-let [intersections (seq (a/intersections a1 a2))]
        [(point-distance (:west-point a1) (:west-point a2)) a2]
        (let [p1 (a/midpoint (a/arc (:west-point a1) (:west-point a2)))
              p2 (a/midpoint (a/arc (:east-point a1) (:east-point a2)))]
          (println "Non intersecting. Distance:" (point-distance (:west-point a1) (:west-point a2))
                   "ords:" (pr-str (a/arc->ords a2)))
          (recur (a/arc p1 p2)))))))




(defspec arc-intersection-spec 100
  (for-all [arc sgen/arcs]
    (and
      ;;An arc should intersect itself
      (> (count (a/intersections arc arc)) 0)
      ;; An arc should not intersect an antipodal arc
      (= (count (a/intersections arc (a/antipodal arc))) 0))))

(def example-arcs
  "A bunch of example arcs with names."
  (let [examples {;; North pole
                  :near_np1 [7.81,88.95, 136.47,88.9]
                  :near_np2 [-42.05,88.78, 128.32,86.04]
                  :near_np_crosses_am [-106.7,88.84, 122.86,89.21]
                  :endpoint_on_np [0,85, 0,90]
                  :endpoint_on_np_on_am [180,85, 0,90]
                  :crosses_np [-90,85, 90,85]
                  :crosses_np_on_am [180,0, 0,85]

                  ;; South pole
                  :near_sp1 [7.81,-88.95, 136.47,-88.9]
                  :near_sp2 [-42.05,-88.78, 128.32,-86.04]
                  :near_sp_crosses_am [-106.7,-88.84, 122.86,-89.21]
                  :endpoint_on_sp [0,-85, 0,-90]
                  :endpoint_on_sp_on_am [180,-85, 0,-90]
                  :crosses_sp [-90,-85, 90,-85]
                  :crosses_sp_on_am [180,0, 0,-85]


                  :regular_angled1 [0,1, 10,12]
                  :regular_angled2 [10,-5, 0,10]

                  :long [-48,-76, 87,88]
                  :short [-41.58718,5.11993, -41.58716,5.11991]

                  :hor_on_eq1 [-85,0, 85,0]
                  :hor_on_eq2 [-95,0, 95,0]
                  :hor_on_eq3 [-10,0, -8,0]
                  :hor_on_eq4 [0,0, 90,0]

                  :hor_above_eq1 [-10, 5, -8,5]
                  :hor_above_eq2 [-45,5, 2,5]

                  ;; This is a subset of :hor_above_eq2
                  :hor_above_eq3 [-35,5.29989, -15,5.414773]

                  :ver_along_am [180,-55, -180,65]
                  :ver_along_pm1 [0,-55, 0,65]
                  :ver_along_pm2 [0,-15, 0,-25]

                  :ver_along_pm3 [0,-55, 0,-50]}]
    (into {} (for [[arc-name [lon1 lat1 lon2 lat2]] examples]
               [arc-name (a/arc (p/point lon1 lat1) (p/point lon2 lat2))]))))


(def example-intersections
  "Combinations of all the arcs that should intersect each other."
  (sort-by (comp name first)
           (map (fn [[n1 n2 num]]
                  (let [sorted (vec (sort-by name [n1 n2]))]
                    (if num
                      (conj sorted num)
                      sorted)))

                ;; name1, name2, optional number of intersection points expected
                [[:near_np1 :near_np2]
                 [:endpoint_on_np_on_am :endpoint_on_np]
                 [:endpoint_on_np_on_am :near_np_crosses_am]
                 [:crosses_np :near_np1]
                 [:crosses_np :near_np2]
                 [:crosses_np :endpoint_on_np]
                 [:crosses_np :endpoint_on_np_on_am]

                 [:crosses_np_on_am :near_np2]
                 [:crosses_np_on_am :crosses_np]
                 ;; The 2 indicates that we expect 2 points
                 [:crosses_np_on_am :endpoint_on_np 2]
                 [:crosses_np_on_am :endpoint_on_np_on_am 2]
                 [:crosses_np_on_am :near_np_crosses_am]
                 [:crosses_np_on_am :hor_on_eq2]
                 [:crosses_np_on_am :long]
                 [:crosses_np_on_am :crosses_sp_on_am]
                 [:crosses_np_on_am :ver_along_am 2]

                 [:crosses_sp_on_am :endpoint_on_sp 2]
                 [:crosses_sp_on_am :endpoint_on_sp_on_am 2]
                 [:crosses_sp_on_am :near_sp2]
                 [:crosses_sp_on_am :near_sp_crosses_am]
                 [:crosses_sp_on_am :ver_along_am 2]
                 [:crosses_sp_on_am :crosses_sp]
                 [:crosses_sp_on_am :hor_on_eq2]

                 [:near_np1 :near_np_crosses_am]
                 [:endpoint_on_np :near_np2]

                 [:near_sp1 :near_sp2]
                 [:endpoint_on_sp_on_am :endpoint_on_sp]
                 [:endpoint_on_sp_on_am :near_sp_crosses_am]
                 [:endpoint_on_sp :near_sp2]
                 [:crosses_sp :near_sp1]
                 [:crosses_sp :near_sp2]
                 [:crosses_sp :endpoint_on_sp]
                 [:crosses_sp :endpoint_on_sp_on_am]
                 [:near_sp1 :near_sp_crosses_am]

                 [:regular_angled1 :regular_angled2]
                 [:endpoint_on_np :long]

                 [:hor_on_eq1 :long]
                 [:hor_on_eq1 :regular_angled2]
                 [:hor_on_eq1 :hor_on_eq3 2]

                 [:hor_on_eq4 :hor_on_eq1 2]
                 [:hor_on_eq4 :regular_angled2]
                 [:hor_on_eq4 :ver_along_pm1]

                 [:hor_above_eq2 :long]
                 [:hor_above_eq2 :hor_above_eq3 2]

                 [:ver_along_am :hor_on_eq2]
                 [:ver_along_pm1 :hor_on_eq1]
                 [:ver_along_pm1 :hor_above_eq2]
                 [:ver_along_pm1 :regular_angled1]
                 [:ver_along_pm1 :regular_angled2]
                 [:ver_along_pm1 :ver_along_pm2 2]
                 [:ver_along_pm1 :ver_along_pm3 2]

                 [:short :long]
                 [:short :hor_above_eq2]])))


(def example-non-intersections
  "Combinations of all the arcs that should not intersect."
  (let [set-of-sorted-tuples #(->> % (map (comp vec sort)) set)
        all-arcs (set (keys example-arcs))]
    (->> (clojure.set/difference
           (set-of-sorted-tuples (for [arc-name all-arcs
                                       other-arc  (filter #(not= % arc-name) all-arcs)]
                                   [arc-name other-arc]))
           (set-of-sorted-tuples (map #(subvec % 0 2) example-intersections)))
         vec
         (sort-by first))))
