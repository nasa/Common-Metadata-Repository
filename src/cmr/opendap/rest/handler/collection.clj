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

(defn- generate
  "Private function for creating OPeNDAP URLs when supplied with an HTTP
  GET."
  [request search-endpoint user-token concept-id data]
  (log/debug "Generating URLs based on HTTP GET ...")
  (->> data
       (merge {:collection-id concept-id})
       (ous/get-opendap-urls search-endpoint user-token)
       (response/json request)))

(defn- generate-via-get
  "Private function for creating OPeNDAP URLs when supplied with an HTTP
  GET."
  [request search-endpoint user-token concept-id]
  (log/debug "Generating URLs based on HTTP GET ...")
  (->> request
       :params
       (generate request search-endpoint user-token concept-id)))

(defn- generate-via-post
  "Private function for creating OPeNDAP URLs when supplied with an HTTP
  POST."
  [request search-endpoint user-token concept-id]
  (->> request
       :body
       (slurp)
       (#(json/parse-string % true))
       (generate request search-endpoint user-token concept-id)))

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

(defn stream-urls
  ""
  [request]
  (log/debug "Processing stream request ...")
  (server/with-channel request channel
    (log/debug "Setting 'on-close' callback ...")
    (server/on-close channel
                     (fn [status]
                      (println "Channel closed; status " status)))
    (log/debug "Starting loop ...")
    (loop [id 0]
      (log/debug "Loop id:" id)
      (when (< id 10)
        (timer/schedule-task
         (* id 200) ;; send a message every 200ms
         (log/debug "\tSending chunk to client ...")
         (server/send! channel
                       ;(format "message #%s from server ..." id)
                       {:status 202}
                       false))
        (recur (inc id))))
    (timer/schedule-task (* 10 200) (server/close channel))))
