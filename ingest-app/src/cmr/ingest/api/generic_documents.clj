(ns cmr.ingest.api.generic-documents
  "Subscription ingest functions in support of the ingest API."
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clojure.java.io :as jio]
   [clojure.string :as string]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common-app.services.ingest.subscription-common :as sub-common]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.services.errors :as errors]
   [cmr.ingest.api.core :as api-core]
   [cmr.ingest.services.ingest-service :as ingest]
   [cmr.ingest.services.subscriptions-helper :as jobs]
   [cmr.ingest.validation.validation :as v]
   [cmr.transmit.access-control :as access-control]
   [cmr.transmit.config :as config]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.transmit.search :as search]
   [cmr.transmit.urs :as urs]
   [cmr.schema-validation.json-schema :as js-validater])
  (:import
   [java.util UUID]))


(def approved-generics {:grid ["0.0.1"]})

(defn- approved-generic?
  "Check to see if a requested generic is on the approved list"
  [schema version]
  true)

(defn- validate-json-against-schema
  "validate a document, returns an array of errors if there are problems
   Parameters:
   * schema name, the name of an approved generic
   * schema version, the schema version number, without 'v'" 
  [schema version raw-json]
  (if-some [schema-url (jio/resource (format "generics/%s/v%s/schema.json" schema version))]
    (let [schema-file (slurp schema-url)
          schema-obj (js-validater/json-string->json-schema schema-file)]
      (js-validater/validate-json schema-obj raw-json))
    ["Schema not found"]))

 (defn create-generic-document
   [request]
   "Check a document for fitness to be ingested, and then ingest it. Records can
   be rejected for the following reasons:
   * unsupported schema
   * failed schema
   * failed validation rules (external) (pending)
   * Document name not unique"
   (let [{:keys [body route-params]} request
         provider-id (:provider-id route-params)
         raw-document (slurp (:body request))
         document (json/parse-string raw-document true)
         specification (:MetadataSpecification document)
         spec-name (string/lower-case (:Name specification))
         spec-version (:Version specification)
         ]
     (if-some [validation-errors (validate-json-against-schema spec-name spec-version raw-document)]
       validation-errors
       (let [something-of-importance false]
         ; need to call DB layer to send the document down stream
         (println "stub function: create " (keys request))
         {:status 204}))))

 (defn read-generic-document [request]
   (println "stub function: read " request))
 (defn update-generic-document [request]
   (println "stub function: update " request))
 (defn delete-generic-document [request]
   (println "stub function: delete " request))

(comment defn create-subscription
  "Processes a request to create a subscription. A native id will be generated."
  [provider-id request]
  (let [{:keys [body content-type headers request-context]} request]
    (common-ingest-checks request-context provider-id)
    (let [tmp-subscription (api-core/body->concept!
                            :subscription
                            provider-id
                            (str (UUID/randomUUID))
                            body
                            content-type
                            headers)
          native-id (get-unique-native-id request-context tmp-subscription)
          sub-with-native-id (assoc tmp-subscription :native-id native-id)
          final-sub (add-fields-if-missing request-context sub-with-native-id)
          subscriber-id (get-subscriber-id final-sub)]
      (check-valid-user request-context subscriber-id)
      (check-ingest-permission request-context provider-id subscriber-id)
      (validate-query request-context final-sub)
      (check-duplicate-subscription request-context final-sub)
      (check-subscription-limit request-context final-sub)
      (perform-subscription-ingest request-context final-sub headers))))

(comment defn create-subscription-with-native-id
  "Processes a request to create a subscription using the native-id provided."
  [provider-id native-id request]
  (let [{:keys [body content-type headers request-context]} request]
    (common-ingest-checks request-context provider-id)
    (when (native-id-collision? request-context provider-id native-id)
      (errors/throw-service-error
       :conflict
       (format "Subscription with provider-id [%s] and native-id [%s] already exists."
               provider-id
               native-id)))
    (let [tmp-subscription (api-core/body->concept!
                            :subscription
                            provider-id
                            native-id
                            body
                            content-type
                            headers)
          final-sub (add-fields-if-missing request-context tmp-subscription)
          subscriber-id (get-subscriber-id final-sub)]
      (check-valid-user request-context subscriber-id)
      (check-ingest-permission request-context provider-id subscriber-id)
      (validate-query request-context final-sub)
      (check-duplicate-subscription request-context final-sub)
      (check-subscription-limit request-context final-sub)
      (perform-subscription-ingest request-context final-sub headers))))

(comment defn create-or-update-subscription-with-native-id
  "Processes a request to create or update a subscription. This function
  does NOT fail on collisions. This is mapped to PUT methods to preserve
  existing functionality."
  [provider-id native-id request]
  (let [{:keys [body content-type headers request-context]} request
        _ (common-ingest-checks request-context provider-id)
        tmp-subscription (api-core/body->concept! :subscription
                                                  provider-id
                                                  native-id
                                                  body
                                                  content-type
                                                  headers)
        old-subscriber (when-let [original-subscription
                                  (first (mdb/find-concepts
                                          request-context
                                          {:provider-id provider-id
                                           :native-id native-id
                                           :exclude-metadata false
                                           :latest true}
                                          :subscription))]
                         (get-in original-subscription [:extra-fields :subscriber-id]))
        final-sub (add-fields-if-missing request-context tmp-subscription)
        new-subscriber (get-subscriber-id final-sub)]
    (check-valid-user request-context new-subscriber)
    (check-ingest-permission request-context provider-id new-subscriber old-subscriber)
    (validate-query request-context final-sub)
    (check-duplicate-subscription request-context final-sub)
    (check-subscription-limit request-context final-sub)
    (perform-subscription-ingest request-context final-sub headers)))

(comment defn delete-subscription
  "Deletes the subscription with the given provider id and native id."
  [provider-id native-id request]
  (let [{:keys [body content-type headers request-context]} request
        _ (common-ingest-checks request-context provider-id)
        subscriber-id (when-let [subscription (first (mdb/find-concepts
                                                      request-context
                                                      {:provider-id provider-id
                                                       :native-id native-id
                                                       :exclude-metadata false
                                                       :latest true}
                                                      :subscription))]
                        (get-in subscription [:extra-fields :subscriber-id]))
        concept-attribs (-> {:provider-id provider-id
                             :native-id native-id
                             :concept-type :subscription}
                            (api-core/set-revision-id headers)
                            (api-core/set-user-id request-context headers))]
    (check-ingest-permission request-context provider-id subscriber-id)
    (info (format "Deleting subscription %s from client %s"
                  (pr-str concept-attribs) (:client-id request-context)))
    (api-core/generate-ingest-response headers
                                       (api-core/format-and-contextualize-warnings-existing-errors
                                        (ingest/delete-concept
                                         request-context
                                         concept-attribs)))))
