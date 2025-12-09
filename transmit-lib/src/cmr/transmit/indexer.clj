(ns cmr.transmit.indexer
  "Provides functions for accessing the indexer application"
  (:require
   [cheshire.core :as cheshire]
   [clj-http.client :as client]
   [cmr.common.services.errors :as errors]
   [cmr.transmit.http-helper :as h]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]))

;; Defines health check function
(declare get-indexer-health)
(h/defhealther get-indexer-health :indexer {:timeout-secs 2})

(declare clear-cache)
(h/defcacheclearer clear-cache :indexer)

(defn- rebalance-collection-url
  [conn index-set-id concept-id]
  (format "%s/index-sets/%s/rebalancing-collections/%s" (conn/root-url conn) index-set-id concept-id))

(defn- start-rebalance-collection-url
  [conn index-set-id concept-id]
  (str (rebalance-collection-url conn index-set-id concept-id) "/start"))

(defn- finalize-rebalance-collection-url
  [conn index-set-id concept-id]
  (str (rebalance-collection-url conn index-set-id concept-id) "/finalize"))

(defn- reshard-index-url
  [conn index-set-id index]
  (format "%s/index-sets/%s/reshard/%s" (conn/root-url conn) index-set-id index))

(defn- start-reshard-index-url
  [conn index-set-id index]
  (str (reshard-index-url conn index-set-id index) "/start"))

(defn- get-reshard-status-url
  [conn index-set-id index]
  (str (reshard-index-url conn index-set-id index) "/status"))

(defn- finalize-reshard-index-url
  [conn index-set-id index]
  (str (reshard-index-url conn index-set-id index) "/finalize"))

(defn get-index-set
  "Submit a request to indexer to fetch an index-set assoc with an id"
  [context id]
  (let [conn (config/context->app-connection context :indexer)
        params (merge
                (config/conn-params conn)
                {:method :get
                 :url (format "%s/index-sets/%s" (conn/root-url conn) (str id))
                 :accept :json
                 :throw-exceptions false
                 :headers {:client-id config/cmr-client-id
                           config/token-header (config/echo-system-token)}})
        response (client/request params)
        status (:status response)
        body (cheshire/decode (:body response) true)]
    (case (int status)
      404 nil
      200 body
      (errors/internal-error! (format "Unexpected error fetching index-set with id: %s,
                                      Index set app reported status: %s, error: %s"
                                      id status (pr-str (flatten (:errors body))))))))

(defn- submit-rebalancing-collection-request
  "A helper function for submitting a request to modify the list of rebalancing collections."
  [context index-set-id concept-id url-fn target]
  (let [query-params (when target {:target (name target)})]
    (h/request context :indexer
               {:url-fn #(url-fn % index-set-id concept-id)
                :method :post
                :http-options {:headers {config/token-header (config/echo-system-token)}
                               :content-type :json
                               :query-params query-params}
                :response-handler (fn [_request {:keys [status body]}]
                                    (cond
                                      (= status 200) nil
                                      (= status 400) (errors/throw-service-errors :bad-request (:errors body))
                                      :else (errors/internal-error!
                                             (str "Unexpected status code:"
                                                  status " response:" (pr-str body)))))})))

(defn add-rebalancing-collection
  "Adds the specified collection to the set of rebalancing collections in the index set."
  [context index-set-id concept-id target]
  (submit-rebalancing-collection-request context index-set-id concept-id
                                         start-rebalance-collection-url target))

(defn finalize-rebalancing-collection
  "Finalizes the rebalancing collection specified in the indexer application."
  [context index-set-id concept-id]
  (submit-rebalancing-collection-request context index-set-id concept-id
                                         finalize-rebalance-collection-url nil))

(defn- submit-reshard-index-request
  "A helper function for submitting a request to reshard an index."
  [context index-set-id index url-fn num-shards elastic-name]
  (let [query-params (cond-> {}
                           num-shards (assoc :num_shards num-shards)
                           :always-true (assoc :elastic_name elastic-name))]
    (h/request context :indexer
               {:url-fn #(url-fn % index-set-id index)
                :method :post
                :http-options {:headers {config/token-header (config/echo-system-token)}
                               :content-type :json
                               :query-params query-params}
                :response-handler (fn [_request {:keys [status body]}]
                                    (cond
                                      (= status 200) nil
                                      (= status 400) (errors/throw-service-errors :bad-request (:errors body))
                                      (= status 404) (errors/throw-service-errors :not-found (:errors body))
                                      :else (errors/internal-error!
                                             (str "Unexpected status code:"
                                                  status " response:" (pr-str body)))))})))

(defn add-resharding-index
  "Adds the specified index to the set of resharding indexes."
  [context index-set-id index num-shards elastic-name]
  (submit-reshard-index-request context index-set-id index start-reshard-index-url num-shards elastic-name))

(defn get-reshard-status
  "Get the resharding status of the given index."
  [context index-set-id index elastic-name]
  (let [conn (config/context->app-connection context :indexer)
         params (merge
                 (config/conn-params conn)
                 {:method :get
                  :query-params {:elastic_name elastic-name}
                  :url (get-reshard-status-url conn index-set-id index)
                  :accept :json
                  :throw-exceptions false
                  :headers {:client-id config/cmr-client-id
                            config/token-header (config/echo-system-token)}})
         response (client/request params)
         status (:status response)
         body (cheshire/decode (:body response) true)]

    {:status status :body body}

     ;(case (int status)
     ;  404 {:status status :body body}
     ;  200 {:status status :body body}
     ;  (errors/internal-error! (format "Unexpected error fetching resharding status for index: %s,
     ;                                   Index set app reported status: %s, error: %s"
     ;                                  index status (pr-str (flatten (:errors body))))))

    ))

(defn finalize-resharding-index
  "Finalizes the resharding index specified in the indexer application."
  [context index-set-id index elastic-name]
  (submit-reshard-index-request context index-set-id index finalize-reshard-index-url nil elastic-name))
