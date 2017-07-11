(ns cmr.system-int-test.data2.umm-spec-variable
  "Contains variable data generators for example-based testing in system
  integration tests."
  (:require
    [clj-time.core :as t]
    [clj-time.format :as f]
    [cmr.common.date-time-parser :as p]
    [cmr.common.util :as util]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.umm-spec-common :as dc]
    [cmr.umm-spec.additional-attribute :as aa]
    [cmr.umm-spec.models.umm-common-models :as umm-cmn]
    [cmr.umm-spec.models.umm-variable-models :as umm-v]
    [cmr.umm-spec.temporal :as umm-spec-temporal]
    [cmr.umm-spec.util :as u]))

(def sample-umm-var
  {:Name "A-name"
   :LongName "A long UMM-Var name"
   :Units "m"
   :DataType "float32"
   :DimensionsName "H2OFunc"
   :Dimensions "11"
   :ValidRange {}
   :Scale "1.0"
   :Offset "0.0"
   :FillValue "-9999.0"
   :VariableType "SCIENCE_VARIABLE"
   :ScienceKeywords [(umm-cmn/map->ScienceKeywordType
                      {:Category "sk-A"
                       :Topic "sk-B"
                       :Term "sk-C"})]})

(defn variable
  "Returns a UmmVar from the given attribute map."
  ([]
   (variable {}))
  ([attribs]
   (umm-v/map->UMM-Var (merge sample-umm-var attribs)))
  ([index attribs]
   (umm-v/map->UMM-Var
    (merge
     sample-umm-var
     {:Name (str "Name " index)
      :LongName (str "Long UMM-Var name" index)}
     attribs))))

(defn variable-concept
  "Returns the variable for ingest with the given attributes"
  ([attribs]
   (variable-concept attribs :umm-json))
  ([attribs concept-format]
   (let [{:keys [provider-id native-id]} attribs]
     (-> (variable attribs)
         (assoc :provider-id provider-id :native-id native-id)
         (d/umm-var->concept concept-format)))))
