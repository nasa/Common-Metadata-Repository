(ns cmr.system-int-test.data2.granule
  "Contains data generators for example based testing in system integration tests."
  (:require [cmr.umm.umm-granule :as g]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.granule.temporal :as gt]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.common.date-time-parser :as p]
            [cmr.umm.umm-spatial :as umm-s]
            [cmr.common.util :as util]
            [cmr.umm.collection.entry-id :as eid]
            [cmr.spatial.orbits.swath-geometry :as swath])
  (:import [cmr.umm.umm_granule
            Orbit
            DataProviderTimestamps]))

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
  "Return a sensor-ref based on sensor attribs."
  [attribs]
  (g/map->SensorRef attribs))

(defn instrument-ref
  "Return an instrument-ref based on instrument attribs"
  [attribs]
  (g/map->InstrumentRef attribs))

(defn platform-ref
  "Return a platform-ref based on platform attribs"
  [attribs]
  (g/map->PlatformRef attribs))

(defn platform-refs
  "Return a list of platform-ref based on given short names"
  [& short-names]
  (map #(g/map->PlatformRef {:short-name %}) short-names))

(defn data-granule
  "Returns a data-granule with the given attributes"
  [attribs]
  (let [{:keys [producer-gran-id day-night size production-date-time]} attribs]
    (when (or size producer-gran-id day-night production-date-time)
      (g/map->DataGranule {:producer-gran-id producer-gran-id
                           :day-night day-night
                           :production-date-time (or production-date-time
                                                     (p/parse-datetime "2010-01-01T12:00:00Z"))
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
  (if (and (get-in granule [:spatial-coverage :orbit])
           (not-empty (:orbit-calculated-spatial-domains granule)))
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
(defn two-d
  [name]
  (let [attribs {:name name
                 :start-coordinate-1 0.0
                 :end-coordinate-1 0.0
                 :start-coordinate-2 0.0
                 :end-coordinate-2 0.0}]
    (two-d-coordinate-system attribs)))

(defn data-provider-timestamps
  "Create the data providers time stamps record for granules"
  [attribs]
  (let [attribs (util/remove-nil-keys
                  (select-keys attribs (util/record-fields DataProviderTimestamps)))
        attribs (into {} (for [[k v] attribs] [k (p/parse-datetime v)]))
        minimal-timestamps {:insert-time (d/make-datetime 10 false)
                            :update-time (d/make-datetime 18 false)}]
    (g/map->DataProviderTimestamps (merge minimal-timestamps attribs))))

(defn granule
  "Creates a granule"
  ([collection]
   (granule collection {}))
  ([collection attribs]
   (let [timestamps {:data-provider-timestamps (data-provider-timestamps attribs)}
         {:keys [entry-title] {:keys [short-name version-id]} :product} collection
         coll-ref (g/map->CollectionRef {:entry-title entry-title
                                         :entry-id (eid/entry-id short-name version-id)
                                         :short-name short-name
                                         :version-id version-id})
         minimal-gran {:granule-ur (d/unique-str "ur")
                       :collection-ref coll-ref
                       ;; Including the parent collection concept id so it's available later.
                       :collection-concept-id (:concept-id collection)}
         data-granule {:data-granule (data-granule attribs)}
         temporal {:temporal (temporal attribs)}]
     (g/map->UmmGranule (merge minimal-gran timestamps data-granule temporal attribs)))))

(defn granule-with-umm-spec-collection
  "Creates a granule with the collection coming from UMM spec"
  ([collection collection-concept-id]
   (granule-with-umm-spec-collection collection collection-concept-id {}))
  ([collection collection-concept-id attribs]
   (let [timestamps {:data-provider-timestamps (data-provider-timestamps attribs)}
         entry-title (:EntryTitle collection)
         short-name (:ShortName collection)
         version-id (:Version collection)
         coll-ref (g/map->CollectionRef {:entry-title entry-title
                                         :entry-id (eid/entry-id short-name version-id)
                                         :short-name short-name
                                         :version-id version-id})
         minimal-gran {:granule-ur (d/unique-str "ur")
                       :collection-ref coll-ref
                       ;; Including the parent collection concept id so it's available later.
                       :collection-concept-id collection-concept-id}
         data-granule {:data-granule (data-granule attribs)}
         temporal {:temporal (temporal attribs)}]
     (g/map->UmmGranule (merge minimal-gran timestamps data-granule temporal attribs)))))
