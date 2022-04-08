(ns cmr.ingest.services.ingest-service.service
  (:require
   [cmr.common.util :refer [defn-timed]]
   [cmr.common.services.errors :as errors]
   [cmr.common-app.services.kms-fetcher :as kms-fetcher]
   [cmr.common.validations.core :as cm-validation]
   [cmr.ingest.services.messages :as msg]
   [cmr.ingest.validation.validation :as validation]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.umm-spec.umm-spec-core :as spec]
   [cmr.umm-spec.validation.umm-spec-validation-core :as umm-spec-validation]))

(defn- add-extra-fields-for-service
  "Returns service concept with fields necessary for ingest into metadata db
  under :extra-fields."
  [context concept service]
  (assoc concept :extra-fields {:service-name (:Name service)
                                :service-type (:Type service)}))

(defn- add-top-fields-for-service
  "Returns service concept with top level fields needed for ingest into metadata db"
  [concept]
  (assoc concept
         :provider-id (:provider-id concept)
         :native-id (:native-id concept)))

(defn if-errors-throw
  "Throws an error if there are any errors."
  [error-type errors]
  (when (seq errors)
    (errors/throw-service-errors error-type errors)))

(defn- match-kms-related-url-content-type-type-and-subtype
  "Create a kms match validator for use by validation-service"
  [context]
  (let [kms-index (kms-fetcher/get-kms-index context)]
    [{:RelatedURLs [(validation/match-kms-keywords-validation
                     kms-index
                     :related-urls
                     msg/related-url-content-type-type-subtype-not-matching-kms-keywords)
                    (cm-validation/every [{:Format (validation/match-kms-keywords-validation-single
                                                    kms-index
                                                    :granule-data-format
                                                    msg/getdata-format-not-matches-kms-keywords)}
                                          {:MimeType (validation/match-kms-keywords-validation-single
                                                      kms-index
                                                      :mime-type
                                                      msg/mime-type-not-matches-kms-keywords)}])]}]))

(defn- validate-all-fields
  "Check all fields that need to be validated. Currently this is the Related URL Content Type, Type, and
  Subtype fields. Throws an error if errors are found"
  [context service]
  (let [errors (seq (umm-spec-validation/validate-service service (match-kms-related-url-content-type-type-and-subtype context)))]
    (if-errors-throw :bad-request errors)))

(defn-timed save-service
  "Store a service concept in mdb and indexer. Return concept-id, and revision-id."
  [context concept]
  (let [{:keys [:format :metadata]} concept
        service (spec/parse-metadata context :service format metadata)
        _ (validate-all-fields context service)
        full-concept (as-> concept intermediate
                      (add-extra-fields-for-service context intermediate service)
                      (add-top-fields-for-service intermediate))
        {:keys [concept-id revision-id]} (mdb2/save-concept context full-concept)]
    {:concept-id concept-id
     :revision-id revision-id}))
