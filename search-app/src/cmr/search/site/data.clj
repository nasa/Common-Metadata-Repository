(ns cmr.search.site.data
  "The functions of this namespace are specifically responsible for generating
  data structures to be consumed by site page templates.")

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
