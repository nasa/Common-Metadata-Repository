(ns cmr.opendap.app.routes.site
  "This namespace defines the REST routes provided by this service.

  Upon idnetifying a particular request as matching a given route, work is then
  handed off to the relevant request handler function."
  (:require
   [cmr.opendap.components.config :as config]
   [cmr.opendap.app.handler.cache :as cache-handler]
   [cmr.opendap.app.handler.collection :as collection-handler]
   [cmr.opendap.app.handler.core :as core-handler]
   [cmr.opendap.health :as health]
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
    :head core-handler/ok}]])

(defn docs
  "Note that these routes only cover part of the docs; the rest are supplied
  via static content from specific directories (done in middleware)."
  [httpd-component]
  [["/opendap/docs" {
    :get (core-handler/dynamic-page
          pages/opendap-docs
          {:base-url (config/opendap-url httpd-component)})}]])

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
;;;   Assembled Routes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn all
  [httpd-component]
  (concat
   (main httpd-component)
   (docs httpd-component)
   (redirects httpd-component)
   (static httpd-component)))
