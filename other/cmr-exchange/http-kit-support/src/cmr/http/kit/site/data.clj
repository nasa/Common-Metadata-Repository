(ns cmr.http.kit.site.data
  "The functions of this namespace are specifically responsible for generating
  data structures to be consumed by site page templates.

  Of special note: this namespace and its sibling `page` namespace are only
  ever meant to be used in the `cmr.search.site` namespace, particularly in
  support of creating site routes for access in a browser.

  Under no circumstances should `cmr.search.site.data` be accessed from outside
  this context; the data functions defined herein are specifically for use
  in page templates, structured explicitly for their needs."
  (:require
   [cmr.http.kit.components.config :as config]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Data Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-partner-guide
  "Data for templates that display a link to Partner Guides. Clients should
  overrirde these keys in their own base static and base page maps if they
  need to use different values."
  {:partner-url "https://wiki.earthdata.nasa.gov/display/CMR/CMR+Client+Partner+User+Guide"
   :partner-text "Client Partner's Guide"})

(defn base-static
  "Data that all static pages have in common.

  Note that static pages don't have any context."
  [system]
  (merge default-partner-guide
         {:base-url ""
          :app-title (config/default-page-title system)}))

(defn base-dynamic
  "Data that all pages have in common.

  Note that dynamic pages need to provide the base-url."
  ([system]
   (base-dynamic system {}))
  ([system data]
   (merge default-partner-guide
          {:app-title (config/default-page-title system)}
          data)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Page Data Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti base-page
  "Data that all app pages have in common.

  The `:cli` variant uses a special constructed context (see
  `static.StaticContext`).

  The default variant is the original, designed to work with the regular
  request context which contains the state of a running CMR."
  :execution-context)

(defmethod base-page :cli
  [system data]
  (base-static system data))

(defmethod base-page :default
  [system data]
  (base-dynamic system data))
