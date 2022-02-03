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
    [cmr.umm-spec.versioning :as ver]
    [cmr.umm-spec.additional-attribute :as aa]
    [cmr.umm-spec.metadata-specification :as m-spec]
    [cmr.umm-spec.models.umm-common-models :as umm-cmn]
    [cmr.umm-spec.models.umm-variable-models :as umm-v]
    [cmr.umm-spec.temporal :as umm-spec-temporal]
    [cmr.umm-spec.util :as u]))

(def sample-umm-var
  (-> {:Name "A newfangled, brand-new, immensely longer variable name, much larger than the old name so we can be sure that everything works as expected."
       :LongName "A long UMM-Var name"
       :Definition "Defines the variable"
       :Units "m"
       :DataType "float32"
       :Dimensions [(umm-v/map->DimensionType {:Name "Solution_3_Land" :Size 3 :Type "OTHER"})]
       :Sets [(umm-v/map->SetType {:Name "Data_Fields" :Type "Science" :Size 2 :Index 2})]
       :Scale 1.0
       :Offset 0.0
       :FillValues [(umm-v/map->FillValueType {:Value -9999.0, :Type "SCIENCE_FILLVALUE"})]
       :VariableType "COORDINATE"
       :VariableSubType "LONGITUDE"
       :ScienceKeywords [(umm-cmn/map->ScienceKeywordType
                          {:Category "sk-A"
                           :Topic "sk-B"
                           :Term "sk-C"})]}
      (m-spec/update-version :variable ver/current-variable-version)))

(defn variable
  "Returns a UmmVar from the given attribute map."
  ([]
   (variable {}))
  ([attribs]
   (umm-v/map->UMM-Var (merge sample-umm-var attribs)))
  ([index attribs]
   (umm-v/map->UMM-Var
    (merge sample-umm-var
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
         (d/umm-var->concept concept-format))))
  ([attribs concept-format index]
    (let [{:keys [provider-id native-id]} attribs]
     (-> index
         (variable attribs)
         (assoc :provider-id provider-id :native-id native-id)
         (d/umm-var->concept concept-format)))))

(defn measurement-identifier
  "Returns the UMM-Var MeasurementIdentifier for the given attributes map"
  [attribs]
  (let [{:keys [context-medium object quantities]} attribs]
    (umm-v/map->MeasurementIdentifierType
      {:MeasurementContextMedium context-medium
       :MeasurementObject object
       :MeasurementQuantities (seq (map #(umm-v/map->MeasurementQuantityType {:Value %})
                                        quantities))})))
