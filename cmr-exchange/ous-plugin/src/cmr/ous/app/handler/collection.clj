(ns cmr.ous.app.handler.collection
  "This namespace defines the REST API handlers for collection resources."
  (:require
   [cheshire.core :as json]
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [clojusc.twig :as logger]
   [cmr.authz.token :as token]
   [cmr.exchange.common.results.errors :as base-errors]
   [cmr.exchange.query.core :as base-query]
   [cmr.exchange.query.util :as util]
   [cmr.http.kit.request :as base-request]
   [cmr.ous.components.config :as config]
   [cmr.ous.core :as ous]
   [cmr.ous.results.errors :as errors]
   [cmr.ous.util.http.request :as request]
   [cmr.ous.util.http.response :as response]
   [cmr.ous.util.query :as query]
   [org.httpkit.server :as server]
   [org.httpkit.timer :as timer]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   OUS Handlers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- generate
  "Private function for creating OPeNDAP URLs when supplied with an HTTP
  GET."
  [component req user-token concept-id data]
  (log/debug "Generating URLs based on HTTP GET ...")
  (log/trace "Got request:" (logger/pprint (into {} req)))
  (log/debug "Got request-id: " (base-request/extract-request-id req))
  (let [api-version (request/accept-api-version component req)
        dap-version (:dap-version (util/normalize-params (:params req)))
        sa-header (base-request/get-header req "cmr-search-after")]
    (->> data
         (merge {:collection-id concept-id
                 :request-id (base-request/extract-request-id req)})
         (ous/get-opendap-urls component api-version user-token dap-version sa-header)
         (response/json req))))

(defn- generate-via-get
  "Private function for creating OPeNDAP URLs when supplied with an HTTP
  GET."
  [component req user-token concept-id]
  (log/debug "Generating URLs based on HTTP GET ...")
  (->> req
       :params
       (generate component req user-token concept-id)))

(defn- generate-via-post
  "Private function for creating OPeNDAP URLs when supplied with an HTTP
  POST."
  [component req user-token concept-id]
  (->> req
       :body
       (slurp)
       (#(json/parse-string % true))
       (generate component req user-token concept-id)))

(defn unsupported-method
  "XXX"
  [req]
  {:error base-errors/not-implemented})

(defn generate-urls
  "XXX"
  [component]
  (fn [req]
    (log/debug "Method-dispatching for URLs generation ...")
    (let [user-token (token/extract req)
          concept-id (get-in req [:path-params :concept-id])]
      (case (:request-method req)
        :get (generate-via-get component req user-token concept-id)
        :post (generate-via-post component req user-token concept-id)
        (unsupported-method req)))))

(defn batch-generate
  "XXX"
  [component]
  ;; XXX how much can we minimize round-tripping here?
  ;;     this may require creating divergent logic/impls ...
  ;; XXX This is being tracked in CMR-4864
  (fn [req]
    {:error base-errors/not-implemented}))

(defn stream-urls
  ""
  [component]
  (fn [req]
    (let [heartbeat (config/streaming-heartbeat component)
          timeout (config/streaming-timeout component)
          iterations (Math/floor (/ timeout heartbeat))]
      (log/debug "Processing stream request ...")
      (server/with-channel req channel
        (log/debug "Setting 'on-close' callback ...")
        (server/on-close channel
                         (fn [status]
                           (println "Channel closed; status " status)))
        (let [result-channel (async/thread
                               ((generate-urls component) req))]
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
                (query/parse {:destination destination})
                base-query/->query-string))))

(defn bridge-services
  "Here's what we expect a request might look like:
  https://this-service/service-bridge/collection/c123?
    destination=giovanni&
    bounding-box=[...]&
    temporal=[...]&
    variables=V123,V234,V345&
    granules=G123"
  [component]
  (fn [req]
    (let [user-token (token/extract req)
          ;; TODO: Destination will not be found in path-params.
          ;; We need to retrieve this from the raw-params.
          {:keys [concept-id destination]} (:path-params req)
          api-version (request/accept-api-version component req)
          data (-> req
                   :params
                   (merge {:collection-id concept-id}))]
      (log/warnf "Handling bridge request from %s ..." (:referer req))
      (log/warnf "Bridging service to %s ..." destination)
      (response/json {:service {:url (params->service-url component destination data)}}))))
