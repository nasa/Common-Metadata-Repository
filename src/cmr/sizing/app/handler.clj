(ns cmr.sizing.app.handler
  "This namespace defines the REST API handlers for collection resources."
  (:require
   [cmr.authz.token :as token]
   ;; XXX need an answer to these deps ...
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
   [cmr.sizing.core :as sizing]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Size Estimate Handlers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn estimate-size
  [component]
  (fn [req]
    (log/debug "Estimating download size based on HTTP GET ...")
    (let [user-token (token/extract req)
          concept-id (get-in req [:path-params :concept-id])]
      (->> req
           :params
           (merge {:collection-id concept-id})
           (sizing/estimate-size component user-token)
           (response/json req)))))

(defn stream-estimate-size
  [component]
  (fn [req]
    {:errors [:not-implemented]}))
