(ns cmr.common-app.api.request-write-access-augmenter
  "Adds Write Access prefix to token in the request context of the given request.
   This function should be called on routes that will ingest into CMR.
   The CMR Ingest prefix is used to indicate to legacy services that when
   validating the Launchpad token, the NAMS CMR Ingest group should also be checked."
  (:require
   [cmr.common-app.api.request-context-user-augmenter :as augmenter]))

(def URS_TOKEN_MAX_LENGTH 100)
(def WRITE_ACCESS_SEPARATOR "WRITE_ACCESS:")

(defn is-launchpad-token?
  "Returns true if the given token is a launchpad token.
   Currently we only check the length of the token to decide."
  [token]
  (> (count token) URS_TOKEN_MAX_LENGTH))

(defn add-write-access-to-request
  "Add Write Access prefix to token in the request context of the given request.
   This function should be called on routes that will ingest into CMR.
   The CMR Ingest prefix is used to indicates to legacy services that when
   validating the Launchpad token, the NAMS CMR Ingest group should also be checked.
   Ingest will only be allowed if the user is in the NAMS CMR Ingest group and
   also has the right ACLs which is based on Earthdata Login uid."
  [request]
  (let [token (-> request :request-context :token)]
    (if (is-launchpad-token? token)
      ;; for Launchpad token add CMR_INGEST: prefix so that legacy service
      ;; can do extra validation to check if the user has been approved for
      ;; CMR Ingest workflow.
      (-> request
          (update-in [:request-context :token] #(str WRITE_ACCESS_SEPARATOR %))
          ;; the next line is needed because we had the ring handler tied to the context
          ;; when the ring handler is added to the middleware stack.
          ;; Now we updated the token inside the context, we need to update the handler
          ;; to be tied to the new context to use the updated token.
          (update-in [:request-context] augmenter/add-user-id-and-sids-to-context))
      request)))
