(ns cmr.transmit.index-set
  "Provide functions to invoke index set app"
  (:require [clj-http.client :as client]
            [cmr.common.services.errors :as errors]
            [cmr.transmit.http-helper :as h]
            [cheshire.core :as cheshire]
            [cmr.transmit.config :as config]
            [cmr.transmit.connection :as conn]))

(defn- rebalance-collection-url
  [conn index-set-id concept-id]
  (format "%s/index-sets/%s/rebalancing-collections/%s" (conn/root-url conn) index-set-id concept-id))

(defn- start-rebalance-collection-url
  [conn index-set-id concept-id]
  (str (rebalance-collection-url conn index-set-id concept-id) "/start"))

(defn- finalize-rebalance-collection-url
  [conn index-set-id concept-id]
  (str (rebalance-collection-url conn index-set-id concept-id) "/finalize"))

(defn get-index-set
  "Submit a request to index-set app to fetch an index-set assoc with an id"
  [context id]
  (let [conn (config/context->app-connection context :index-set)
        response (client/request
                   (merge
                     (config/conn-params conn)
                     {:method :get
                      :url (format "%s/index-sets/%s" (conn/root-url conn) (str id))
                      :accept :json
                      :throw-exceptions false
                      :headers {config/token-header (config/echo-system-token)}}))
        status (:status response)
        body (cheshire/decode (:body response) true)]
    (case status
      404 nil
      200 body
      (errors/internal-error! (format "Unexpected error fetching index-set with id: %s,
                                      Index set app reported status: %s, error: %s"
                                      id status (pr-str (flatten (:errors body))))))))

;; Defines health check function
(h/defhealther get-index-set-health :index-set 2)

(defn- submit-rebalancing-collection-request
  "A helper function for submitting a request to modify the list of rebalancing collections."
  [context index-set-id concept-id url-fn]
  (h/request context :index-set
             {:url-fn #(url-fn % index-set-id concept-id)
              :method :post
              :http-options {:headers {config/token-header (config/echo-system-token)}}
              :response-handler (fn [_request {:keys [status body]}]
                                  (cond
                                    (= status 200) nil
                                    (= status 400) (errors/throw-service-errors :bad-request (:errors body))
                                    :else (errors/internal-error!
                                           (str "Unexpected status code:"
                                                status " response:" (pr-str body)))))}))

(defn add-rebalancing-collection
  "Adds the specified collection to the set of rebalancing collections in the index set."
  [context index-set-id concept-id]
  (submit-rebalancing-collection-request context index-set-id concept-id
                                         start-rebalance-collection-url))

(defn finalize-rebalancing-collection
  "Finalizes the rebalancing collection specified in the index set application."
  [context index-set-id concept-id]
  (submit-rebalancing-collection-request context index-set-id concept-id
                                         finalize-rebalance-collection-url))