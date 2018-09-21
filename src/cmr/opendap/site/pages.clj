(ns cmr.opendap.site.pages
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
   [cmr.http.kit.site.data :as data]
   [cmr.http.kit.site.pages :as pages]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   HTML page-genereating functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn home
  "Prepare the home page template."
  [request data]
  (pages/render-html
   "templates/opendap-home.html"
   (data/base-dynamic data)))

(defn opendap-docs
  "Prepare the top-level search docs page."
  [request data]
  (log/debug "Calling opendap-docs page ...")
  (pages/render-html
   "templates/opendap-docs.html"
   (data/base-dynamic data)))
