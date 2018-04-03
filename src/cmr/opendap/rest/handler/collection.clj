(ns cmr.opendap.rest.handler.collection
  "This namespace defines the REST API handlers for collection resources."
  (:require
   [clojure.java.io :as io]
   ;; XXX implement the OUS collection business logic
   ;; [cmr.opendap.ous.collection :as collection]
   [cmr.opendap.rest.response :as response]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   OUS Handlers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- generate-via-get
  "XXX"
  [conn-mgr request concept-id]
  (->> [:params :q]
       (get-in request)
       ;; XXX maybe for params we should pass a populated record,
       ;;     essentially using an API for params expected when
       ;;     generating OPeNDAP URLs ... the record should probably be
       ;;     in cmr.opendap.ous.collection and named something ...
       ;;     OPeNDAPGenURLsParams seems rather horrific; we can pass a
       ;;     map here in params and use that with the map->Record fn ...
       ;(collections/get-opendap-urls conn-mgr params)
       ((fn [_] {:error :not-implemented}))
       (response/json request)))

(defn- generate-via-post
  "XXX"
  [conn-mgr request concept-id]
  (->> request
       :body
       slurp
       ;; XXX see note about params above, in sister function
       ;(collections/get-opendap-urls conn-mgr params)
       ((fn [_] {:error :not-implemented}))
       (response/json request)))

(defn unsupported-method
  "XXX"
  [request]
  ;; XXX add error message; utilize error reporting infra
  {:error :not-implemented})

(defn generate-urls
  "XXX"
  [conn-mgr]
  (fn [request]
    (let [concept-id (get-in request [:path-params :concept-id])]
      (case (:method request)
        :get (generate-via-get conn-mgr request concept-id)
        :post (generate-via-post conn-mgr request concept-id)
        (unsupported-method request)))))

(defn batch-generate
  "XXX"
  [conn-mgr]
  ;; XXX how much can we minimize round-tripping here?
  ;;     this may require creating divergent logic/impls ...
  {:error :not-implemented})
