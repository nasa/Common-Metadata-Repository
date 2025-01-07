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

(defmethod concepts/get-sample-metadata :order-option
  [_concept-type]
  (json/generate-string
   {:Id "0AF0BB4E",
    :Name "With Browse",
    :Description "",
    :Form "<form xmlns=\"http://echo.nasa.gov/v9/echoforms\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"> <model> <instance> <ecs:options xmlns:ecs=\"http://ecs.nasa.gov/options\"> <!-- ECS distribution options example --> <ecs:distribution> <ecs:mediatype> <ecs:value>FtpPull</ecs:value> </ecs:mediatype> <ecs:mediaformat> <ecs:ftppull-format> <ecs:value>FILEFORMAT</ecs:value> </ecs:ftppull-format> </ecs:mediaformat> </ecs:distribution> <ecs:do-ancillaryprocessing>true</ecs:do-ancillaryprocessing> <ecs:ancillary> <ecs:orderBrowse/> </ecs:ancillary> </ecs:options> </instance> </model> <ui> <group id=\"mediaOptionsGroup\" label=\"Media Options\" ref=\"ecs:distribution\"> <output id=\"MediaTypeOutput\" label=\"Media Type:\" relevant=\"ecs:mediatype/ecs:value ='FtpPull'\" type=\"xsd:string\" value=\"'HTTPS Pull'\"/> <output id=\"FtpPullMediaFormatOutput\" label=\"Media Format:\" relevant=\"ecs:mediaformat/ecs:ftppull-format/ecs:value='FILEFORMAT'\" type=\"xsd:string\" value=\"'File'\"/> </group> <group id=\"checkancillaryoptions\" label=\"Additional file options:\" ref=\"ecs:ancillary\" relevant=\"//ecs:do-ancillaryprocessing = 'true'\"> <input label=\"Include associated Browse file in order\" ref=\"ecs:orderBrowse\" type=\"xsd:boolean\"/> </group> </ui> </form>"
    :Scope "PROVIDER",
    :SortKey "Name",
    :Deprecated false,
    :MetadataSpecification {:Name "Order Option",
                            :Version "1.0.0",
                            :URL "https://cdn.earthdata.nasa.gov/generics/order-option/v1.0.0"}}))

(defmethod concepts/create-concept :order-option
  [concept-type & args]
  (let [[provider-id uniq-num attributes] (concepts/parse-create-concept-args concept-type args)
        native-id (str "oo-native" uniq-num)
        extra-fields (merge {:document-name "ORDER-OPTION-1"
                             :schema "order-option"}
                            (:extra-fields attributes))
        attributes (merge {:user-id (str "user" uniq-num)
                           :format "application/json"
                           :native-id native-id
                           :extra-fields extra-fields}
                          (dissoc attributes :extra-fields))]
    (concepts/create-any-concept provider-id concept-type uniq-num attributes)))

(defmethod concepts/get-sample-metadata :visualization
  [_concept-type]
  (json/generate-string
   {:Id "MODIS_Combined_L3_IGBP_Land_Cover_Type_Annual",
    :VisualizationType "tiles",
    :Name "Land Cover Type (L3, IGBP, Annual, Best Available, MODIS, Aqua+Terra)",
    :Specification {:specificationtile1 "a", :specificationtile2 "b"},
    :Generation {:generationtile1 "a", :generationtile2 "b"},
    :ConceptIds [{:Type "STD",
                  :Value "C186286578-LPDAAC_ECS",
                  :ShortName "MCD12Q1",
                  :Title "MODIS/Terra+Aqua Land Cover Type Yearly L3 Global 500m SIN Grid V006",
                  :Version "006",
                  :DataCenter "LPDAAC_ECS"}],
    :MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/generics/visualization/v1.0.0",
                            :Name "Visualization",
                            :Version "1.0.0"}}))

(defmethod concepts/create-concept :visualization
  [concept-type & args]
  (let [[provider-id uniq-num attributes] (concepts/parse-create-concept-args concept-type args)
        native-id (str "vsl-native" uniq-num)
        extra-fields (merge {:document-name "vsl-docname"
                             :schema "visualization"}
                            (:extra-fields attributes))
        attributes (merge {:user-id (str "user" uniq-num)
                           :format "application/json"
                           :native-id native-id
                           :extra-fields extra-fields}
                          (dissoc attributes :extra-fields))]
    (concepts/create-any-concept provider-id concept-type uniq-num attributes)))
