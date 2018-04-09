(ns cmr.opendap.rest.handler.collection
  "This namespace defines the REST API handlers for collection resources."
  (:require
   [clojure.java.io :as io]
   [cmr.opendap.ous.collection :as collection]
   [cmr.opendap.http.response :as response]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   OUS Handlers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- generate-via-get
  "XXX"
  [request concept-id]
  (->> request
       :params
       (merge {:collection-id concept-id})
       (collection/get-opendap-urls)
       (response/json request)))

(defn- generate-via-post
  "XXX"
  [request concept-id]
  (->> request
       :body
       slurp
       ;; XXX see note about params above, in sister function
       ;(collection/get-opendap-urls conn-mgr params)
       ((fn [_] {:error :not-implemented}))
       (response/json request)))

(defn unsupported-method
  "XXX"
  [request]
  ;; XXX add error message; utilize error reporting infra
  {:error :not-implemented})

(def generate-urls
  "XXX"
  (fn [request]
    (let [concept-id (get-in request [:path-params :concept-id])]
      (case (:method request)
        :get (generate-via-get request concept-id)
        :post (generate-via-post request concept-id)
        (unsupported-method request)))))

(def batch-generate
  "XXX"
  ;; XXX how much can we minimize round-tripping here?
  ;;     this may require creating divergent logic/impls ...
  (fn [request]
    {:error :not-implemented}))
