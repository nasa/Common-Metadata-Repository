(ns cmr.spatial.orbit
  (:require
   [clojure.data.xml :as x]
   [clojure.string :as str]
   [cmr.common.validations.core :as v]
   [cmr.common.xml :as cx]
   [cmr.spatial.messages :as msg]
   [cmr.spatial.validation :as sv]))

(defrecord Orbit
  [ascending-crossing
   start-lat
   start-direction
   end-lat
   end-direction])

(defn- start-end-direction
  [field-path direction]
  (when (and (not= "A" direction)
             (not= "D" direction))
    {field-path [(msg/start-end-direction direction)]}))

(def validations
  [{:ascending-crossing [v/required v/number (v/within-range -180.0 180.0)]
    :start-lat [v/required v/number (v/within-range -90.0 90.0)]
    :end-lat [v/required v/number (v/within-range -90.0 90.0)]
    :start-direction start-end-direction
    :end-direction start-end-direction}])

(extend-protocol sv/SpatialValidation
  cmr.spatial.orbit.Orbit
  (validate
    [record]
    (v/create-error-messages (v/validate validations record))))

(defn build-orbit-string
  "Builds ISO SMAP orbit string using umm values."
  [orbit]
  (let [{:keys [ascending-crossing start-lat start-direction end-lat end-direction]} orbit]
    (format "AscendingCrossing: %s StartLatitude: %s StartDirection: %s EndLatitude: %s EndDirection: %s"
            ascending-crossing start-lat start-direction end-lat end-direction)))
