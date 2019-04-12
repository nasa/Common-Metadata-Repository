(ns cmr.ingest.site.data
  "The functions of this namespace are specifically responsible for generating
  data structures to be consumed by site page templates.

  Of special note: this namespace and its sibling `page` namespace are only
  ever meant to be used in the `cmr.ingest.site` namespace, particularly in
  support of creating site routes for access in a browser.

  Under no circumstances should `cmr.ingest.site.data` be accessed from outside
  this context; the data functions defined herein are specifically for use in
  page templates, structured explicitly for their needs."
  (:require
   [cmr.common-app.config :as common-config]
   [cmr.common-app.site.data :as common-data]))

(def data-partners-guide
  "Data for data partner's guide. Ingest includes a link to the Data Partner's Guide instead
  of the Client Partner's guide."
  {:partner-url "https://wiki.earthdata.nasa.gov/display/CMR/CMR+Data+Partner+User+Guide"
   :partner-text "Data Partner's Guide"})

(defn base-page
  "Data that all app pages have in common."
  [context]
  (merge (common-data/base-page context)
         data-partners-guide
         {:app-title "CMR Ingest" :release-version (str "v " (common-config/release-version))}))

(defn base-static
  "Data that all static pages have in common.

  Note that static pages don't have any context."
  []
  (merge (common-data/base-static)
         data-partners-guide
         {:app-title "CMR Ingest" :release-version (str "v " (common-config/release-version))}))
