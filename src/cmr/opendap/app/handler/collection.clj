(ns cmr.opendap.app.handler.collection
  "This namespace defines the REST API handlers for collection resources."
  (:require
   [cheshire.core :as json]
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [cmr.authz.token :as token]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
   [cmr.opendap.ous.core :as ous]
   [cmr.opendap.query.core :as query]
   [cmr.opendap.results.errors :as errors]
   [cmr.opendap.sizing.core :as sizing]
   [org.httpkit.server :as server]
   [org.httpkit.timer :as timer]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   OUS Handlers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- generate
  "Private function for creating OPeNDAP URLs when supplied with an HTTP
  GET."
  [component request user-token concept-id data]
  (log/debug "Generating URLs based on HTTP GET ...")
  (let [api-version (request/accept-api-version component request)]
    (->> data
         (merge {:collection-id concept-id})
         (ous/get-opendap-urls component api-version user-token)
         (response/json request))))

(defn- generate-via-get
  "Private function for creating OPeNDAP URLs when supplied with an HTTP
  GET."
  [component request user-token concept-id]
  (log/debug "Generating URLs based on HTTP GET ...")
  (->> request
       :params
       (generate component request user-token concept-id)))

(defn- generate-via-post
  "Private function for creating OPeNDAP URLs when supplied with an HTTP
  POST."
  [component request user-token concept-id]
  (->> request
       :body
       (slurp)
       (#(json/parse-string % true))
       (generate component request user-token concept-id)))

(defn unsupported-method
  "XXX"
  [request]
  {:error errors/not-implemented})

(defn generate-urls
  "XXX"
  [component]
  (fn [request]
    (log/debug "Method-dispatching for URLs generation ...")
    (log/trace "request:" request)
    (let [user-token (token/extract request)
          concept-id (get-in request [:path-params :concept-id])]
      (case (:request-method request)
        :get (generate-via-get component request user-token concept-id)
        :post (generate-via-post component request user-token concept-id)
        (unsupported-method request)))))

(defn batch-generate
  "XXX"
  [component]
  ;; XXX how much can we minimize round-tripping here?
  ;;     this may require creating divergent logic/impls ...
  ;; XXX This is being tracked in CMR-4864
  (fn [request]
    {:error errors/not-implemented}))

(defn stream-urls
  ""
  [component]
  (fn [request]
    (let [heartbeat (config/streaming-heartbeat component)
          timeout (config/streaming-timeout component)
          iterations (Math/floor (/ timeout heartbeat))]
      (log/debug "Processing stream request ...")
      (server/with-channel request channel
        (log/debug "Setting 'on-close' callback ...")
        (server/on-close channel
                         (fn [status]
                          (println "Channel closed; status " status)))
        (let [result-channel (async/thread
                                ((generate-urls component) request))]
          (log/trace "Starting loop ...")
          (async/go-loop [id 0]
            (log/trace "Loop id:" id)
            (if-let [result (async/<! result-channel)]
              (do
                (log/trace "Result:" result)
                (server/send! channel result)
                (server/close channel)
              (when (< id iterations)
                (timer/schedule-task
                 (* id heartbeat) ; send a message every heartbeat period
                 (log/trace "\tSending 0-byte placeholder chunk to client ...")
                 (server/send! channel
                               {:status 202}
                               false))
                (recur (inc id))))))
          (timer/schedule-task timeout (server/close channel)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Size Estimate Handlers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn estimate-size
  [component]
  (fn [request]
    (log/debug "Estimating download size based on HTTP GET ...")
    (let [user-token (token/extract request)
          concept-id (get-in request [:path-params :concept-id])
          api-version (request/accept-api-version component request)]
      (->> request
           :params
           (merge {:collection-id concept-id})
           (sizing/estimate-size component api-version user-token)
           (response/json request)))))

(defn stream-estimate-size
  [component]
  (fn [request]
    {:errors [:not-implemented]}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Service Bridge Handlers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-service-endpoint
  [component destination]
  (case destination
    :cmr (config/get-cmr-search-endpoint component)
    :giovanni (config/get-giovanni-endpoint component)
    :edsc (config/get-edsc-endpoint component)))

(defn params->service-url
  [component destination data]
  (let [service-endpoint (get-service-endpoint component destination)]
    (format "%s?%s" service-endpoint
                    (-> data
                        (query/parse destination)
                        query/->query-string))))

(defn bridge-services
  "Here's what we expect a request might look like:
  https://this-service/service-bridge/collection/c123?
    destination=giovanni&
    bounding-box=[...]&
    temporal=[...]&
    variables=V123,V234,V345&
    granules=G123"
  [component]
  (fn [request]
    (let [user-token (token/extract request)
          ;; TODO: Destination will not be found in path-params.
          ;; We need to retrieve this from the raw-params.
          {:keys [concept-id destination]} (:path-params request)
          api-version (request/accept-api-version component request)
          data (-> request
                   :params
                   (merge {:collection-id concept-id}))]
      (log/warnf "Handling bridge request from %s ..." (:referer request))
      (log/warnf "Bridging service to %s ..." destination)
      (response/json {
                      :service {
                                :url (params->service-url component destination data)}}))))
