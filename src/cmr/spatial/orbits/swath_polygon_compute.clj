(ns cmr.spatial.orbits.swath-polygon-compute
  "TODO"
  (:require [cmr.spatial.orbits.catalog-orbit :as catalog-orbit]
            [cmr.spatial.math :refer :all]
            [cmr.common.services.errors :as errors]
            [primitive-math]
            [clj-time.core :as t]))

(primitive-math/use-primitive-operators)

(defn get-ascending-crossing-time
  "Finds the ascending crossing date time given at the given longitude from orbit calculated spatial domains"
  [granule-orbit ocsds]
  ;; If the ascending equator crossing exists in the spatial domain, then we use the time
  ;; corresponding to it for the ascending equator crossing time, otherwise we assume that the first
  ;; equator crossing in the spatial domain is a descending crossing and the ascending equator
  ;; crossing occurred half period earlier.
  (or (some->> ocsds
               (filter (fn [{:keys [equator-crossing-longitude]}]
                         (= (:ascending-crossing granule-orbit)
                            equator-crossing-longitude)))
               first
               :equator-crossing-date-time)
      (let [^double equator-crossing-time (-> ocsds first :equator-crossing-date-time)]
        (- equator-crossing-time ^double (* (/ ^double (:period granule-orbit) 2.0) 60.0)))))

(defn temporal+ascending-crossing-time->elapsed-time-range
  "Takes the granule temporal and the ascending crossing time and it returns a tuple of the
  beginning elapsed time and the end elapsed time."
  [temporal ascending-crossing-time]
  ;; Given the ascending equator crossing and the time corresponding it, the orbit track can be computed
  ;; at any moment of time. We pick several points  of time in the temporal range for the granule and
  ;; build polygons from them.
  ;; TODO this only works for granules with range date time. What about granules with a single date time?
  (let [begin (get-in temporal [:range-date-time :beginning-date-time])
        end (get-in temporal [:range-date-time :ending-date-time])
        begin-elapsed-time (t/in-minutes (t/interval begin ascending-crossing-time))
        end-elapsed-time (t/in-minutes (t/interval end ascending-crossing-time))]
    [begin-elapsed-time end-elapsed-time]))

(defn to-polygons
  "TODO
  orbit-parameters - orbit parameters from the collection
  granule-orbit - orbit element from the granule
  spatial-domains - orbit calculated spatial domains from granule metadata
  temporal - temporal element from the granule"
  [orbit-parameters granule-orbit ocsds temporal]
  ;; Find the time of the ascending equator crossing
  (let [ascending-crossing-time (get-ascending-crossing-time granule-orbit ocsds)
        [begin-elapsed-time end-elapsed-time] (temporal+ascending-crossing-time->elapsed-time-range
                                                temporal ascending-crossing-time)]
    (catalog-orbit/geometry orbit-parameters (:ascending-crossing granule-orbit)
                            [begin-elapsed-time end-elapsed-time])))



(comment
  (require '[cmr.spatial.dev.viz-helper :as viz-helper])

  (let [orbit-parameters
        {:inclination-angle 65
         :period 90
         :swath-width 120.0
         :start-circular-latitude -90.0
         :number-of-orbits 0.5}]

    (viz-helper/clear-geometries)
    (viz-helper/add-geometries
      (catalog-orbit/geometry orbit-parameters -158.1 [30 40])))

  ;; TODO build integration tests that take some made up data that looks good and is run through
  ;; the code in ruby
  ;; We can also get some existing granules in ops and their polygons and hard code them in the
  ;; integration tests.



  )


