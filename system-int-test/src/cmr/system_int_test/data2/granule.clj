(ns cmr.system-int-test.data2.granule
  "Contains data generators for example based testing in system integration tests."
  (:require
   [cmr.common.date-time-parser :as p]
   [cmr.common.util :as util]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.orbits.swath-geometry :as swath]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.umm.collection.entry-id :as eid]
   [cmr.umm.granule.temporal :as gt]
   [cmr.umm.umm-collection :as c]
   [cmr.umm.umm-granule :as g]
   [cmr.umm.umm-spatial :as umm-s])
  (:import
   [cmr.umm.umm_granule Orbit DataProviderTimestamps]))

(defn related-url
  "Creates related url for online_only test"
  ([]
   (related-url nil))
  ([attribs]
   (let [description (d/unique-str "description")]
     (c/map->RelatedURL (merge {:url (d/unique-str "http://example.com/file")
                                :description description
                                :title description}
                               attribs)))))

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

(defn sensor-refs
  "Return a list of sensor-ref based on given short names"
  [& short-names]
  (map #(g/map->SensorRef {:short-name %}) short-names))

(defn instrument-ref
  "Return an instrument-ref based on instrument attribs"
  [attribs]
  (g/map->InstrumentRef attribs))

(defn instrument-refs
  "Return a list of instrument-ref based on given short names"
  [& short-names]
  (map #(g/map->InstrumentRef {:short-name %}) short-names))

(defn instrument-ref-with-sensor-refs
  "Return a instrument-ref based on a given short name and a list of sensor short names"
  [short-name & sensor-short-names]
  (let [sensor-refs (apply sensor-refs sensor-short-names)]
    (g/map->InstrumentRef {:short-name short-name
                           :sensor-refs sensor-refs})))

(defn platform-ref
  "Return a platform-ref based on platform attribs"
  [attribs]
  (g/map->PlatformRef attribs))

(defn platform-refs
  "Return a list of platform-ref based on given short names"
  [& short-names]
  (map #(g/map->PlatformRef {:short-name %}) short-names))

(defn platform-ref-with-instrument-refs
  "Return a platform-ref based on a given short name and a list of instrument short names"
  [short-name & instr-short-names]
  (let [instr-refs (apply instrument-refs instr-short-names)]
    (g/map->PlatformRef {:short-name short-name
                         :instrument-refs instr-refs})))

(defn platform-ref-with-instrument-ref-and-sensor-refs
  "Return a platform-ref based on a given short name and a instrument ref which is
   based on a short name and a list of sensor short names"
  [plat-short-name instr-short-name & sensor-short-names]
  (let [instr-ref (apply instrument-ref-with-sensor-refs instr-short-name sensor-short-names)]
    (g/map->PlatformRef {:short-name plat-short-name
                         :instrument-refs [instr-ref]})))

(defn data-granule
  "Returns a data-granule with the given attributes"
  [attribs]
  (let [{:keys [producer-gran-id day-night size production-date-time feature-ids crid-ids identifiers]} attribs]
    (when (or size producer-gran-id day-night production-date-time feature-ids crid-ids identifiers)
      (g/map->DataGranule {:producer-gran-id producer-gran-id
                           :day-night day-night
                           :production-date-time (or production-date-time
                                                     (p/parse-datetime "2010-01-01T12:00:00Z"))
                           :size size
                           :feature-ids feature-ids
                           :crid-ids crid-ids
                           :identifiers identifiers}))))

(defn orbit-calculated-spatial-domain
  "Returns an orbit-calculated-spatial-domain with the given attributes"
  [attribs]
  (g/map->OrbitCalculatedSpatialDomain attribs))

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

(defn- pass-param->TrackPass
  "Returns the umm-lib granule model TrackPass from the given pass param."
  [track-pass]
  (let [{:keys [pass tiles]} track-pass]
    (g/map->TrackPass
     {:pass pass
      :tiles tiles})))

(defn- track-param->Track
  "Returns the umm-lib granule model Track from the given track param."
  [track]
  (when track
    (let [{:keys [cycle passes]} track]
      (g/map->Track
       {:cycle cycle
        :passes (map pass-param->TrackPass passes)}))))

(defn spatial-with-track
  "This is a helper function returns a spatial coverage of granule with track information.
  This function is used only to construct various track for searching granules by track tests.
  The given track is in the format of: e.g.
  {:cycle 1
   :passes [{:pass 3 :tiles [\"2F\" \"3R\"]}]}"
  [track]
  (g/map->SpatialCoverage {:geometries [(m/mbr -180 90 180 -90)]
                           :track (track-param->Track track)}))

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
