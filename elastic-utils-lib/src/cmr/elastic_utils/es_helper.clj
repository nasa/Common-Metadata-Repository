(ns cmr.elastic-utils.es-helper
  "Defines helper functions for invoking ES"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.string :as string]
   [cmr.common.log :as log :refer [debug info infof report warn]]
   [cmr.common.services.errors :as errors]
   [cmr.elastic-utils.config :as es-config]
   [cmr.elastic-utils.es-util :as es-util]
   [cmr.transmit.config :as t-config]))

(defn search
  "Performs a search query across one or more indexes"
  [conn index _mapping-type opts]
  (let [qk [:search_type :scroll :routing :preference :ignore_unavailable]
        qp (merge {:track_total_hits true}
                  (select-keys opts qk))
        body (apply dissoc opts qk)
        url (es-util/url-with-path conn index "_search")]
    (let [response (http/post url
                              (merge (:http-opts conn)
                                     {:content-type :json
                                      :body (json/generate-string body)
                                      :query-params qp
                                      :accept :json
                                      :throw-exceptions false}))
          status (:status response)]
      (if (some #{status} [200 201])
        (es-util/decode-response response)
        (throw (ex-info (str "Search failed with status " status)
                        {:status status :body (:body response)}))))))

(defn count-query
  "Performs a count query over one or more indexes"
  [conn index _mapping-type query]
  (let [url (es-util/url-with-path conn index "_count")
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
        (es-util/decode-response response)
        (throw (ex-info (str "Count failed with status " status)
                        {:status status :body (:body response)}))))))

