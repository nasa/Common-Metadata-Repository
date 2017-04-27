(ns cmr.common-app.site.data
  "The functions of this namespace are specifically responsible for generating
  data structures to be consumed by site page templates.

  Of special note: this namespace and its sibling `page` namespace are only
  ever meant to be used in the `cmr.common-app.site` namespace, particularly in
  support of creating site routes for access in a browser.

  Under no circumstances should `cmr.common-app.site.data` be accessed from
  outside this context; the data functions defined herein are specifically for
  use in page templates, structured explicitly for their needs."
  (:require
   [clj-time.core :as clj-time]
   [cmr.transmit.config :as config]))

(defn time-page
  "Data for templates that use time data."
  []
  {:datestamp (str (clj-time/today))})

(defn base-static
  "Data that all static pages have in common.

  Note that static pages don't have any context."
  []
  (assoc (time-page) :base-url "../../../"))

(defn base-page
  "Data that all pages have in common."
  ([context]
   (base-page context "CMR"))
  ([context app-title]
   (assoc (time-page)
          :base-url (config/application-public-root-url context)
          :app-title app-title)))
