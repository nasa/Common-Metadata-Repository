(ns cmr.umm.iso-smap.granule.spatial
  "Contains functions for parsing and generating the ISO SMAP granule spatial"
  (:require
   [cmr.common.xml :as cx]
   [cmr.common.util :as util]
   [cmr.umm.umm-granule :as g]
   [cmr.umm.iso-smap.spatial :as spatial]))

(def extent-path
  [:composedOf :DS_DataSet :has :MI_Metadata :identificationInfo :MD_DataIdentification
   :extent :EX_Extent :geographicElement])

(defn- orbit?
  "Returns true or false depending on the existence of the :ascending-crossing key existing in the
  spatial coverage map."
  [spatial-map]
  (some #{:ascending-crossing} (keys spatial-map)))

(defn- orbit-calculated-spatial-domain?
  "Returns true or false depending on the existence of the orbit-cal-spatial-domain keys in the
  spatial coverage map."
  [spatial-map]
  (let [spatial-map-keys (keys spatial-map)]
    (or (some #{:orbital-mode-name} spatial-map-keys)
        (some #{:orbit-number} spatial-map-keys)
        (some #{:start-orbit-number} spatial-map-keys)
        (some #{:stop-orbit-number} spatial-map-keys)
        (some #{:equator-crossing-longitude} spatial-map-keys)
        (some #{:equator-crossing-date-time} spatial-map-keys))))

(defn xml-elem->SpatialCoverage
  "Returns a UMM SpatialCoverage from a parsed XML structure"
  [xml-struct]
  (let [spatial-elems (cx/elements-at-path xml-struct extent-path)
        geometries (flatten (map spatial/decode spatial-elems))]
    (when (seq geometries)
      (g/map->SpatialCoverage (util/remove-map-keys empty?
                                {:geometries (remove orbit-calculated-spatial-domain?
                                                    (remove orbit? geometries))
                                 :orbit (first (filter orbit? geometries))})))))

(defn xml-elem->OrbitCalculatedSpatialDomains
  "Returns a list of UMM OrbitCalculatedSpatialDomain from a parsed XML structure"
  [xml-struct]
  (let [spatial-elems (cx/elements-at-path xml-struct extent-path)
        geometries (flatten (map spatial/decode spatial-elems))]
    (when (seq geometries)
      (->> geometries
           (map #(when (orbit-calculated-spatial-domain? %) %))
           (remove nil?)
           seq))))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generators

(defn generate-spatial
  "Generates the Spatial element from spatial coverage"
  [{:keys [geometries]}]
  (map spatial/encode geometries))

(defn generate-orbit-calculated-spatial-domains
  "Generate the OrbitCalculatedSpatialDomains elements from the Umm OrbitCalculatedSpatialDomains."
  [ocsds]
  (map spatial/encode ocsds)) 
