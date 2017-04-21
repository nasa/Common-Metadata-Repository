(ns cmr.search.site.pages
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
   [cmr.search.site.data :as data]
   [selmer.parser :as selmer]))

(defn render-template-ok
  "A utility function for preparing template pages.

  The function name includes the postfix `-ok` to indicate that it returns a
  200 HTTP response status code. Pages that need to return other codes should
  utilize a different function for rendering."
  ([context template]
    (render-template-ok context template (data/base-page context)))
  ([context template page-data]
    {:status 200
     :body (selmer/render-file template page-data)}))

(defn home
  "Prepare the home page template."
  [context]
  (render-template-ok context "templates/index.html" (data/get-index context)))

(defn search-docs
  "Prepare the top-level search docs page."
  [context]
  (render-template-ok context "templates/search-docs.html"))

(defn search-site-docs
  "Prepare the site-specific (non-API) docs page."
  [context]
  (render-template-ok context "templates/search-site-docs.html"))

(defn collections-directory
  "Prepare the page that links to all sub-directory pages.

  For now, this is just a page with a single link (the EOSDIS collections
  directory)."
  [context]
  (render-template-ok
   context
   "templates/directory-links.html"
   (data/get-directory-links context)))

(defn eosdis-collections-directory
  "Prepare the EOSDIS directory page that provides links to all the providers."
  [context]
  (render-template-ok
   context
   "templates/eosdis-directory-links.html"
   (data/get-eosdis-directory-links context)))

(defn provider-tag-directory
  "Prepare the page that provides links to collection landing pages based
  upon a provider and a tag."
  [context provider-id tag]
  (render-template-ok
   context
   "templates/provider-tag-landing-links.html"
   (data/get-provider-tag-landing-links context provider-id tag)))
