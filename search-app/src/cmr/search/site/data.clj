(ns cmr.search.site.data
  "The functions of this namespace are specifically responsible for generating
  data structures to be consumed by site page templates.

  Of special note: this namespace and its sibling `page` namespace are only
  ever meant to be used in the `cmr.search.site` namespace, particularly in
  support of creating site routes for access in a browser.

  Under no circumstances should `cmr.search.site.data` be accessed from outside
  this context; the data functions defined herein are specifically for use
  in page templates, structured explicitly for their needs.")

(defn get-index
  "Return the data for the index page (none for now)."
  [request]
  {})

(defn get-landing-links
  "Provide the list of links that will be rendered on the general landing
  pages page."
  [request]
  {:links [{:href "/site/collections/eosdis-landing-pages"
            :text "Landing Pages for EOSDIS Collections"}]})

(defn doi-link
  "Given DOI umm data of the form `{:doi <STRING>}`, generate a landing page
  link."
  [doi-data]
  (format "http://dx.doi.org/%s" (doi-data "DOI")))

(defn cmr-link
  "Given a CMR host and a concept ID, return the collection landing page for
  the given id."
  [cmr-host concept-id]
  (format "https://%s/concepts/%s.html" cmr-host concept-id))

(defn get-eosdis-landing-links
  "Generate the data necessary to render EOSDIS landing page links."
  [request]
  {})
