(ns cmr.spatial.orbits.catalog-orbit
  "TODO
  represents code from echo_oracle_geometry/spatial/orbit.rb"
  (:require [cmr.spatial.math :refer :all]
            [cmr.common.services.errors :as errors]
            [primitive-math]
            [cmr.spatial.orbits.orbit-geometry :as orbit-geometry]
            [cmr.spatial.orbits.coordinate :as coordinate]
            [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.polygon :as poly]))

(primitive-math/use-primitive-operators)

;; TODO rename this
(defn geometry
  "TODO"
  ([orbit-parameters ascending-crossing-lon time-range]
   (geometry orbit-parameters ascending-crossing-lon time-range 1.0))
  ([orbit-parameters
    ascending-crossing-lon
    [^double begin-elapsed-time
     ^double end-elapsed-time]
    ^double time-interval]
   (let [orbit-time (- end-elapsed-time begin-elapsed-time)
         num-interval (Math/ceil (/ orbit-time time-interval))
         actual-interval (/ orbit-time num-interval)
         swath-edges (for [n (range 0 num-interval)]
                       (let [time-elapsed-mins (+ begin-elapsed-time (* (double n) actual-interval))
                             edge (orbit-geometry/along-track-swath-edges
                                    orbit-parameters ascending-crossing-lon time-elapsed-mins)]
                         (map coordinate/to-point edge)))]
     (for [[edge1 edge2] (partition 2 1 swath-edges)]
       (poly/polygon :geodetic [(gr/ring (concat edge1 (reverse edge2)))])))))
