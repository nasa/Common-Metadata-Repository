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
  "Performs a search query across one or more indexes"
  [conn index _mapping-type opts]
  (let [qk [:search_type :scroll :routing :preference :ignore_unavailable]
        qp (merge {:track_total_hits true}
                  (select-keys opts qk))
        body (apply dissoc opts qk)
        url (format "%s/%s/_search" (:uri conn) (join-names index))]
    (let [response (http/post url
                              (merge (.http-opts conn)
                                     {:content-type :json
                                      :body (json/generate-string body)
                                      :query-params qp
                                      :accept :json
                                      :throw-exceptions false}))
          status (:status response)]
      (if (some #{status} [200 201])
        (rest/parse-safely (:body response))
        (throw (ex-info (str "Search failed with status " status)
                        {:status status :body (:body response)}))))))

(defn count-query
  "Performs a count query over one or more indexes"
  [conn index _mapping-type query]
  (let [url (format "%s/%s/_count" (:uri conn) (join-names index))
        body (if (get query :query)
               query
               {:query query})]
    (let [response (http/post url
                              (merge (.http-opts conn)
                                     {:content-type :json
                                      :body (json/generate-string body)
                                      :accept :json
                                      :throw-exceptions false}))
          status (:status response)]
      (if (some #{status} [200 201])
        (rest/parse-safely (:body response))
        (throw (ex-info (str "Count failed with status " status)
                        {:status status :body (:body response)}))))))

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
                     (join-names index))
         response (http/post delete-url
                             (merge http-opts
                                    {:headers {"Authorization" admin-token
                                               "Confirm-delete-action" "true"
                                               :client-id t-config/cmr-client-id}
                                     :content-type :json
                                     :body (json/generate-string {:query query})
                                     :throw-exceptions false}))
         status (:status response)]
     (if (some #{status} [200 201])
       (rest/parse-safely (:body response))
       (throw (ex-info (str "Delete by query failed with status " status)
                       {:status status :body (:body response)}))))))

(defn delete-index
  "Deletes an index from the elastic store"
  [conn index]
  (rest/delete conn
               (rest/url-with-path conn index)))

(defn bulk
  "Performs a bulk operation"
  ([conn operations] (bulk conn operations nil))
  ([conn operations params]
   (when (not-empty operations)
     (rest/post-string conn (rest/bulk-url conn)
                       {:body (-> (map json/encode operations)
                                  (interleave (repeat "\n"))
                                  (string/join))
                        :content-type "application/x-ndjson"
                        :query-params params}))))

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

(defn get-reindex-task-status
  "Get the reindex task status and if there were any failures if the task is considered COMPLETE.
  Returns a map that captures the complete status and if there were any failures.
  Example map return:
  {
    :complete true
    :failures [{
                :index: \"new_index\",
                :type: \"_doc\",
                :id\": \"doc_id\",
                :cause: {
                          :type: \"exception\",
                          :reason: \"failure reason\"
                          :caused_by: {
                                        :type: \"number_format_exception\",
                                        :reason: \"cause reason\"
                                      }
                         }
                :status: 400
                }]
    :error {:type \"type\"
            :reason \"reason\"
            :caused_by {}
            }
    }"
  [conn index reindex-task-id]
  (try
    (let [url (rest/url-with-path conn "_tasks" reindex-task-id)
          resp (rest/get conn url)
          completed (:completed resp)
          failures (get-in resp [:response :failures])
          task-error (:error resp)
          description (get-in resp [:task :description])
          index-found-in-description (and description
                                          (string/includes? (string/lower-case description) (string/lower-case index)))
          full-status {:completed completed
                       :failures failures
                       :error task-error}]

      ;; check if this is the right task id for this index
      (if-not index-found-in-description
        (errors/throw-service-error :internal-error (format "Given task id %s is not tracking the given index %s because description in task [%s] did not include the index. Mismatch on task id with index error." reindex-task-id index description)))
      full-status)
    (catch clojure.lang.ExceptionInfo e
      (throw e))
    (catch Exception e
      (errors/throw-service-error
        :internal-error
        (str "Something went wrong when calling elastic to get reindexing status for index " index " with task id " reindex-task-id ". With exception details: " e)))))
