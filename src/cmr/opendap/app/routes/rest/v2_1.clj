(ns cmr.opendap.app.routes.rest.v2-1
  "This namespace defines the Version 2.1 REST routes provided by this service.

  Upon idnetifying a particular request as matching a given route, work is then
  handed off to the relevant request handler function."
  (:require
   [cmr.opendap.app.handler.collection :as collection-handler]
   [cmr.opendap.app.routes.rest.v1 :as routes-v1]
   [cmr.opendap.app.routes.rest.v2 :as routes-v2]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   REST API Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn service-bridge-api
  [httpd-component]
  [["/service-bridge/crossover/collection/:concept-id" {
    :get {:handler (collection-handler/bridge-services httpd-component)
          :permissions #{:read}}}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Assembled Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Note that, since this uses the JAR-file plugin, routes are also pulled
;; in implicitly.

(defn all
  [httpd-component]
  (concat
   (service-bridge-api httpd-component)
   (routes-v2/all httpd-component)))
