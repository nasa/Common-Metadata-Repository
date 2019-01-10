(ns cmr.sizing.app.handler
  "This namespace defines the REST API handlers for collection resources."
  (:require
   [cmr.authz.token :as token]
   [cmr.http.kit.request :as request]
   [cmr.http.kit.response :as response]
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
           (merge {:collection-id concept-id
                   :request-id (request/extract-request-id req)})
           (sizing/estimate-size component user-token)
           ;; We may need to override this in our own response ns if the base
           ;; error handler in cmr.http.kit isn't sufficient ...
           (response/json req)))))

(defn stream-estimate-size
  [component]
  (fn [req]
    {:errors [:not-implemented]}))
