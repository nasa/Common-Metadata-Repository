(ns cmr.search.site.pages
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
    [cmr.search.site.data :as data]
    [selmer.parser :as selmer]))

(defn render-template-ok
  "A utility function for preparing template pages."
  ([template]
    (render-template-ok template {}))
  ([template data]
    {:status 200
     :body (selmer/render-file template data)}))

(defn home
  "Prepare the home page template."
  [request]
  (render-template-ok
    "templates/index.html"
    (data/get-index request)))

(defn search-docs
  "Prepare the top-level search docs page."
  [request]
  (render-template-ok
    "templates/search-docs.html"))

(defn search-site-docs
  "Prepare the site-specific (non-API) docs page."
  [request]
  (render-template-ok
    "templates/search-site-docs.html"))

(defn collections-directory
  "Prepare the page that links to all sub-directory pages.

  For now, this is just a page with a single link (the EOSDIS collections
  directory)."
  [request]
  (render-template-ok
    "templates/directory-links.html"
    (data/get-directory-links request)))

(defn eosdis-collections-directory
  "Prepare the EOSDIS directory page that provides links to all the providers."
  [request]
  (render-template-ok
    "templates/eosdis-directory-links.html"
    (data/get-eosdis-directory-links request)))
