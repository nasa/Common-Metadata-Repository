(ns cmr.system-int-test.data2.granule
  "Contains data generators for example based testing in system integration tests."
  (:require [cmr.umm.granule :as g]
            [cmr.umm.collection :as c]
            [cmr.umm.granule.temporal :as gt]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.common.date-time-parser :as p]
            [cmr.umm.spatial :as umm-s]
            [cmr.spatial.orbits.swath-geometry :as swath])
  (:import [cmr.umm.granule Orbit]))

(defn psa
  "Creates product specific attribute ref"
  [name values]
  (g/map->ProductSpecificAttributeRef
    {:name name
     :values values}))

(defn temporal
  "Return a temporal with range date time of the given date times"
  [attribs]
  (let [{:keys [beginning-date-time ending-date-time single-date-time]} attribs
        begin (when beginning-date-time (p/parse-datetime beginning-date-time))
        end (when ending-date-time (p/parse-datetime ending-date-time))
        single (when single-date-time (p/parse-datetime single-date-time))]
    (cond
      (or begin end)
      (gt/temporal {:range-date-time (c/->RangeDateTime begin end)})
      single
      (gt/temporal {:single-date-time single}))))

(defn sensor-ref
  "Return a sensor-ref based on sensor short-name"
  [sensor-sn]
  (g/->SensorRef sensor-sn))

(defn instrument-ref
  "Return an instrument-ref based on instrument attribs"
  ([instrument-sn]
   (g/map->InstrumentRef
     {:short-name instrument-sn}))
  ([instrument-sn & sensor-refs]
   (g/map->InstrumentRef
     {:short-name instrument-sn
      :sensor-refs sensor-refs})))

(defn platform-ref
  "Return a platform-ref based on platform attribs"
  ([platform-sn]
   (g/map->PlatformRef
     {:short-name platform-sn}))
  ([platform-sn & instrument-refs]
   (g/map->PlatformRef
     {:short-name platform-sn
      :instrument-refs instrument-refs})))

(defn data-granule
  "Returns a data-granule with the given attributes"
  [attribs]
  (let [{:keys [producer-gran-id day-night size]} attribs]
    (when (or size producer-gran-id day-night)
      (g/map->DataGranule {:producer-gran-id producer-gran-id
                           :day-night day-night
                           :size size}))))

(defn orbit
  [asc-crossing start-lat start-dir end-lat end-dir]
  (g/->Orbit asc-crossing start-lat start-dir end-lat end-dir))

(defmulti spatial
  (fn [& spatial-data]
    (if (and (= 1 (count spatial-data))
             (= Orbit (type (first spatial-data))))
      :orbit
      :geometry)))

(defmethod spatial :orbit
  [& orbits]
  (g/map->SpatialCoverage {:orbit (first orbits)}))

(defmethod spatial :geometry
  [& geometries]
  (g/map->SpatialCoverage {:geometries geometries}))

(defn granule->orbit-shapes
  "This is a helper for creating the expected spatial for a granule that has orbit data that could be
  converted into a spatial shape. Given a granule and its collection, returns a sequence containing its
  orbit geometries, or a empty sequence if it is not an orbit granule"
  [granule coll]
  (if (get-in granule [:spatial-coverage :orbit])
    (swath/to-polygons (get-in coll [:spatial-coverage :orbit-parameters])
                       (get-in granule [:spatial-coverage :orbit :ascending-crossing])
                       (:orbit-calculated-spatial-domains granule)
                       (:beginning-date-time granule)
                       (:ending-date-time granule))
    []))


(defn two-d-coordinate-system
  [attribs]
  (let [{:keys [name start-coordinate-1 end-coordinate-1
                start-coordinate-2 end-coordinate-2]} attribs]
    (g/map->TwoDCoordinateSystem {:name name
                                  :start-coordinate-1 start-coordinate-1
                                  :end-coordinate-1 end-coordinate-1
                                  :start-coordinate-2 start-coordinate-2
                                  :end-coordinate-2 end-coordinate-2})))

(defn granule
  "Creates a granule"
  ([collection]
   (granule collection {}))
  ([collection attribs]
   (let [timestamps {:data-provider-timestamps (dc/data-provider-timestamps attribs)}
         {:keys [format-key entry-title short-name version-id]} collection
         ;; Here we infer the granule format based on collection format
         ;; Added the special case for SMAP ISO granule to cover CMR-956.
         coll-ref (if (= :iso-smap format-key)
                    (g/collection-ref entry-title short-name version-id)
                    (g/collection-ref entry-title))
         minimal-gran {:granule-ur (d/unique-str "ur")
                       :collection-ref coll-ref
                       ;; Including the parent collection concept id so it's available later.
                       :collection-concept-id (:concept-id collection)}
         data-granule {:data-granule (data-granule attribs)}
         temporal {:temporal (temporal attribs)}]
     (g/map->UmmGranule (merge minimal-gran timestamps data-granule temporal attribs)))))

