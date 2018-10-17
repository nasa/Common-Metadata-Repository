(ns cmr.umm-spec.umm-g.orbit-calculated-spatial-domain
  "Contains functions for parsing UMM-G JSON OrbitCalculatedSpatialDomains into umm-lib granule model
  OrbitCalculatedSpatialDomains and generating UMM-G JSON OrbitCalculatedSpatialDomains from umm-lib
  granule model OrbitCalculatedSpatialDomains."
  (:require
   [cmr.umm.umm-granule :as g])
  (:import cmr.umm.umm_granule.OrbitCalculatedSpatialDomain))

(defn- umm-g-orbit-calculated-spatial-domain->OrbitCalculatedSpatialDomain
  "Returns the umm-lib granule model OrbitCalculatedSpatialDomain from the given
  UMM-G OrbitCalculatedSpatialDomain."
  [ocsd]
  (g/map->OrbitCalculatedSpatialDomain
   {:orbital-model-name (:OrbitalModelName ocsd)
    :orbit-number (:OrbitNumber ocsd)
    :start-orbit-number (:BeginOrbitNumber ocsd)
    :stop-orbit-number (:EndOrbitNumber ocsd)
    :equator-crossing-longitude (:EquatorCrossingLongitude ocsd)
    :equator-crossing-date-time (:EquatorCrossingDateTime ocsd)}))

(defn umm-g-orbit-calculated-spatial-domains->OrbitCalculatedSpatialDomains
  "Returns the umm-lib granule model OrbitCalculatedSpatialDomains from the given
  UMM-G OrbitCalculatedSpatialDomains."
  [orbit-calculated-spatial-domains]
  (seq (map umm-g-orbit-calculated-spatial-domain->OrbitCalculatedSpatialDomain
            orbit-calculated-spatial-domains)))

(defn OrbitCalculatedSpatialDomains->umm-g-orbit-calculated-spatial-domains
  "Returns the UMM-G OrbitCalculatedSpatialDomains from the given umm-lib granule model
  OrbitCalculatedSpatialDomains."
  [orbit-calculated-spatial-domains]
  (when (seq orbit-calculated-spatial-domains)
    (for [ocsd orbit-calculated-spatial-domains]
      (let [{:keys [orbital-model-name orbit-number start-orbit-number stop-orbit-number
                    equator-crossing-longitude equator-crossing-date-time]} ocsd]
        {:OrbitalModelName orbital-model-name
         :OrbitNumber orbit-number
         ;; Added conversion to int to deal with potential invalid data from other formats,
         ;; the start-orbit-number and stop-orbit-number were used to be double before.
         :BeginOrbitNumber (when start-orbit-number (int start-orbit-number))
         :EndOrbitNumber (when stop-orbit-number (int stop-orbit-number))
         :EquatorCrossingLongitude equator-crossing-longitude
         :EquatorCrossingDateTime equator-crossing-date-time}))))
