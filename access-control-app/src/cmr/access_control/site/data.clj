(ns cmr.access-control.site.data
  "The functions of this namespace are specifically responsible for generating
  data structures to be consumed by site page templates.

  Of special note: this namespace and its sibling `page` namespace are only
  ever meant to be used in the `cmr.access-control.site` namespace,
  particularly in support of creating site routes for access in a browser.

  Under no circumstances should `cmr.access-control.site.data` be accessed
  from outside this context; the data functions defined herein are specifically
  for use in page templates, structured explicitly for their needs."
  (:require
   [cmr.common-app.config :as common-config]
   [cmr.common-app.site.data :as common-data]))

(defn base-page
  "Data that all app pages have in common."
  [context]
  (assoc (common-data/base-page context) :app-title "CMR Access Control"
                                         :release-version (str "v " (common-config/release-version))))

(defn base-static
  "Data that all static pages have in common.

  Note that static pages don't have any context."
  []
  (assoc (common-data/base-static) :app-title "CMR Access Control"
                                   :release-version (str "v " (common-config/release-version))))
