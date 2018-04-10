(ns cmr.opendap.rest.handler.collection
  "This namespace defines the REST API handlers for collection resources."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [cmr.opendap.ous.collection :as collection]
   [cmr.opendap.http.response :as response]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   OUS Handlers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- generate-via-get
  "Private function for creating OPeNDAP URLs when supplied with an HTTP
  GET."
  [request concept-id]
  (log/debug "Generating URLs based on HTTP GET ...")
  (->> request
       :params
       (merge {:collection-id concept-id})
       (collection/get-opendap-urls)
       (response/json request)))

(defn- generate-via-post
  "Private function for creating OPeNDAP URLs when supplied with an HTTP
  POST."
  [request concept-id]
  (->> request
       :body
       (slurp)
       (#(json/parse-string % true))
       (merge {:collection-id concept-id})
       (collection/get-opendap-urls)
       (response/json request)))

(defn unsupported-method
  "XXX"
  [request]
  ;; XXX add error message; utilize error reporting infra
  {:error :not-implemented})

(def generate-urls
  "XXX"
  (fn [request]
    (log/debug "Method-dispatching for URLs generation ...")
    (log/trace "request:" request)
    (let [concept-id (get-in request [:path-params :concept-id])]
      (case (:request-method request)
        :get (generate-via-get request concept-id)
        :post (generate-via-post request concept-id)
        (unsupported-method request)))))

(def batch-generate
  "XXX"
  ;; XXX how much can we minimize round-tripping here?
  ;;     this may require creating divergent logic/impls ...
  (fn [request]
    {:error :not-implemented}))
