(ns cmr.opendap.app.handler.collection
  "This namespace defines the REST API handlers for collection resources."
  (:require
   [cheshire.core :as json]
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [cmr.opendap.auth.token :as token]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.ous.core :as ous]
   [cmr.opendap.http.response :as response]
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
  (->> data
       (merge {:collection-id concept-id})
       (ous/get-opendap-urls component user-token)
       (response/json request)))

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
  ;; XXX add error message; utilize error reporting infra
  {:error :not-implemented})

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
  (fn [request]
    {:error :not-implemented}))

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
               (* id heartbeat) ;; send a message every heartbeat period
               (log/trace "\tSending 0-byte placeholder chunk to client ...")
               (server/send! channel
                             {:status 202}
                             false))
              (recur (inc id))))))
        (timer/schedule-task timeout (server/close channel)))))))
