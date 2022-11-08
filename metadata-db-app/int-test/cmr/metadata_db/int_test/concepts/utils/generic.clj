(ns cmr.metadata-db.int-test.concepts.utils.generic
  "Defines implementations for all of the multi-methods for generics in the metadata-db
  integration tests."
  (:require
   [cheshire.core :as json]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]))

(defmethod concepts/get-sample-metadata :data-quality-summary
  [_concept-type]
  (json/generate-string
   {:Id "8EA5CA1F-E339-8065-26D7-53B64074D7CC",
    :Name "CER-BDS_Terra",
    :Summary "Summary",
    :MetadataSpecification {:Name "Data Quality Summary",
                            :Version "1.0.0",
                            :URL "https://cdn.earthdata.nasa.gov/generics/data-quality-summary/v1.0.0"}}))

(defmethod concepts/create-concept :data-quality-summary
  [concept-type & args]
  (let [[provider-id uniq-num attributes] (concepts/parse-create-concept-args concept-type args)
        native-id (str "dqs-native" uniq-num)
        extra-fields (merge {:document-name "CER-BDS_Terra"
                             :schema "data-quality-summary"}
                            (:extra-fields attributes))
        attributes (merge {:user-id (str "user" uniq-num)
                           :format "application/json"
                           :native-id native-id
                           :extra-fields extra-fields}
                          (dissoc attributes :extra-fields))]
    (concepts/create-any-concept provider-id concept-type uniq-num attributes)))
