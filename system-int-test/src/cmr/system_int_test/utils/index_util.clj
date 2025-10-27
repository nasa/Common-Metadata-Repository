(ns cmr.system-int-test.utils.index-util
  "provides index related utilities."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.string :as string]
   [clojure.test :refer [is]]
   [cmr.common.log :as log :refer (warn)]
   [cmr.elastic-utils.config :as es-config]
   [cmr.indexer.indexer-util :as indexer-util]
   [cmr.message-queue.test.queue-broker-side-api :as qb-side-api]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.url-helper :as url]
   [cmr.transmit.config :as transmit-config]))

(defn refresh-all-elastic-indexes
  []
  (client/post (url/elastic-refresh-url es-config/gran-elastic-name) {:connection-manager (s/conn-mgr)})
  (client/post (url/elastic-refresh-url es-config/elastic-name) {:connection-manager (s/conn-mgr)}))

(defn wait-until-indexed
  "Wait until ingested concepts have been indexed"
  []
  (qb-side-api/wait-for-terminal-states)
  (refresh-all-elastic-indexes))

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

(defn delete-tags-from-elastic
  "Delete all tags from elasticsearch index"
  [tags]
  (doseq [tag tags]
    (let [{:keys [concept-id revision-id]} tag
          response (client/delete
                    (url/elastic-delete-tag-url concept-id)
                    {:connection-manager (s/conn-mgr)
                     :query-params {:version revision-id
                                    :version_type "external_gte"}
                     :throw-exceptions false})]
      (is (= 200 (:status response)) (:body response)))))

(defn reindex-tags
  "Re-index all tags"
  []
  (let [response (client/post
                  (url/indexer-reindex-tags-url)
                  {:connection-manager (s/conn-mgr)
                   :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                   :throw-exceptions false})]
    (is (= 200 (:status response)) (:body response))))

(defn reindex-suggestions
  "Re-index all suggestions"
  []
  (let [response (client/post (url/reindex-suggestions-url)
                              {:connection-manager (s/conn-mgr)
                               :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                               :throw-exceptions false})]
    (is (= 200 (:status response)) (:body response))))

(defn reindex-concept-with-ignore-conflict-param
  "Re-index concept with ignore_conflict param."
  ([concept-id revision-id]
   (reindex-concept-with-ignore-conflict-param concept-id revision-id nil))
  ([concept-id revision-id ignore_conflict]
   (let [query-params (if ignore_conflict
                        {:ignore_conflict ignore_conflict}
                        {})
         response (client/post (url/indexer-url)
                               {:connection-manager (s/conn-mgr)
                                :headers {transmit-config/token-header (transmit-config/echo-system-token)
                                          "content-type" "application/json"}
                                :throw-exceptions false
                                :body (json/generate-string {:concept-id concept-id :revision-id revision-id})
                                :query-params query-params})]
     response)))

(defn doc-present?
  "If doc is present return true, otherwise return false"
  [index-name type-name doc-id elastic-name]
  (let [response (client/get
                  (format "%s/%s/_doc/_search?q=_id:%s" (url/elastic-root elastic-name) index-name doc-id)
                  {:throw-exceptions false
                   :connection-manager (s/conn-mgr)})
        body (json/decode (:body response) true)]
    (and (= 1 (get-in body [:hits :total :value]))
         (= doc-id (get-in body [:hits :hits 0 :_id])))))

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

(defn delete-gran-elastic-index
  "Helper will delete the elasticsearch granule index associated with a collection."
  [coll]
  (let [index-name (string/replace (format "1_%s" (string/lower-case (:concept-id coll)))
                                   #"-" "_")]
    (warn "Deleting index " index-name)
    (client/delete (format "%s/%s" (url/elastic-root es-config/gran-elastic-name) index-name)
                   {:connection-manager (s/conn-mgr)})))

(defn- query-for-granules-by-collection
  "Elasticsearch query to return all of the granules in a given collection."
  [coll]
  (json/generate-string
   {:query
    {:bool
     {:must
      {:match_all {}}
      :filter {:term {:collection-concept-id-doc-values (:concept-id coll)}}}}}))

(defn delete-granules-from-small-collections-elastic-index
  "Helper to delete granules from the small collections index for the given collection."
  [coll]
  (client/post (format "%s/1_small_collections/_delete_by_query" (url/elastic-root es-config/gran-elastic-name))
                 {:connection-manager (s/conn-mgr)
                  :body (query-for-granules-by-collection coll)
                  :content-type "application/json"}))

(defn gran-elastic-index-exists?
  "Helper to check if elasticsearch granule index exists."
  [coll]
  (let [index-name (string/replace (format "1_%s" (string/lower-case (:concept-id coll)))
                                   #"-" "_")]
    (client/head (format "%s/%s" (url/elastic-root es-config/gran-elastic-name) index-name)
                 {:connection-manager (s/conn-mgr)
                  :throw-exceptions false})))

(defn get-aliases
  "Returns a vector of alias names for the given index."
  [index-name]
  (let [elastic-name (indexer-util/get-es-cluster-name-by-index-or-alias index-name)
        aliases-url (format "%s/_cat/aliases" (url/elastic-root elastic-name))
        resp (client/get aliases-url
                         {:query-params {:format "json"}
                          :connection-manager (s/conn-mgr)
                          :throw-exceptions false})]
    (if (= 200 (:status resp))
      (->> (json/decode (:body resp) true)
           (filter #(= (:index %) index-name))
           (mapv :alias))
      [])))

(defn alias-exists?
  "Returns true if the given alias exists for the specified index."
  [index-name alias]
  (contains? (set (get-aliases index-name)) alias))

