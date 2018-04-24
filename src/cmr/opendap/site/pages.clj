(ns cmr.opendap.site.pages
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
   [cmr.opendap.site.data :as data]
   [selmer.parser :as selmer]
   [ring.util.response :as response]))

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
  [request data]
  (render-html
   "templates/opendap-home.html"
   (data/base-dynamic data)))

(defn opendap-docs
  "Prepare the top-level search docs page."
  [request data]
  (render-html
   "templates/opendap-docs.html"
   (data/base-dynamic data)))

(defn not-found
  "Prepare the home page template."
  ([request]
    (not-found request {:base-url "/opendap"}))
  ([request data]
    (render-html
     "templates/opendap-not-found.html"
     (data/base-dynamic data))))
