(ns cmr.http.kit.site.pages
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
   [cmr.http.kit.components.config :as config]
   [cmr.http.kit.site.data :as data]
   [selmer.parser :as selmer]
   [ring.util.response :as response]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Page Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn render-template
  "A utility function for preparing templates."
  [template page-data]
  (response/response
   (selmer/render-file template page-data)))

(defn render-html
  "A utility function for preparing HTML templates."
  [template page-data]
  (response/content-type
   (render-template template page-data)
   "text/html"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   HTML page-genereating functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn home
  "Prepare the home page template."
  [system request data]
  (render-html
   "templates/opendap-home.html"
   (data/base-dynamic system data)))

(defn opendap-docs
  "Prepare the top-level search docs page."
  [system request data]
  (log/debug "Calling opendap-docs page ...")
  (render-html
   "templates/opendap-docs.html"
   (data/base-dynamic system data)))

(defn not-found
  "Prepare the home page template."
  ([system request]
    (not-found
     system request {:base-url (str (config/http-base-url system) "/")}))
  ([system request data]
    (render-html
     "templates/opendap-not-found.html"
     (data/base-dynamic system data))))
