(ns cmr.sizing.app.routes.rest
  "Upon idnetifying a particular request as matching a given route, work is then
  handed off to the relevant request handler function."
  (:require
   [cmr.sizing.app.handler :as handler]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   REST API Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn size-estimate-api
  [httpd-component]
  [["/service-bridge/size-estimate/collection/:concept-id" {
    :get {:handler (handler/estimate-size httpd-component)
          :permissions #{:read}}
    :post {:handler (handler/estimate-size httpd-component)
           :permissions #{:read}}}]
   ["/service-bridge/size-estimate/streaming-collection/:concept-id" {
    :get {:handler (handler/stream-estimate-size httpd-component)
          :permissions #{:read}}}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Assembled Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn all
  ([httpd-component]
    (all httpd-component :default))
  ([httpd-component version]
    (size-estimate-api httpd-component)))
