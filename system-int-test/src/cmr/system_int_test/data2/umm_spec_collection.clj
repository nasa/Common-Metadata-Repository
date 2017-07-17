(ns cmr.system-int-test.data2.umm-spec-collection
  "Contains collection data generators for example-based testing in system
  integration tests."
  (:require
    [clj-time.core :as t]
    [clj-time.format :as f]
    [cmr.common.date-time-parser :as p]
    [cmr.common.util :as util]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.umm-spec-common :as dc]
    [cmr.umm-spec.additional-attribute :as aa]
    [cmr.umm-spec.models.umm-collection-models :as umm-c]
    [cmr.umm-spec.models.umm-common-models :as umm-cmn]
    [cmr.umm-spec.temporal :as umm-spec-temporal]
    [cmr.umm-spec.util :as u]))

(defn location-keyword
  "Return a location keyword based on the given attributes."
  [attribs]
  (umm-c/map->LocationKeywordType attribs))

(defn directory-name
  "Returns directory name"
  [attribs]
  (umm-c/map->DirectoryNameType attribs))

;; Used for testing warnings when reqired properties are missing.
(def umm-c-missing-properties
  "This is the UMM-C that misses required properties."
  {:ShortName "short"
   :Version "V1"
   :EntryTitle "The entry title V5"
   :DataDates [(umm-cmn/map->DateType {:Date (t/date-time 2012)
                                       :Type "CREATE"})]
   :Abstract "A very abstract collection"})

(def umm-c-missing-properties-dif
  "This is the minimal valid UMM-C."
  {:DataCenters [(umm-cmn/map->DataCenterType
                   {:Roles ["ARCHIVER"]
                    :ShortName "AARHUS-HYDRO"
                    :LongName "Hydrogeophysics Group, Aarhus University "})]
   :ScienceKeywords [(umm-cmn/map->ScienceKeywordType
                      {:Category "EARTH SCIENCE SERVICES"
                       :Topic "DATA ANALYSIS AND VISUALIZATION"
                       :Term "GEOGRAPHIC INFORMATION SYSTEMS"})]
   :ShortName "short"
   :Version "V1"
   :EntryTitle "The entry title V5"
   :DataDates [(umm-cmn/map->DateType {:Date (t/date-time 2012)
                                       :Type "CREATE"})]
   :Abstract "A very abstract collection"})

(def umm-c-missing-properties-dif10
  "This is the minimal valid UMM-C."
  {:Platforms [(umm-cmn/map->PlatformType
                 {:ShortName "A340-600" :LongName "Airbus A340-600"})]
   :DataCenters [(umm-cmn/map->DataCenterType
                   {:Roles ["ARCHIVER"]
                    :ShortName "AARHUS-HYDRO"
                    :LongName "Hydrogeophysics Group, Aarhus University "})]
   :ScienceKeywords [(umm-cmn/map->ScienceKeywordType
                      {:Category "EARTH SCIENCE SERVICES"
                       :Topic "DATA ANALYSIS AND VISUALIZATION"
                       :Term "GEOGRAPHIC INFORMATION SYSTEMS"})]
   :SpatialExtent (umm-cmn/map->SpatialExtentType {:GranuleSpatialRepresentation "NO_SPATIAL"})
   :ShortName "short"
   :Version "V1"
   :EntryTitle "The entry title V5"
   :DataDates [(umm-cmn/map->DateType {:Date (t/date-time 2012)
                                       :Type "CREATE"})]
   :Abstract "A very abstract collection"
   :TemporalExtents [(umm-cmn/map->TemporalExtentType {:SingleDateTimes [(t/date-time 2012)]})]})

(def minimal-umm-c
  "This is the minimal valid UMM-C."
  {:Platforms [(umm-cmn/map->PlatformType
                 {:ShortName "A340-600" :LongName "Airbus A340-600" :Type "Aircraft"})]
   :ProcessingLevel (umm-c/map->ProcessingLevelType {:Id "3"})
   :DataCenters [(umm-cmn/map->DataCenterType
                   {:Roles ["ARCHIVER"]
                    :ShortName "AARHUS-HYDRO"
                    :LongName "Hydrogeophysics Group, Aarhus University "})]
   :ScienceKeywords [(umm-cmn/map->ScienceKeywordType
                      {:Category "EARTH SCIENCE SERVICES"
                       :Topic "DATA ANALYSIS AND VISUALIZATION"
                       :Term "GEOGRAPHIC INFORMATION SYSTEMS"})]
   :SpatialExtent (umm-cmn/map->SpatialExtentType {:GranuleSpatialRepresentation "NO_SPATIAL"})
   :ShortName "short"
   :Version "V1"
   :EntryTitle "The entry title V5"
   :CollectionProgress "COMPLETE"
   :DataDates [(umm-cmn/map->DateType {:Date (t/date-time 2012)
                                       :Type "CREATE"})]
   :Abstract "A very abstract collection"
   :TemporalExtents [(umm-cmn/map->TemporalExtentType {:SingleDateTimes [(t/date-time 2012)]})]})

(defn collection-missing-properties
  "Returns a UmmCollection missing reqired properties"
  ([]
   (collection-missing-properties {}))
  ([attribs]
   (umm-c/map->UMM-C (merge umm-c-missing-properties attribs))))

(defn collection-missing-properties-dif
  "Returns a UmmCollection missing reqired properties"
  ([]
   (collection-missing-properties {}))
  ([attribs]
   (umm-c/map->UMM-C (merge umm-c-missing-properties-dif attribs))))

(defn collection-missing-properties-dif10
  "Returns a UmmCollection missing reqired properties"
  ([]
   (collection-missing-properties {}))
  ([attribs]
   (umm-c/map->UMM-C (merge umm-c-missing-properties-dif10 attribs))))

(defn collection
  "Returns a UmmCollection from the given attribute map."
  ([]
   (collection {}))
  ([attribs]
   (umm-c/map->UMM-C (merge minimal-umm-c attribs)))
  ([index attribs]
   (umm-c/map->UMM-C
    (merge
     minimal-umm-c
     {:ShortName (str "Short Name " index)
      :Version (str "V" index)
      :EntryTitle (str "Entry Title " index)}
     attribs))))

(defn collection-concept
  "Returns the collection for ingest with the given attributes"
  ([attribs]
   (collection-concept attribs :echo10))
  ([attribs concept-format]
   (let [{:keys [provider-id native-id]} attribs]
     (-> attribs
         (collection)
         (assoc :provider-id provider-id :native-id native-id)
         (d/umm-c-collection->concept concept-format)))))
