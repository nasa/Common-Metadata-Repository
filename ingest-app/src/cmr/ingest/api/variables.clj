(ns cmr.ingest.api.variables
  "Variable ingest functions in support of the ingest API."
  (:require
   [clojure.string :as string]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.config :as ingest-config]
   [cmr.ingest.services.ingest-service :as ingest]
   [cmr.ingest.validation.validation :as v]
   [cmr.umm-spec.umm-spec-core :as spec]))

(defn- validate-and-prepare-variable-concept
  "Validate variable concept, set the concept format and returns the concept;
  throws error if the metadata is not a valid against the UMM variable JSON schema."
  [concept]
  (let [concept (update-in concept [:format] (partial ingest/fix-ingest-concept-format :variable))]
    (v/validate-concept-request concept)
    (v/validate-concept-metadata concept)
    concept))

(defn ingest-variable
  "Processes a request to create or update a variable."
  ([provider-id native-id request]
   (ingest-variable provider-id native-id request nil nil))
  ([provider-id native-id request coll-concept-id coll-revision-id]
   (let [provider-id (if coll-concept-id
                       (last (string/split coll-concept-id #"C[0-9]+-"))
                       provider-id)
         {:keys [body content-type headers request-context]} request
         concept (api-core/body->concept!
                  :variable provider-id native-id body content-type headers)]

     (lt-validation/validate-launchpad-token request-context)
     (api-core/verify-provider-exists request-context provider-id)
     (acl/verify-ingest-management-permission
      request-context :update :provider-object provider-id)
     (common-enabled/validate-write-enabled request-context "ingest")
     ;; Ensures the associated collection is visible and not deleted.
     (v/validate-variable-associated-collection
      request-context coll-concept-id coll-revision-id)
     (let [concept (validate-and-prepare-variable-concept concept)
           {concept-format :format metadata :metadata data :data} concept
           variable (spec/parse-metadata request-context :variable concept-format metadata)
           validate-var-result (v/umm-spec-validate-variable
                                variable request-context (not (ingest-config/validate-umm-var-keywords)))
           concept-with-user-id
           (util/remove-nil-keys
            (-> concept
                (api-core/set-user-id request-context headers)
                (assoc :coll-concept-id coll-concept-id
                       :coll-revision-id (when coll-revision-id
                                           (read-string coll-revision-id)))))
           ;; Log the ingest attempt
           _ (info (format "Ingesting variable %s from client %s"
                           (api-core/concept->loggable-string concept-with-user-id)
                           (:client-id request-context)))
           save-variable-result (ingest/save-variable request-context concept-with-user-id)
           concept-to-log (api-core/concept-with-revision-id concept-with-user-id save-variable-result)]
       ;; Log the successful ingest, with the metadata size in bytes.
       (api-core/log-concept-with-metadata-size concept-to-log request-context)
       (api-core/generate-ingest-response headers
                                          ;; if validate-var-result is truthy here, then it contains warnings
                                          (if (seq validate-var-result)
                                            (merge save-variable-result
                                                   {:warnings (errors/errors->message validate-var-result)})
                                            save-variable-result))))))

(defn delete-variable
  "Deletes the variable with the given provider id and native id."
  [provider-id native-id request]
  (api-core/delete-concept :variable provider-id native-id request))
