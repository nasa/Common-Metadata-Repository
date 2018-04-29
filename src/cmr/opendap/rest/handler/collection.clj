(ns cmr.opendap.rest.handler.collection
  "This namespace defines the REST API handlers for collection resources."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [cmr.opendap.auth.token :as token]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.ous.core :as ous]
   [cmr.opendap.http.response :as response]
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
