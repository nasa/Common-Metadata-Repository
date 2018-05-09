(ns cmr.opendap.rest.handler.collection
  "This namespace defines the REST API handlers for collection resources."
  (:require
   [cheshire.core :as json]
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

(defn- generate-via-get
  "Private function for creating OPeNDAP URLs when supplied with an HTTP
  GET."
  [request search-endpoint user-token concept-id]
  (log/debug "Generating URLs based on HTTP GET ...")
  (->> request
       :params
       (merge {:collection-id concept-id})
       (ous/get-opendap-urls search-endpoint user-token)
       (response/json request)))

(defn- generate-via-post
  "Private function for creating OPeNDAP URLs when supplied with an HTTP
  POST."
  [request search-endpoint user-token concept-id]
  (->> request
       :body
       (slurp)
       (#(json/parse-string % true))
       (merge {:collection-id concept-id})
       (ous/get-opendap-urls search-endpoint user-token)
       (response/json request)))

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
          concept-id (get-in request [:path-params :concept-id])
          search-endpoint (config/get-search-url component)]
      (case (:request-method request)
        :get (generate-via-get request search-endpoint user-token concept-id)
        :post (generate-via-post request search-endpoint user-token concept-id)
        (unsupported-method request)))))

(defn batch-generate
  "XXX"
  [component]
  ;; XXX how much can we minimize round-tripping here?
  ;;     this may require creating divergent logic/impls ...
  (fn [request]
    {:error :not-implemented}))

(defn stream
  ""
  [request]
  (log/debug "Processing stream request ...")
  (server/with-channel request channel
    ; (server/send! channel
    ;               {:headers {"Content-Type" "text/event-stream; charset=utf-8"
    ;                          "Cache-Control" "no-cache"}}
    ;               false)
    (log/debug "Setting 'on-close' callback ...")
    (server/on-close channel
                     (fn [status]
                      (println "channel closed, " status)))
    (log/debug "Starting loop ...")
    (loop [id 0]
      (log/debug "Loop id:" id)
      (when (< id 10)
        (timer/schedule-task
         (* id 200) ;; send a message every 200ms
         ; (let [msg (format "message #%s from server ..." id)]
         ;  (server/send! channel (format "%x\r\n%s\r\n" (count msg) msg) false))) ; false => don't close after send
         (server/send! channel
                       (format "message #%s from server ..." id)
                       false))
        (recur (inc id))))
    (timer/schedule-task 20000 (server/close channel))))
