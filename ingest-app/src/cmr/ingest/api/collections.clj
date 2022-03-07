(ns cmr.ingest.api.collections
  "Collection ingest functions in support of the ingest API."
  (:require
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.util :as util]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.services.ingest-service :as ingest]))

(def VALIDATE_KEYWORDS_HEADER "cmr-validate-keywords")
(def ENABLE_UMM_C_VALIDATION_HEADER "cmr-validate-umm-c")
(def TESTING_EXISTING_ERRORS_HEADER "cmr-test-existing-errors")

(defn get-validation-options
  "Returns a map of validation options with boolean values"
  [headers]
  {:validate-keywords? (= "true" (get headers VALIDATE_KEYWORDS_HEADER))
   :validate-umm? (= "true" (get headers ENABLE_UMM_C_VALIDATION_HEADER))
   :test-existing-errors? (= "true" (get headers TESTING_EXISTING_ERRORS_HEADER))})

(defn validate-collection
  [provider-id native-id request]
  (let [{:keys [body content-type params headers request-context]} request
        concept (api-core/body->concept! :collection provider-id native-id body content-type headers)
        validation-options (get-validation-options headers)]
    (api-core/verify-provider-exists request-context provider-id)
    (info (format "Validating Collection %s from client %s"
                  (api-core/concept->loggable-string concept) (:client-id request-context)))
    (let [validate-response (ingest/validate-and-prepare-collection request-context
                                                                    concept
                                                                    validation-options)]
      (api-core/generate-validate-response
       headers
       (util/remove-nil-keys
        (select-keys (api-core/format-and-contextualize-warnings-existing-errors validate-response)
                     [:warnings :existing-errors]))))))

(defn ingest-collection
  [provider-id native-id request]
  (let [{:keys [body content-type params headers request-context]} request]
    (lt-validation/validate-launchpad-token request-context)
    (api-core/verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
    (common-enabled/validate-write-enabled request-context "ingest")
    (let [concept (api-core/body->concept! :collection provider-id native-id body content-type headers)
          validation-options (get-validation-options headers)
          ;; Log the ingest attempt
          _ (info (format "Ingesting collection %s from client %s"
                          (api-core/concept->loggable-string concept)
                          (:client-id request-context)))
          save-collection-result (ingest/save-collection
                                  request-context
                                  (api-core/set-user-id concept request-context headers)
                                  validation-options)
          concept-to-log (-> concept
                             (api-core/concept-with-revision-id save-collection-result)
                             (assoc :entry-title (:entry-title save-collection-result)))]
      ;; Log the successful ingest, with the metadata size in bytes.
      (api-core/log-concept-with-metadata-size concept-to-log request-context)
      (api-core/generate-ingest-response headers
                                         (api-core/format-and-contextualize-warnings-existing-errors
                                          ;; entry-title is added just for the logging above.
                                          ;; dissoc it so that it remains the same as the
                                          ;; original code.
                                          (dissoc save-collection-result :entry-title))))))

(defn delete-collection
  "Delete the collection with the given provider id and native id."
  [provider-id native-id request]
  (api-core/delete-concept :collection provider-id native-id request))
