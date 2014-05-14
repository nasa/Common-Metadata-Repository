(ns cmr.system-int-test.data2.granule
  "Contains data generators for example based testing in system integration tests."
  (:require [cmr.umm.granule :as g]
            [cmr.umm.collection :as c]
            [cmr.umm.granule.temporal :as gt]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common.date-time-parser :as p]))

(defn psa
  "Creates product specific attribute ref"
  [name values]
  (g/map->ProductSpecificAttributeRef
    {:name name
     :values values}))

(defn temporal
  "Return a temporal with range date time of the given date times"
  [attribs]
  (let [{:keys [beginning-date-time ending-date-time]} attribs
        begin (when beginning-date-time (p/parse-datetime beginning-date-time))
        end (when ending-date-time (p/parse-datetime ending-date-time))]
    (when (or begin end)
      (gt/temporal {:range-date-time (c/->RangeDateTime begin end)}))))

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
  (let [{:keys [producer-gran-id size]} attribs]
    (when (or size producer-gran-id)
      (g/map->DataGranule {:producer-gran-id producer-gran-id
                           :size size}))))

(defn spatial
  [& geometries]
  (g/->SpatialCoverage geometries))

(defn related-url
  "Creates related url for online_only test"
  [type]
  (g/->RelatedURL type nil (d/unique-str "http://example.com/file") (d/unique-str "description")))

(defn granule
  "Creates a granule"
  ([collection]
   (granule collection {}))
  ([collection attribs]
   (let [coll-ref (g/collection-ref (:entry-title collection))
         minimal-gran {:granule-ur (d/unique-str "ur")
                       :collection-ref coll-ref}
         data-granule {:data-granule (data-granule attribs)}
         temporal {:temporal (temporal attribs)}]
     (g/map->UmmGranule (merge minimal-gran data-granule temporal attribs)))))

