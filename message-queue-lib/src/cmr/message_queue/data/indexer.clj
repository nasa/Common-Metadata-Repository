(ns cmr.message-queue.data.indexer
  "Implements Ingest App datalayer access interface. Takes on the role of a proxy to indexer app."
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.common.services.health-helper :as hh]
            [cmr.system-trace.core :refer [deftracefn]]
            [cmr.system-trace.http :as ch]
            [cmr.transmit.config :as transmit-config]
            [cmr.transmit.connection :as transmit-conn]
            [cmr.acl.core :as acl]))

(defn- get-headers
  "Gets the headers to use for communicating with the indexer."
  [context]
  (assoc (ch/context->http-headers context) acl/token-header (transmit-config/echo-system-token)))

(deftracefn reindex-provider-collections
  "Reindexes all the collections in the provider"
  [context provider-ids]
  (let [conn (transmit-config/context->app-connection context :indexer)
        url (format "%s/reindex-provider-collections"
                    (transmit-conn/root-url conn))
        response (client/post url {:content-type :json
                                   :throw-exceptions false
                                   :body (json/generate-string provider-ids)
                                   :accept :json
                                   :headers (get-headers context)
                                   :connection-manager (transmit-conn/conn-mgr conn)})
        status (:status response)]
    (when-not (= 200 status)
      (errors/internal-error!
        (str "Unexpected status"  status  " " (:body response))))))

(deftracefn index-concept
  "Forward newly created concept for indexer app consumption."
  [context concept-id revision-id]
  (let [conn (transmit-config/context->app-connection context :indexer)
        indexer-url (transmit-conn/root-url conn)
        concept-attribs {:concept-id concept-id, :revision-id revision-id}
        response (client/post indexer-url {:body (json/generate-string concept-attribs)
                                           :content-type :json
                                           :throw-exceptions false
                                           :accept :json
                                           :headers (get-headers context)
                                           :connection-manager (transmit-conn/conn-mgr conn)})
        status (:status response)]
    (when-not (= 201 status)
      (errors/internal-error! (str "Operation to index a concept failed. Indexer app response status code: "  status  " " response)))))

(defn- delete-from-indexer
  "Execute http delete of the given url on the indexer"
  [context delete-url]
  (let [conn (transmit-config/context->app-connection context :indexer)
        indexer-root (transmit-conn/root-url conn)
        response (client/delete (format "%s/%s" indexer-root delete-url)
                                {:accept :json
                                 :throw-exceptions false
                                 :headers (get-headers context)
                                 :connection-manager (transmit-conn/conn-mgr conn)})
        status (:status response)]
    (when-not (some #{200, 204} [status])
      (errors/internal-error!
        (format "Delete %s operation failed. Indexer app response status code: %s %s"
                delete-url status response)))
    response))

(deftracefn delete-concept-from-index
  "Delete a concept with given revision-id from index."
  [context concept-id revision-id]
  (delete-from-indexer context (format "%s/%s" concept-id revision-id)))

(deftracefn delete-provider-from-index
  "Delete a provider with given provider-id from index."
  [context provider-id]
  (delete-from-indexer context (format "provider/%s" provider-id)))

(defmulti handle-indexing-request
  "Handles indexing requests received from the message queue."
  (fn [request-type msg]
    (keyword request-type)))

(defmethod handle-indexing-request :index-concept
  [request-type msg]
  (let [{:keys [concept-id revision-id]} msg]
    (debug "Received index-concept request for concept-id" concept-id "revision-id" revision-id)
    ;; TODO make call here
    ))

(defmethod handle-indexing-request :delete-concept
  [request-type msg]
  (let [{:keys [concept-id revision-id]} msg]
    (debug "Received delete-concept request for concept-id" concept-id "revision-id" revision-id)
    ;; TODO make call here
    ))

(defmethod handle-indexing-request :re-index-provider
  [request-type msg]
  (let [{:keys [provider-id]} msg]
    (debug "Received re-index-provider request for provider-id" provider-id)
    ;; TODO make call here
    ))

(defmethod handle-indexing-request :delete-provider
  [request-type msg]
  (let [{:keys [provider-id]} msg]
    (debug "Received delete-provider request for provider-id" provider-id)
    ;; TODO make call here
    ))

(defmethod handle-indexing-request :default
  [request-type _]
  (errors/internal-error! (str "Received unknown message type: " request-type)))
