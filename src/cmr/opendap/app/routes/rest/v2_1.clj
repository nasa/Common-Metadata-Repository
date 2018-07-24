(ns cmr.opendap.app.routes.rest.v2-1
  "This namespace defines the Version 2.1 REST routes provided by this service.

  Upon idnetifying a particular request as matching a given route, work is then
  handed off to the relevant request handler function."
  (:require
   [cmr.opendap.app.handler.collection :as collection-handler]
   [cmr.opendap.app.handler.core :as core-handler]
   [cmr.opendap.app.routes.rest.v1 :as routes-v1]
   [cmr.opendap.app.routes.rest.v2 :as routes-v2]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   REST API Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ous-api
  [httpd-component]
  [["/opendap/ous/collections" {
    :post {:handler collection-handler/batch-generate
          ;; XXX CMR-4864, CMR-4863
          ;;     Protecting collections will be a little different than
          ;;     protecting a single collection, since the concept-id isn't in
          ;;     the path-params. Instead, we'll have to parse the body,
          ;;     extract the concepts ids from that, create an ACL query
          ;;     containing multiple concept ids, and then check those results.
          ;; :permission #{...?}
          }
    :options core-handler/ok}]
   ["/opendap/ous/collection/:concept-id" {
    :get {:handler (collection-handler/generate-urls httpd-component)
          :permissions #{:read}}
    :post {:handler (collection-handler/generate-urls httpd-component)
           :permissions #{:read}}
    :options core-handler/ok}]
   ["/opendap/ous/streaming-collection/:concept-id" {
    :get (collection-handler/stream-urls httpd-component)}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Assembled Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn all
  [httpd-component]
  (concat
   (ous-api httpd-component)
   (routes-v2/admin-api httpd-component)
   routes-v1/testing))
