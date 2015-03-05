(ns ^{:doc "provides index related utilities."}
  cmr.system-int-test.utils.index-util
  (:require [clj-http.client :as client]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.indexer.config :as config]
            [cmr.system-int-test.utils.queue :as queue]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cheshire.core :as json]
            [clojure.walk :as walk]
            [cmr.system-int-test.system :as s]))

(defn refresh-elastic-index
  []
  (client/post (url/elastic-refresh-url) {:connection-manager (s/conn-mgr)}))

(defn wait-until-indexed
  "Wait until ingested concepts have been indexed"
  []
  (when (config/use-index-queue?)
    (client/post (url/dev-system-wait-for-indexing-url) {:connection-manager (s/conn-mgr)}))
  (refresh-elastic-index))

(defn get-message-queue-history
  "Returns the message queue history."
  []
  (-> (client/get (url/dev-system-get-message-queue-history-url) {:connection-manager (s/conn-mgr)})
      :body
      json/decode
      walk/keywordize-keys))

(defn- messages+id->message
  "Returns the first message for a given message id."
  [messages id]
  (first (filter #(= id (:id %)) messages)))

(defn concept-history
  "Returns a map of concept id revision id tuples to the sequence of states for each one"
  [messages]
  (let [int-states (for [mq messages
                         :when (not= (get-in mq [:action :action-type]) :reset)
                         :let [{{:keys [action-type]
                                 {:keys [concept-id revision-id id]} :data} :action} mq
                               result-state (:state (messages+id->message (:messages mq) id))]]
                     {[concept-id revision-id] [{:action action-type :result result-state}]})]
    (apply merge-with concat int-states)))

