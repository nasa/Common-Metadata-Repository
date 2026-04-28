(ns cmr.elastic-utils.es-helper
  "Defines helper functions for invoking ES"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.string :as string]
   [cmr.common.services.errors :as errors]
   [cmr.elastic-utils.config :as es-config]
   [cmr.transmit.config :as t-config]))

(defn- parse-safely
  "Parses the json body from the response safely"
  [body]
  (when body
    (if (string? body)
      (json/decode body true)
      body)))

(defn- decode-response
  "Decodes the response body from the given response"
  [response]
  (-> response
      :body
      parse-safely))

(defn- join-names
  "Joins names together with a comma"
  [names]
  (if (sequential? names)
    (string/join "," names)
    names))

(defn- url-with-path
  "Returns the url with the given path"
  [conn & path-parts]
  (let [path (->> path-parts
                  (map join-names)
                  (filter identity)
                  (string/join "/"))]
    (str (:uri conn) "/" path)))

(defn search
  "Performs a search query across one or more indexes"
  [conn index _mapping-type opts]
  (let [qk [:search_type :scroll :routing :preference :ignore_unavailable]
        qp (merge {:track_total_hits true}
                  (select-keys opts qk))
        body (apply dissoc opts qk)
        url (url-with-path conn index "_search")]
    (let [response (http/post url
                              (merge (:http-opts conn)
                                     {:content-type :json
                                      :body (json/generate-string body)
                                      :query-params qp
                                      :accept :json
                                      :throw-exceptions false}))
          status (:status response)]
      (if (some #{status} [200 201])
        (decode-response response)
        (throw (ex-info (str "Search failed with status " status)
                        {:status status :body (:body response)}))))))

(defn count-query
  "Performs a count query over one or more indexes"
  [conn index _mapping-type query]
  (let [url (url-with-path conn index "_count")
        body (if (get query :query)
               query
               {:query query})]
    (let [response (http/post url
                              (merge (:http-opts conn)
                                     {:content-type :json
                                      :body (json/generate-string body)
                                      :accept :json
                                      :throw-exceptions false}))
          status (:status response)]
      (if (some #{status} [200 201])
        (decode-response response)
        (throw (ex-info (str "Count failed with status " status)
                        {:status status :body (:body response)}))))))

(defn scroll
  "Performs a scroll query, fetching the next page of results from a query given a scroll id"
  [conn scroll-id opts]
  (let [url (url-with-path conn "_search" "scroll")
        body (merge {:scroll_id scroll-id}
                    (select-keys opts [:scroll]))]
    (decode-response
     (http/post url
                (merge (:http-opts conn)
                       {:content-type :json
                        :body (json/generate-string body)
                        :accept :json})))))

(defn doc-get
  "Fetches and returns a document by id or `nil` if it does not exist."
  ([conn index mapping-type id]
   (doc-get conn index mapping-type id nil))
  ([conn index _mapping-type id opts]
   (let [url (url-with-path conn index "_doc" id)
         response (http/get url
                            (merge (:http-opts conn)
                                   {:query-params opts
                                    :accept :json
                                    :throw-exceptions false}))
         status (:status response)]
     (when-not (= 404 status)
       (decode-response response)))))

(defn put
  "Creates or updates a document in the search index, using the provided document id"
  ([conn index mapping-type id document]
   (put conn index mapping-type id document nil))
  ([conn index _mapping-type id document opts]
   (let [url (url-with-path conn index "_doc" id)]
     (decode-response
      (http/put url
                (merge (:http-opts conn)
                       {:content-type :json
                        :body (if (string? document) document (json/generate-string document))
                        :query-params opts
                        :accept :json}))))))

(defn delete
  "Deletes document from the index."
  ([conn index mapping-type id]
   (delete conn index mapping-type id nil))
  ([conn index _mapping-type id opts]
   (let [url (url-with-path conn index "_doc" id)]
     (decode-response
      (http/delete url
                   (merge (:http-opts conn)
                          {:content-type :json
                           :query-params opts
                           :accept :json
                           :throw-exceptions false}))))))

(defn delete-by-query
  "Performs a delete-by-query operation over one or more indexes and types.
  Multiple indexes and types can be specified by passing in a seq of strings,
  otherwise specifying a string suffices."
  [conn index _mapping-type query]
  (let [admin-token (es-config/elastic-admin-token)
        url (url-with-path conn index "_delete_by_query")
        response (http/post url
                            (merge (:http-opts conn)
                                   {:headers {"Authorization" admin-token
                                              "Confirm-delete-action" "true"
                                              :client-id t-config/cmr-client-id}
                                    :content-type :json
                                    :body (json/generate-string {:query query})
                                    :throw-exceptions false}))
        status (:status response)]
    (if (#{200 201} status)
      (decode-response response)
      (throw (ex-info (str "Delete by query failed with status " status)
                      {:status status :body (:body response)})))))

(defn delete-index
  "Deletes an index from the elastic store"
  [conn index]
  (let [url (url-with-path conn index)]
    (decode-response
     (http/delete url
                  (merge (:http-opts conn)
                         {:accept :json})))))

(defn bulk
  "Performs a bulk operation"
  ([conn operations] (bulk conn operations nil))
  ([conn operations params]
   (when (not-empty operations)
     (let [url (url-with-path conn "_bulk")]
       (decode-response
        (http/post url
                   (merge (:http-opts conn)
                          {:body (-> (map json/encode operations)
                                     (interleave (repeat "\n"))
                                     (string/join)
                                     (str "\n"))
                           :content-type "application/x-ndjson"
                           :query-params params
                           :accept :json})))))))

(defn clear-scroll
  "Performs a clear scroll call for the given scroll id"
  [conn scroll-id]
  (let [url (url-with-path conn "_search" "scroll")]
    (decode-response
     (http/delete url
                  (merge (:http-opts conn)
                         {:content-type :json
                          :body (json/generate-string {:scroll_id scroll-id})
                          :accept :json})))))

(defn migrate-index
  "Copies the contents of one index into another. Used for resharding."
  [conn source-index target-index]
  (let [body {"source" {:index source-index}
              "dest" {:index target-index
                      :version_type "external_gte"}}
        url (str (url-with-path conn "_reindex") "?wait_for_completion=false")]
    (decode-response
     (http/post url
                (merge (:http-opts conn)
                       {:body (json/encode body)
                        :content-type "application/json"
                        :accept :json})))))

(defn get-reindex-task-status
  "Get the reindex task status and if there were any failures if the task is considered COMPLETE.
  Returns a map that captures the complete status and if there were any failures."
  [conn index reindex-task-id]
  (try
    (let [url (url-with-path conn "_tasks" reindex-task-id)
          resp (decode-response
                (http/get url
                          (merge (:http-opts conn)
                                 {:accept :json})))
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
