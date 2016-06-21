(ns cmr.system-int-test.utils.index-util
  "provides index related utilities."
  (:require [clojure.test :refer [is]]
            [clj-http.client :as client]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.indexer.config :as config]
            [cmr.system-int-test.utils.queue :as queue]
            [cmr.transmit.config :as transmit-config]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cheshire.core :as json]
            [cmr.system-int-test.system :as s]
            [cmr.message-queue.test.queue-broker-side-api :as qb-side-api]))

(defn refresh-elastic-index
  []
  (client/post (url/elastic-refresh-url) {:connection-manager (s/conn-mgr)}))

(defn wait-until-indexed
  "Wait until ingested concepts have been indexed"
  []
  (qb-side-api/wait-for-terminal-states)
  (refresh-elastic-index))

(defn full-refresh-collection-granule-aggregate-cache
  "Triggers a full refresh of the collection granule aggregate cache in the indexer."
  []
  (let [response (client/post
                  (url/full-refresh-collection-granule-aggregate-cache-url)
                  {:connection-manager (s/conn-mgr)
                   :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                   :throw-exceptions false})]
    (is (= 200 (:status response)) (:body response))))

(defn partial-refresh-collection-granule-aggregate-cache
  "Triggers a partial refresh of the collection granule aggregate cache in the indexer."
  []
  (let [response (client/post
                  (url/partial-refresh-collection-granule-aggregate-cache-url)
                  {:connection-manager (s/conn-mgr)
                   :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                   :throw-exceptions false})]
    (is (= 200 (:status response)) (:body response))))

(defn update-indexes
  "Makes the indexer update the index set mappings and indexes"
  []
  (let [response (client/post (url/indexer-update-indexes)
                   {:connection-manager (s/conn-mgr)
                    :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                    :throw-exceptions false})]
    (is (= 200 (:status response)) (:body response))))

(defn delete-all-tags-from-elastic
  "Delete all tags from elasticsearch index"
  []
  (let [response (client/delete (url/elastic-delete-tags-url)
                   {:connection-manager (s/conn-mgr)
                    :body "{\"query\": {\"match_all\": {}}}"
                    :throw-exceptions false})]
    (is (= 200 (:status response)) (:body response))))

(defn reindex-tags
  "Re-index all tags"
  []
  (let [response (client/post (url/indexer-reindex-tags-url)
                   {:connection-manager (s/conn-mgr)
                    :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                    :throw-exceptions false})]
    (is (= 200 (:status response)) (:body response))))


(defn- messages+id->message
  "Returns the first message for a given message id."
  [messages id]
  (first (filter #(= id (:id %)) messages)))

(defn- concept-history
  "Returns a map of concept id revision id tuples to the sequence of states for each one."
  [message-states]
  (let [int-states (for [mq message-states
                         :let [{{:keys [action-type]
                                 {:keys [concept-id revision-id id]} :message} :action} mq
                               result-state (:state (messages+id->message (:messages mq) id))]]
                     {[concept-id revision-id] [{:action action-type :result result-state}]})]
    (apply merge-with concat int-states)))

(defn get-concept-message-queue-history
  "Gets the message queue history and then returns a map of concept-id revision-id tuples to the
  sequence of states for each one."
  [queue-name]
  (concept-history (qb-side-api/get-message-queue-history queue-name)))

(defn reset-message-queue-behavior-fixture
  "This is a clojure.test fixture that will reset the message queue behavior to normal processing
  after a test completes."
  []
  (fn [f]
    (try
      (f)
      (finally
        (s/only-with-real-message-queue
          (qb-side-api/set-message-queue-retry-behavior 0)
          (qb-side-api/set-message-queue-publish-timeout 10000))))))