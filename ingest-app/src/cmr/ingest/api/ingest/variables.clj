(ns cmr.ingest.api.ingest.variables
  "Variable ingest functions in support of the ingest API."
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
     set-user-id
     verify-provider-exists]]
   [cmr.ingest.services.ingest-service :as ingest]))

(defn validate-variable
  [request]
  (let [{:keys [body content-type headers request-context]} request
        concept (body->concept :variable body content-type headers)
        validation-options (get-validation-options headers)
        _ (info (format "Validating Variable %s from client %s ..."
                        (concept->loggable-string concept)
                        (:client-id request-context)))
        validation (ingest/validate-variable
                    request-context concept validation-options)]
    (->> [:warnings]
         (select-keys (contextualize-warnings validation))
         (util/remove-nil-keys)
         (generate-validate-response headers))))

(defn ingest-variable
  [request]
  (let [{:keys [body content-type headers request-context]} request]
    (acl/verify-ingest-management-permission request-context :update)
    (common-enabled/validate-write-enabled request-context "ingest")
    (let [concept (body->concept :variable body content-type headers)
          validation-options (get-validation-options headers)
          save-result (ingest/save-variable
                       request-context
                       (set-user-id concept request-context headers)
                       validation-options)]
      (info (format "Ingesting variable %s from client %s"
                    (concept->loggable-string
                     (assoc concept
                            :long-name (:long-name save-result)))
                    (:client-id request-context)))
      (generate-ingest-response
       headers
       (contextualize-warnings
        ;; long-name is added just for the logging above.
        ;; dissoc it so that it remains the same as the original code.
        (dissoc save-result :long-name))))))
