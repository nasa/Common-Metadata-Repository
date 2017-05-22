(ns cmr.ingest.api.ingest.collections
  "Collection ingest functions in support of the ingest API."
  (:require
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.util :as util]
   [cmr.ingest.api.ingest.core :refer [
     body->concept
     concept->loggable-string
     contextualize-warnings
     generate-ingest-response
     generate-validate-response
     get-validation-options
     set-revision-id
     set-user-id
     verify-provider-exists]]
   [cmr.ingest.services.ingest-service :as ingest]))

(defn validate-collection
  [provider-id native-id request]
  (let [{:keys [body content-type params headers request-context]} request
        concept (body->concept :collection provider-id native-id body content-type headers)
        validation-options (get-validation-options headers)]
    (verify-provider-exists request-context provider-id)
    (info (format "Validating Collection %s from client %s"
                  (concept->loggable-string concept) (:client-id request-context)))
    (let [validate-response (ingest/validate-and-prepare-collection request-context
                                                                    concept
                                                                    validation-options)]
      (generate-validate-response headers (util/remove-nil-keys (select-keys (contextualize-warnings validate-response) [:warnings]))))))

(defn ingest-collection
  [provider-id native-id request]
  (let [{:keys [body content-type params headers request-context]} request]
    (verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
    (common-enabled/validate-write-enabled request-context "ingest")
    (let [concept (body->concept :collection provider-id native-id body content-type headers)
          validation-options (get-validation-options headers)
          save-collection-result (ingest/save-collection
                                  request-context
                                  (set-user-id concept request-context headers)
                                  validation-options)]
      (info (format "Ingesting collection %s from client %s"
              (concept->loggable-string (assoc concept :entry-title (:entry-title save-collection-result)))
              (:client-id request-context)))
      (generate-ingest-response headers (contextualize-warnings
                                          ;; entry-title is added just for the logging above.
                                          ;; dissoc it so that it remains the same as the original code.
                                          (dissoc save-collection-result :entry-title))))))

(defn delete-collection
  [provider-id native-id request]
  (let [{:keys [request-context params headers]} request
        concept-attribs (-> {:provider-id provider-id
                             :native-id native-id
                             :concept-type :collection}
                            (set-revision-id headers)
                            (set-user-id request-context headers))]
    (common-enabled/validate-write-enabled request-context "ingest")
    (verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
    (info (format "Deleting collection %s from client %s"
                  (pr-str concept-attribs) (:client-id request-context)))
    (generate-ingest-response headers
                              (contextualize-warnings (ingest/delete-concept
                                                        request-context
                                                        concept-attribs)))))
