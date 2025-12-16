(ns cmr.elastic-utils.es-helper
  "Defines helper functions for invoking ES"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.string :as string]
   [clojurewerkz.elastisch.rest :as rest]
   [clojurewerkz.elastisch.rest.document :as doc]
   [clojurewerkz.elastisch.rest.response :refer [not-found?]]
   [clojurewerkz.elastisch.rest.utils :refer [join-names]]
   [cmr.common.log :refer [info]]
   [cmr.common.services.errors :as errors]
   [cmr.elastic-utils.config :as es-config]
   [cmr.transmit.config :as t-config]))

(defn search
  "Performs a search query across one or more indexes and one or more mapping types"
  [conn index _mapping-type opts]
  ;; Temporarily putting in this log to check which indexes are going to which cluster during ES cluster split
  (when (es-config/split-cluster-log-toggle)
    (info "CMR-10600 ES search for index: " index " connected to " conn))
  (let [qk [:search_type :scroll :routing :preference :ignore_unavailable]
        qp (merge {:track_total_hits true}
                  (select-keys opts qk))
        body (apply dissoc (concat [opts] qk))]
    (rest/post conn (rest/search-url conn (join-names index))
               {:content-type :json
                :body body
                :query-params qp})))

(defn count-query
  "Performs a count query over one or more indexes and types"
  [conn index mapping-type query]
  (doc/count conn index mapping-type query))

(defn scroll
  "Performs a scroll query, fetching the next page of results from a query given a scroll id"
  [conn scroll-id opts]
  (doc/scroll conn scroll-id opts))

(defn doc-get
  "Fetches and returns a document by id or `nil` if it does not exist."
  ([conn index mapping-type id]
   (doc-get conn index mapping-type id nil))
  ([conn index _mapping-type id opts]
   (let [result (if (empty? opts)
                  (rest/get conn (rest/record-url conn index "_doc" id))
                  (rest/get conn (rest/record-url conn index "_doc" id) {:query-params opts}))]
     (when-not (not-found? result)
       result))))

(defn put
  "Creates or updates a document in the search index, using the provided document id"
  ([conn index mapping-type id document]
   (put conn index mapping-type id document nil))
  ([conn index _mapping-type id document opts]
   (rest/put conn (rest/record-url conn index "_doc" id)
             {:content-type :json
              :body document
              :query-params opts})))

(defn delete
  "Deletes document from the index."
  ([conn index mapping-type id]
   (delete conn index mapping-type id nil))
  ([conn index _mapping-type id opts]
   (-> (rest/record-url conn index "_doc" id)
       (http/delete (merge {:throw-exceptions false}
                           (.http-opts conn)
                           {:content-type :json :query-params opts}
                           {:accept :json}))
       (:body)
       (rest/parse-safely))))

(defn delete-by-query
  "Performs a delete-by-query operation over one or more indexes and types.
  Multiple indexes and types can be specified by passing in a seq of strings,
  otherwise specifying a string suffices."
  ([conn index mapping-type query]
   (delete-by-query conn index mapping-type query nil))
  ([conn index _mapping-type query http-opts]
   (let [admin-token (es-config/elastic-admin-token)
         delete-url (rest/delete-by-query-url
                     conn
                     (join-names index))]
     (http/post delete-url
                (merge http-opts
                       {:headers {"Authorization" admin-token
                                  "Confirm-delete-action" "true"
                                  :client-id t-config/cmr-client-id}
                        :content-type :json
                        :body (json/generate-string {:query query})})))))

(defn delete-index
  "Deletes an index from the elastic store"
  [conn index]
  (rest/delete conn
               (rest/url-with-path conn index)))

(defn ^:private bulk-with-url
  "Construct the bulk URL and form body to specification provided by:
  https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html
  and perform post."
  ([conn url operations]
   (bulk-with-url conn url operations nil))
  ([conn url operations opts]
   (let [bulk-json (map json/encode operations)
         bulk-json (-> bulk-json
                       (interleave (repeat "\n"))
                       (string/join))]
     (rest/post-string conn url
                       {:body bulk-json
                        :content-type "application/x-ndjson"
                        :query-params opts}))))

(defn bulk
  "Performs a bulk operation"
  ([conn operations] (bulk conn operations nil))
  ([conn operations params]
   (when (not-empty operations)
     (bulk-with-url conn (rest/bulk-url conn) operations params))))

(defn clear-scroll
  "Performs a clear scroll call for the given scroll id"
  [conn scroll-id]
  (rest/delete conn (rest/scroll-url conn) {:content-type :json
                                            :body {:scroll_id scroll-id}}))

(defn migrate-index
  "Copies the contents of one index into another. Used for resharding."
  [conn source-index target-index]
  (let [body {"source" {:index source-index}
              "dest" {:index target-index}}
        url (str (rest/url-with-path conn "_reindex") "?wait_for_completion=false")]
    (rest/post-string conn url
                      {:body (json/encode body)
                       :content-type "application/json"})))

(defn- extract-descriptions-from-reindex-resp
  "Pulls out description value from the elastic reindex response."
  [reindex-resp-json]
  (let [nodes-map (:nodes reindex-resp-json)]
    (->> nodes-map
         (vals)
         (map :tasks)
         (mapcat vals)
         (keep :description)
         (map clojure.string/lower-case))))

(defn reindexing-still-in-progress?
  "Returns boolean of whether elastic is still reindexing the given index."
  [conn index]
  (try
    (let [url (str (rest/url-with-path conn "_tasks") "?actions=*reindex*&detailed=true")
          resp (rest/get conn url)
          current-reindexing-descriptions (extract-descriptions-from-reindex-resp resp)]
      ;; if the resp's descriptions still has the index in it, then it is still re-indexing
      (boolean (some #(string/includes? (string/lower-case %) index) current-reindexing-descriptions)))
    (catch Exception e
      (errors/throw-service-error
        :internal-error
        (str "Something went wrong when calling elastic to get reindexing status for index " index ". With exception details: " e)))))
