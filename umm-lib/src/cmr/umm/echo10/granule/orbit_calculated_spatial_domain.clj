(ns cmr.umm.echo10.granule.orbit-calculated-spatial-domain
  "Contains functions for parsing and generating the ECHO10 dialect for orbit calculated spatial domains."
  (:require
   [clojure.data.xml :as xml]
   [cmr.common.xml :as cx]
   [cmr.umm.umm-granule :as granule]))

(defn xml-elem->OrbitCalculatedSpatialDomain
  [ocsd-elem]
  (let [orbital-model-name (cx/string-at-path ocsd-elem [:OrbitalModelName])
        orbit-number (cx/long-at-path ocsd-elem [:OrbitNumber])
        start-orbit-number (cx/integer-at-path ocsd-elem [:StartOrbitNumber])
        stop-orbit-number (cx/integer-at-path ocsd-elem [:StopOrbitNumber])
        equator-crossing-longitude (cx/double-at-path ocsd-elem [:EquatorCrossingLongitude])
        equator-crossing-date-time (cx/datetime-at-path ocsd-elem [:EquatorCrossingDateTime])]
    (granule/->OrbitCalculatedSpatialDomain orbital-model-name
                                            orbit-number
                                            start-orbit-number
                                            stop-orbit-number
                                            equator-crossing-longitude
                                            equator-crossing-date-time)))

(defn xml-elem->orbit-calculated-spatial-domains
  [granule-element]
  (seq (map xml-elem->OrbitCalculatedSpatialDomain
            (cx/elements-at-path
              granule-element
              [:OrbitCalculatedSpatialDomains :OrbitCalculatedSpatialDomain]))))

(defn generate-orbit-calculated-spatial-domains
  "Generates the OrbitCalculatedSpatialDomains element of ECHO10 XML from a UMM Granule
  orbit-calcualated-spatial-domains record."
  [ocsds]
  (when (seq ocsds)
    (xml/element
      :OrbitCalculatedSpatialDomains {}
      (for [{:keys [orbital-model-name
                    orbit-number
                    start-orbit-number
                    stop-orbit-number
                    equator-crossing-longitude
                    equator-crossing-date-time]} ocsds]
        (xml/element :OrbitCalculatedSpatialDomain {}
                     (when orbital-model-name
                       (xml/element :OrbitalModelName {} orbital-model-name))
                     (when orbit-number
                       (xml/element :OrbitNumber {} orbit-number))
                     (when start-orbit-number
                       (xml/element :StartOrbitNumber {} start-orbit-number))
                     (when stop-orbit-number
                       (xml/element :StopOrbitNumber {} stop-orbit-number))
                     (when equator-crossing-longitude
                       (xml/element :EquatorCrossingLongitude {} equator-crossing-longitude))
                     (when equator-crossing-date-time
                       (xml/element :EquatorCrossingDateTime {} (str equator-crossing-date-time))))))))


