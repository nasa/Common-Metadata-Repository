(ns cmr.ous.app.routes.rest.v2-1
  "This namespace defines the Version 2.1 REST routes provided by this service.

  Upon idnetifying a particular request as matching a given route, work is then
  handed off to the relevant request handler function."
  (:require
   [cmr.http.kit.app.handler :as core-handler]
   [cmr.ous.app.handler.collection :as collection-handler]
   [cmr.ous.app.routes.rest.v1 :as routes-v1]
   [cmr.ous.app.routes.rest.v2 :as routes-v2]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   REST API Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ous-api
  [httpd-component]
  [["/service-bridge/ous/collections" {
    :post {:handler collection-handler/batch-generate}
    :options core-handler/ok}]
   ["/service-bridge/ous/collection/:concept-id" {
    :get {:handler (collection-handler/generate-urls httpd-component)
          :permissions #{:read}}
    :post {:handler (collection-handler/generate-urls httpd-component)
           :permissions #{:read}}
    :options core-handler/ok}]
   ["/service-bridge/ous/streaming-collection/:concept-id" {
    :get (collection-handler/stream-urls httpd-component)}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Assembled Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Note that, since this uses the JAR-file plugin, routes are also pulled
;; in implicitly.

(defn all
  [httpd-component]
  (concat
   (ous-api httpd-component)
   (routes-v2/admin-api httpd-component)))
