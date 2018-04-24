(ns cmr.opendap.rest.route
  "This namespace defines the REST routes provided by this service.

  Upon idnetifying a particular request as matching a given route, work is then
  handed off to the relevant request handler function."
  (:require
   [cmr.opendap.components.config :as config]
   [cmr.opendap.health :as health]
   [cmr.opendap.rest.handler.collection :as collection-handler]
   [cmr.opendap.rest.handler.core :as core-handler]
   [cmr.opendap.site.pages :as pages]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   CMR OPeNDAP Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn main
  [httpd-component]
  [["/opendap" {
    :get (core-handler/dynamic-page
          pages/home
          {:base-url (config/opendap-url httpd-component)})
    :head core-handler/ok}]
   ["/opendap/docs" {
    :get (core-handler/dynamic-page
          pages/opendap-docs
          {:base-url (config/opendap-url httpd-component)})}]])

(defn ous-api
  [httpd-component]
  [["/opendap/ous/collections" {
    :post {:handler collection-handler/batch-generate
          ;; XXX protecting collections will be a little different than
          ;;     protecting a single collection, since the concept-id isn't in
          ;;     the path-params. Instead, we'll have to parse the body,
          ;;     extract the concepts ids from that, create an ACL query
          ;;     containing multiple concept ids, and then check those results.
          ;; :permission #{...?}
          }
    :options core-handler/ok}]
   ["/opendap/ous/collection/:concept-id" {
    :get {:handler collection-handler/generate-urls
          :permissions #{:read}}
    :post {:handler collection-handler/generate-urls
           :permissions #{:read}}
    :options core-handler/ok}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Static & Redirect Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn redirects
  [httpd-component]
  [["/opendap/robots.txt" {
    :get (core-handler/permanent-redirect
          (str (config/get-search-url httpd-component)
               "/robots.txt"))}]])

(defn static
  [httpd-component]
  [;; Google verification files
   ["/opendap/googled099d52314962514.html" {
    :get (core-handler/text-file
          "public/verifications/googled099d52314962514.html")}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Admin Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn admin
  [httpd-component]
  [["/opendap/health" {
    :get (core-handler/health httpd-component)
    :options core-handler/ok}]
   ["/opendap/ping" {
    :get {:handler core-handler/ping
          :roles #{:admin}}
    :post {:handler core-handler/ping
           :roles #{:admin}}
    :options core-handler/ok}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Testing Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def testing
  [["/testing/401" {:get (core-handler/status :unauthorized)}]
   ["/testing/403" {:get (core-handler/status :forbidden)}]
   ["/testing/404" {:get (core-handler/status :not-found)}]
   ["/testing/405" {:get (core-handler/status :method-not-allowed)}]
   ["/testing/500" {:get (core-handler/status :internal-server-error)}]
   ["/testing/503" {:get (core-handler/status :service-unavailable)}]])
