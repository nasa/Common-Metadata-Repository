(ns cmr.ingest.data.indexer
  "Implements Ingest App datalayer access interface. Takes on the role of a proxy to indexer app."
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [cmr.ingest.config :as config]
            [cmr.common.config :as cfg]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.common.services.health-helper :as hh]
            [cmr.system-trace.core :refer [deftracefn]]
            [cmr.system-trace.http :as ch]
            [cmr.transmit.config :as transmit-config]
            [cmr.transmit.connection :as transmit-conn]
            [cmr.message-queue.services.queue :as queue]
            [cmr.acl.core :as acl]))

(defn- get-headers
  "Gets the headers to use for communicating with the indexer."
  [context]
  (assoc (ch/context->http-headers context) acl/token-header (transmit-config/echo-system-token)))

(defn- index-concept-via-http
  "Index concept using indexer app endpoints via http"
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

(defn- put-message-on-queue
  "Put an index operation on the message queue"
  [context msg]
  (debug "CONTEXT:" context)
  (let [queue-broker (get-in context [:system :queue-broker])
        queue-name (config/index-queue-name)]
    (when-not (queue/publish queue-broker queue-name msg)
      (errors/internal-error!
        (str "Index queue broker refused queue message " msg)))))

(defn- index-concept-via-queue
  "Put an index concept request on the queue to be consuemed by the indexer app"
  [context concept-id revision-id]
  (let [msg {:action :index-concept
             :concept-id concept-id
             :revision-id revision-id}]
    (put-message-on-queue context msg)))

(defn- delete-from-index-via-http
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
                delete-url status response)))))

(defn- delete-concept-from-index-via-queue
  "Put a delete concept request on the queue to be consumed by the indexer app"
  [context concept-id revision-id]
  (let [msg {:action :delete-concept
             :concept-id concept-id
             :revision-id revision-id}]
    (put-message-on-queue context msg)))


(defn- delete-provider-from-index-via-queue
  "Put a delete provider request on the queue to be consumed by the indexer app"
  [context provider-id]
  (let [msg {:action :delete-provider
             :provider-id provider-id}]
    (put-message-on-queue context msg)))

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
  (debug "USE-QUEUE?:" (config/use-index-queue?))
  (if (config/use-index-queue?)
    (index-concept-via-queue context concept-id revision-id)
    (index-concept-via-http context concept-id revision-id)))

(deftracefn delete-concept-from-index
  "Delete a concept with given revision-id from index."
  [context concept-id revision-id]
  (if (config/use-index-queue?)
    (delete-concept-from-index-via-queue context concept-id revision-id)
    (delete-from-index-via-http context (format "%s/%s" concept-id revision-id))))

(deftracefn delete-provider-from-index
  "Delete a provider with given provider-id from index."
  [context provider-id]
  (delete-from-index-via-http context (format "provider/%s" provider-id)))

(defn- get-indexer-health-fn
  "Returns the health status of the indexer app"
  [context]
  (let [conn (transmit-config/context->app-connection context :indexer)
        request-url (str (transmit-conn/root-url conn) "/health")
        response (client/get request-url {:accept :json
                                          :throw-exceptions false
                                          :connection-manager (transmit-conn/conn-mgr conn)})
        {:keys [status body]} response
        result (json/decode body true)]
    (if (= 200 status)
      {:ok? true :dependencies result}
      {:ok? false :problem result})))

(defn get-indexer-health
  "Returns the indexer health with timeout handling."
  [context]
  (let [timeout-ms (* 1000 (+ 2 (hh/health-check-timeout-seconds)))]
    (hh/get-health #(get-indexer-health-fn context) timeout-ms)))
