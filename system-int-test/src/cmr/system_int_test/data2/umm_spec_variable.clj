(ns cmr.system-int-test.data2.umm-spec-variable
  "Contains data generators for example based testing in system integration
  tests."
  (:require
    [cheshire.core :as json]
    [clj-time.core :as t]
    [clj-time.format :as f]
    [cmr.common.date-time-parser :as p]
    [cmr.common.util :as util]
    [cmr.system-int-test.data2.core :as d]
    [cmr.umm-spec.additional-attribute :as aa]
    [cmr.umm-spec.models.umm-variable-models :as umm-var]
    [cmr.umm-spec.models.umm-common-models :as umm-cmn]
    [cmr.umm-spec.temporal :as umm-spec-temporal]
    [cmr.umm-spec.util :as u]))

(def simple-json-variable
  (json/generate-string
    {:Name "A name"
     :LongName "A long UMM-Var name"
     :Units "m"
     :DataType "float32"
     :DimensionsName ["H2OFunc"
                      "H2OPressureLay"
                      "MWHingeSurf"
                      "Cloud"]
     :Dimensions ["11" "14" "7" "2"]
     :ValidRange []
     :Scale 1.0
     :Offset 0.0
     :FillValue -9999.0
     :VariableType "SCIENCE_VARIABLE"}))

(def full-umm-var
  "This is a full example of a valid UMM-Var."
  {:Name "A name"
   :LongName "A long UMM-Var name"
   :Units "m"
   :DataType "float32"
   :DimensionsName ["H2OFunc"
                    "H2OPressureLay"
                    "MWHingeSurf"
                    "Cloud"
                    "HingeSurf"
                    "H2OPressureLev"
                    "AIRSXTrack"
                    "StdPressureLay"
                    "CH4Func"
                    "StdPressureLev"]
   :Dimensions ["11" "14" "7" "2" "100" "15" "3" "28" "10" "9"]
   :ValidRange (umm-var/map->ValidRangeType
                {:Min 0
                 :Max 255})
   :Scale 1.0
   :Offset 0.0
   :FillValue -9999.0
   :VariableType "SCIENCE_VARIABLE"

   ;; XXX Waiting on final form of schema before enabling
   ;;     the rest of these.
   ; :Tagging (umm-var/map->TaggingType
   ;           {:Tags "magical"
   ;            :TagSource "The same hat whence the rabbit"})

   ; :Characteristics (umm-var/map->CharacteristicsType
   ;                   {:Bounds "0,180"
   ;                    :Structure nil
   ;                    :Coordinates "a,1"
   ;                    :ChunkSize nil
   ;                    :GridMapping "Mercator"
   ;                    :Size "huge"
   ;                    :SizeUnits "none"
   ;                    :StandardName  "A standard name"
   ;                    :Reference "link"})

   :ScienceKeywords [(umm-cmn/map->ScienceKeywordType
                      {:Category "EARTH SCIENCE SERVICES"
                       :Topic "DATA ANALYSIS AND VISUALIZATION"
                       :Term "GEOGRAPHIC INFORMATION SYSTEMS"})]

   :ServiceType (umm-var/map->ServiceTypeType
                 {:ServiceType "WCS"
                  :Visualizable false
                  :Subsettable false})})

(defn variable
  "Returns a Umm-Var from the given attribute map."
  ([]
   (variable {}))
  ([attribs]
   (umm-var/map->UMM-Var (merge full-umm-var attribs)))
  ([index attribs]
   (umm-var/map->UMM-Var
    (merge
     full-umm-var
     {:Name (str "Name " index)
      :Version (str "V" index)
      :LongName (str "Long UMM-Var name " index)}
     attribs))))

(defn variable-concept
  "Returns the variable for ingest with the given attributes."
  ([attribs]
   (variable-concept attribs :json))
  ([attribs concept-format]
   (-> attribs
       (variable)
       (d/umm-var->concept concept-format))))