(defn scroll
  "Performs a scroll query, fetching the next page of results from a query given a scroll id"
  [conn scroll-id opts]
  (let [url (es-util/url-with-path conn "_search" "scroll")
        body (merge {:scroll_id scroll-id}
                    (select-keys opts [:scroll]))
        response (http/post url
                            (merge (:http-opts conn)
                                   {:content-type :json
                                    :body (json/generate-string body)
                                    :accept :json
                                    :throw-exceptions false}))
        status (:status response)]
    (if (some #{status} [200 201])
      (es-util/decode-response response)
      (throw (ex-info (str "Scroll failed with status " status)
                      {:status status :body (:body response)})))))

(defn doc-get
  "Fetches and returns a document by id or `nil` if it does not exist."
  ([conn index mapping-type id]
   (doc-get conn index mapping-type id nil))
  ([conn index _mapping-type id opts]
   (let [url (es-util/url-with-path conn index "_doc" id)
         response (http/get url
                            (merge (:http-opts conn)
                                   {:query-params opts
                                    :accept :json
                                    :throw-exceptions false}))
         status (:status response)]
     (cond
       (= 404 status) nil
       (< status 300) (es-util/decode-response response)
       :else (throw (ex-info (str "Getting elastoc document failed with status " status)
                             {:status status :body (:body response)}))))))

(defn put
  "Creates or updates a document in the search index, using the provided document id"
  ([conn index mapping-type id document]
   (put conn index mapping-type id document nil))
  ([conn index _mapping-type id document opts]
   (let [url (es-util/url-with-path conn index "_doc" id)]
     (es-util/decode-response
      (http/put url
                (merge (:http-opts conn)
                       {:content-type :json
                        :body (if (string? document) document (json/generate-string document))
                        :query-params opts
                        :accept :json
                        :throw-exceptions false}))))))

(defn delete
  "Deletes document from the index."
  ([conn index mapping-type id]
   (delete conn index mapping-type id nil))
  ([conn index _mapping-type id opts]
   (let [url (es-util/url-with-path conn index "_doc" id)]
     (es-util/decode-response
      (http/delete url
                   (merge (:http-opts conn)
                          {:content-type :json
                           :query-params opts
                           :accept :json
                           :throw-exceptions false}))))))

(defn- has-scroll-context-error?
  "Checks if the response contains a 'too many scroll contexts' error."
  [response-body]
  (try
    (let [parsed (json/parse-string response-body true)
          error-map (:error parsed)]
      (and (some? error-map)
           (string/includes? (str error-map) "too many scroll contexts")))
    (catch Exception e
      false)))

;(defn delete-by-query
;  "Performs a delete-by-query operation over one or more indexes and types.
;  Multiple indexes and types can be specified by passing in a seq of strings,
;  otherwise specifying a string suffices."
;  [conn index _mapping-type query]
;  (let [admin-token (es-config/elastic-admin-token)
;        url (es-util/url-with-path conn index "_delete_by_query")
;        response (http/post url
;                            (merge (:http-opts conn)
;                                   {:headers {"Authorization" admin-token
;                                              "Confirm-delete-action" "true"
;                                              :client-id t-config/cmr-client-id}
;                                    :content-type :json
;                                    :body (json/generate-string {:query query
;                                                                 :slices 1
;                                                                 :scroll_size 500})
;                                    :throw-exceptions false}))
;        _ (info "response to delete-by-query for index " index " is " response)
;        _ (info "error message is " (:body response))
;        status (:status response)]
;    (if (#{200 201} status)
;      (es-util/decode-response response)
;      (throw (ex-info (str "Delete by query failed with status " status)
;                      {:status status :body (:body response)})))))

;(defn delete-by-query
;  "Performs a delete-by-query operation over one or more indexes and types.
;  Multiple indexes and types can be specified by passing in a seq of strings,
;  otherwise specifying a string suffices."
;  [conn index _mapping-type query]
;  (loop [attempt 1]
;    (let [result (try
;                   [:result (let [admin-token (es-config/elastic-admin-token)
;                                  url (es-util/url-with-path conn index "_delete_by_query")
;                                  response (http/post url
;                                                      (merge (:http-opts conn)
;                                                             {:headers {"Authorization" admin-token
;                                                                        "Confirm-delete-action" "true"
;                                                                        :client-id t-config/cmr-client-id}
;                                                              :content-type :json
;                                                              :query-params {:slices 1
;                                                                             :scroll_size 500
;                                                                             :conflicts "proceed"}
;                                                              :body (json/generate-string {:query query})
;                                                              :throw-exceptions false}))
;                                  resp-body (:body response)
;                                  status (:status response)]
;
;                              (when (has-scroll-context-error? resp-body)
;                                (throw (ex-info "Scroll context error detected"
;                                                {:type :scroll-context-error
;                                                 :body resp-body})))
;
;                              (if (#{200 201} status)
;                                (es-util/decode-response response)
;                                (throw (ex-info (str "Delete by query failed with status " status)
;                                                {:status status :body (:body response)}))))]
;                   (catch Exception e
;                     ;; The :error vector indicates failure.
;                     [:error e]))]
;
;      ;; Make a decision based on the result.
;      (if (= :result (first result))
;        ;; Success: return the actual result.
;        (second result)
;
;        ;; Failure: check if we should retry.
;        (let [e (second result)]
;          (if (and (< attempt 3)
;                   (= :scroll-context-error (:type (ex-data e))))
;            (do
;              (info (format "Scroll context error on attempt %d. Retrying..." attempt))
;              (Thread/sleep 100)
;              (recur (inc attempt)))
;
;            ;; Not retryable: throw the original exception.
;            (throw e)))))))

;(defn delete-by-query
;  "Performs a delete-by-query operation, blocking until completion.
;  Internally, it uses an async task and polling to prevent network timeouts
;  on long-running deletions, while maintaining a synchronous API contract."
;  [conn index _mapping-type query]
;  (let [polling-interval-ms 5000 ;; Poll every 5 seconds
;        max-wait-ms (* 10 60 1000) ;; Max wait time: 10 minutes
;        admin-token (es-config/elastic-admin-token)
;        start-time (System/currentTimeMillis)]
;
;    ;; --- Step 1: Start the deletion as a background task ---
;    (let [start-task-url (es-util/url-with-path conn index "_delete_by_query")
;          start-task-response (http/post start-task-url
;                                         (merge (:http-opts conn)
;                                                {:headers {"Authorization" admin-token}
;                                                 :content-type :json
;                                                 :query-params {:wait_for_completion false
;                                                                :slices 1
;                                                                :scroll_size 500
;                                                                :conflicts "proceed"}
;                                                 :body (json/generate-string {:query query})
;                                                 :throw-exceptions false}))
;          start-task-status (:status start-task-response)]
;
;      (if-not (#{200 201} start-task-status)
;        ;; If we can't even START the task, fail immediately.
;        (throw (ex-info "Failed to start delete-by-query task"
;                        {:status start-task-status :body (:body start-task-response)}))
;
;        ;; --- Step 2: Poll the Task API until the task is complete ---
;        (let [task-id (-> start-task-response es-util/decode-response :task)]
;          (info (str "Started delete-by-query task " task-id ". Polling for completion..."))
;          (loop []
;            (let [check-task-url (es-util/url-with-path conn (str "_tasks/" task-id))
;                  task-status-response (http/get check-task-url
;                                                 (merge (:http-opts conn)
;                                                        {:headers {"Authorization" admin-token}
;                                                         :throw-exceptions false}))
;                  task-status-body (es-util/decode-response task-status-response)]
;
;              (cond
;                ;; Condition 1: Task is successfully completed
;                (true? (:completed task-status-body))
;                (do
;                  (info (str "Task " task-id " completed successfully."))
;                  ;; The final result is in the :response field of the task status
;                  (:response (:task task-status-body)))
;
;                ;; Condition 2: The entire process has timed out
;                (> (- (System/currentTimeMillis) start-time) max-wait-ms)
;                (throw (ex-info (str "Timed out waiting for delete-by-query task " task-id " to complete after " (/ max-wait-ms 1000) " seconds.")
;                                {:task-id task-id}))
;
;                ;; Condition 3: The task itself reported an error
;                (some? (get-in task-status-body [:task :error]))
;                (throw (ex-info (str "Delete-by-query task " task-id " failed with an error.")
;                                {:task-id task-id
;                                 :error-details (get-in task-status-body [:task :error])}))
;
;                ;; Condition 4: Still running, continue polling
;                :else
;                (do
;                  (let [status (get-in task-status-body [:task :status])]
;                    (info (format "Task %s progress: %d deleted / %d total."
;                                  task-id
;                                  (:deleted status)
;                                  (:total status))))
;                  (Thread/sleep polling-interval-ms)
;                  (recur))))))))))

(defn- attempt-to-start-task
  "Makes a single attempt to start the delete-by-query task.
  Throws an exception on any failure, returns a task-id on success."
  [conn index query]
  (let [start-task-url (es-util/url-with-path conn index "_delete_by_query")
        response (http/post start-task-url
                            (merge (:http-opts conn)
                                   {:headers {"Authorization" (es-config/elastic-admin-token)}
                                    :content-type :json
                                    :query-params {:wait_for_completion false
                                                   :slices 1
                                                   :scroll_size 500
                                                   :conflicts "proceed"}
                                    :body (json/generate-string {:query query})
                                    :throw-exceptions false}))
        _ (info "CMR-11405 - Response for starting delete query task is " response)
        status (:status response)
        body (:body response)]
    (when (has-scroll-context-error? body)
      (throw (ex-info "CMR-11405 - Scroll context error on task start" {:type :scroll-context-error :body body})))

    (when-not (#{200 201} status)
      (throw (ex-info "CMR-11405 - Failed to start delete-by-query task" {:status status :body body})))

    (-> response es-util/decode-response :task)))

(defn- start-task-with-retry
  "Wraps the task start attempt with retry logic for scroll-context errors."
  [conn index query]
  (loop [attempt 1]
    ;; Step 1: Run the action and capture the result as either [:ok val] or [:error ex].
    (let [result (try
                   [:ok (attempt-to-start-task conn index query)]
                   (catch Exception e
                     [:error e]))]

      ;; Step 2: Check the result and decide what to do.
      (if (= :ok (first result))
        ;; Success: return the actual value.
        (second result)

        ;; Failure: check if we should retry.
        (let [e (second result)]
          (if (and (< attempt 3) (= :scroll-context-error (:type (ex-data e))))
            ;; Retryable error: This `recur` is now in a simple `if` branch,
            ;; completely outside the `try/catch`, and is guaranteed to be in a tail position.
            (do
              (info (format "CMR-11405 - Scroll context error on attempt %d to start task. Retrying..." attempt))
              (Thread/sleep 100)
              (recur (inc attempt)))
            ;; Not retryable: re-throw the original exception.
            (throw e)))))))

(defn- poll-task-for-completion
  "Polls a given task-id until it completes, fails, or times out."
  [conn task-id]
  (let [polling-interval-ms 5000
        max-wait-ms (* 10 60 1000)
        start-time (System/currentTimeMillis)]
    (info (str "CMR-11405 - Polling task " task-id " for completion..."))
    (loop []
      (let [check-task-url (es-util/url-with-path conn (str "_tasks/" task-id))
            task-status-response (http/get check-task-url
                                           (merge (:http-opts conn)
                                                  {:headers {"Authorization" (es-config/elastic-admin-token)}
                                                   :throw-exceptions false}))
            task-status-body (es-util/decode-response task-status-response)]
        (cond
          (true? (:completed task-status-body))
          (do (info (str "CMR-11405 - Task " task-id " completed successfully."))
              (:response (:task task-status-body)))

          (> (- (System/currentTimeMillis) start-time) max-wait-ms)
          (throw (ex-info (str "CMR-11405 - Timed out waiting for task " task-id) {:task-id task-id}))

          (some? (get-in task-status-body [:task :error]))
          (throw (ex-info (str "CMR-11405 - Task " task-id " failed with an error.")
                          {:task-id task-id :error-details (get-in task-status-body [:task :error])}))

          :else
          (do
            (let [status (get-in task-status-body [:task :status])]
              (info (format "CMR-11405 - Task %s progress: %d deleted / %d total." task-id (:deleted status) (:total status))))
            (Thread/sleep polling-interval-ms)
            (recur)))))))

(defn delete-by-query
  "Performs a delete-by-query operation, blocking until completion."
  [conn index _mapping-type query]
  (info "CMR-11405 - delete-by-query started for index : " index)
  (let [task-id (start-task-with-retry conn index query)]
    (poll-task-for-completion conn task-id)))

(defn delete-index
  "Deletes an index from the elastic store"
  [conn index]
  (let [url (es-util/url-with-path conn index)]
    (es-util/decode-response
     (http/delete url
                  (merge (:http-opts conn)
                         {:accept :json})))))

(defn bulk
  "Performs a bulk operation"
  ([conn operations] (bulk conn operations nil))
  ([conn operations params]
   (when (seq operations)
     (let [url (es-util/url-with-path conn "_bulk")]
       ;; Elasticsearch _bulk API uses a format called NDJSON (Newline Delimited JSON)
       ;; https://www.elastic.co/docs/api/doc/elasticsearch/operation/operation-bulk
       (es-util/decode-response
        (http/post url
                   (merge (:http-opts conn)
                          {:body (-> (map json/encode operations) ; convert each operation map to JSON str
                                     (interleave (repeat "\n")) ; put newline between every JSON str
                                     (string/join) ; Combine all JSON strs into one large str
                                     (str "\n")) ; Append the mandatory final newline
                           :content-type "application/x-ndjson"
                           :query-params params
                           :accept :json
                           :throw-exceptions false})))))))

(defn clear-scroll
  "Performs a clear scroll call for the given scroll id"
  [conn scroll-id]
  (let [url (es-util/url-with-path conn "_search" "scroll")]
    (es-util/decode-response
     (http/delete url
                  (merge (:http-opts conn)
                         {:content-type :json
                          :body (json/generate-string {:scroll_id scroll-id})
                          :accept :json
                          :throw-exceptions false})))))

(defn migrate-index
  "Copies the contents of one index into another. Used for resharding."
  [conn source-index target-index]
  (let [body {"source" {:index source-index}
              "dest" {:index target-index
                      :version_type "external_gte"}
              "conflicts" "proceed"}
        url (str (es-util/url-with-path conn "_reindex") "?wait_for_completion=false")]
    (es-util/decode-response
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
    (let [url (es-util/url-with-path conn "_tasks" reindex-task-id)
          resp (es-util/decode-response
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
