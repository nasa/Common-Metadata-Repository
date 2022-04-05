(ns cmr.ingest.services.ingest-service.tool
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

(defn- add-extra-fields-for-tool
  "Returns tool concept with fields necessary for ingest into metadata db
  under :extra-fields."
  [context concept tool]
  (assoc concept :extra-fields {:tool-name (:Name tool)
                                :tool-type (:Type tool)}))

(defn if-errors-throw
  "Throws an error if there are any errors."
  [error-type errors]
  (when (seq errors)
    (errors/throw-service-errors error-type errors)))

(defn- match-kms-content-type-type-and-subtype
  "Create a kms match validator for use by validation-tool"
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
                                                      msg/mime-type-not-matches-kms-keywords)}])]

      :URL (validation/match-kms-keywords-validation-single
            kms-index
            :related-urls
            msg/url-content-type-type-subtype-not-matching-kms-keywords)}]))

(defn- validate-all-fields
  "Check all fields that need to be validated. Currently this is the Related URL Content Type, Type, and
  Subtype fields. Throws an error if errors are found"
  [context tool]
  (let [errors (seq (umm-spec-validation/validate-tool tool (match-kms-content-type-type-and-subtype context)))]
    (if-errors-throw :bad-request errors)))

(defn-timed save-tool
  "Store a tool concept in mdb and indexer."
  [context concept]
  (let [metadata (:metadata concept)
        tool (spec/parse-metadata context :tool (:format concept) metadata)
        _ (validate-all-fields context tool)
        concept (add-extra-fields-for-tool context concept tool)
        {:keys [concept-id revision-id]} (mdb2/save-concept context
                                          (assoc concept :provider-id (:provider-id concept)
                                                         :native-id (:native-id concept)))]
      {:concept-id concept-id
       :revision-id revision-id}))
