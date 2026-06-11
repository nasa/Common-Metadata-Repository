(ns cmr.ingest.api.granules
  "Granule ingest functions in support of the ingest API."
  (:require
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common.log :refer [info]]
   [cmr.common.util :as util]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as srvc-errors]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.services.ingest-service :as ingest]
   [cmr.ingest.services.messages :as msg]))


(def GRANULE_WARNING_CONTEXT "Granule had the following warnings: ")
(defmulti validate-granule
  "Validates the granule in the request. It can handle a granule and collection sent as multipart-params
  or a normal request with the XML as the body."
  (fn [_provider-id _native-id request]
    (if (seq (:multipart-params request))
      :multipart-params
      :default)))

(defmethod validate-granule :default
  [provider-id native-id {:keys [body content-type headers request-context]}]
  (api-core/verify-provider-exists request-context provider-id)
  (let [concept (api-core/body->concept! :granule provider-id native-id body content-type headers)]
    (info (format "Validating granule %s from client %s"
                  (api-core/concept->loggable-string concept) (:client-id request-context)))
    (ingest/validate-granule request-context concept)
    {:status 200}))

(defn- multipart-param->concept
  "Converts a multipart parameter"
  [provider-id native-id concept-type {:keys [content-type content]}]
  {:metadata content
   :format (mt/keep-version content-type)
   :provider-id provider-id
   :native-id native-id
   :concept-type concept-type})

(defn validate-multipart-params
  "Validates that the multipart parameters includes only the expected keys."
  [expected-keys-set multipart-params]
  (let [provided-keys (set (keys multipart-params))]
    (when (not= expected-keys-set provided-keys)
      (srvc-errors/throw-service-error
        :bad-request
        (msg/invalid-multipart-params expected-keys-set provided-keys)))))

(defmethod validate-granule :multipart-params
  [provider-id native-id {:keys [multipart-params request-context]}]
  (api-core/verify-provider-exists request-context provider-id)
  (validate-multipart-params #{"granule" "collection"} multipart-params)

  (let [coll-concept (multipart-param->concept
                       provider-id native-id :collection (get multipart-params "collection"))
        gran-concept (multipart-param->concept
                       provider-id native-id :granule (get multipart-params "granule"))]
    (ingest/validate-granule-with-parent-collection request-context gran-concept coll-concept)
    {:status 200}))

(defn ingest-granule
  [provider-id native-id request]
  (let [{:keys [body content-type headers request-context]} request]
    (lt-validation/validate-write-token request-context provider-id)
    (api-core/verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
    (common-enabled/validate-write-enabled request-context "ingest")
    (let [concept (api-core/body->concept! :granule provider-id native-id body content-type headers)
          ;; Log the ingest attempt
          _ (info (format "Ingesting granule %s from client %s"
                          (api-core/concept->loggable-string concept)
                          (:client-id request-context)))
          save-granule-result (ingest/save-granule request-context concept)
          _(def sgr1 save-granule-result)
          _(tap> {:source "save-granule-result" :value save-granule-result})

          concept-to-log (api-core/concept-with-revision-id concept save-granule-result)]
      ;; Log the successful ingest, with the metadata size in bytes.
      (api-core/log-concept-with-metadata-size concept-to-log request-context)
      ;; (api-core/generate-ingest-response headers save-granule-result)
      (api-core/generate-ingest-response headers (util/remove-nil-keys
                                                (api-core/format-and-contextualize-warnings-existing-errors save-granule-result GRANULE_WARNING_CONTEXT nil)
                                                               ))
      )))

(
 comment
 (def hack (api-core/format-and-contextualize-warnings-existing-errors-granules sgr1))


(defn flatten-path-errors [m]
  (let [extract (fn [errs] (when (seq errs) (vec (mapcat #(.-errors %) errs))))]
    (-> m
        (update :warnings extract)
        (update :existing-errors extract))))

      
      
 (flatten-path-errors (api-core/format-and-contextualize-warnings-existing-errors-granules sgr1))
)


(defn delete-granule
  [provider-id native-id request]
  (let [{:keys [request-context headers]} request
        concept-attribs (api-core/set-revision-id
                         {:provider-id provider-id
                          :native-id native-id
                          :concept-type :granule}
                         headers)]
    (lt-validation/validate-write-token request-context provider-id)
    (api-core/verify-provider-exists request-context provider-id)
    (acl/verify-ingest-management-permission request-context :update :provider-object provider-id)
    (common-enabled/validate-write-enabled request-context "ingest")
    (info (format "Deleting granule %s from client %s"
                  (pr-str concept-attribs) (:client-id request-context)))
    (api-core/generate-ingest-response headers (ingest/delete-granule request-context concept-attribs))))
