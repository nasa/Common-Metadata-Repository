(ns cmr.search.site.pages
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
   [cmr.search.site.data :as data]
   [ring.util.response :as ring-util]
   [selmer.parser :as selmer]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utility functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn render-template
  "A utility function for preparing template pages."
  ([context template]
    (render-template context template (data/base-page context)))
  ([context template page-data]
    (ring-util/response
     (selmer/render-file template page-data))))

(defn render-html
  "A utility function for preparing template pages."
  ([context template]
    (render-html context template (data/base-page context)))
  ([context template page-data]
    (ring-util/content-type
     (render-template context template page-data)
     "text/html")))

(defn render-xml
  "A utility function for preparing XML templates."
  ([context template]
    (render-xml context template (data/base-page context)))
  ([context template page-data]
    (ring-util/content-type
     (render-template context template page-data)
     "text/xml")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; HTML page-genereating functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn home
  "Prepare the home page template."
  [context]
  (render-html context "templates/index.html" (data/get-index context)))

(defn search-docs
  "Prepare the top-level search docs page."
  [context]
  (render-html context "templates/search-docs.html"))

(defn search-site-docs
  "Prepare the site-specific (non-API) docs page."
  [context]
  (render-html context "templates/search-site-docs.html"))

(defn collections-directory
  "Prepare the page that links to all sub-directory pages.

  For now, this is just a page with a single link (the EOSDIS collections
  directory)."
  [context]
  (render-html
   context
   "templates/directory-links.html"
   (data/get-directory-links context)))

(defn eosdis-collections-directory
  "Prepare the EOSDIS directory page that provides links to all the providers."
  [context]
  (render-html
   context
   "templates/eosdis-directory-links.html"
   (data/get-eosdis-directory-links context)))

(defn provider-tag-directory
  "Prepare the page that provides links to collection landing pages based
  upon a provider and a tag."
  [context provider-id tag]
  (render-html
   context
   "templates/provider-tag-landing-links.html"
   (data/get-provider-tag-landing-links context provider-id tag)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; XML resource-genereating functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn sitemap-master
  "Prepare the XML page that provides the master sitemap, which collects all
  CMR sitemaps together."
  [context]
  (render-xml
   context
   "templates/sitemap-master.xml"
   (data/get-eosdis-directory-links context)))

(defn sitemap-top-level
  "Prepare the XML page that provides the top-most sitemap."
  [context]
  (render-xml
   context
   "templates/sitemap-top-level.xml"
   (data/get-eosdis-directory-links context)))

(defn sitemap-provider-tag
  "Prepare the XML page that provides the sitemap associated with the given
  provider and tag."
  [context provider-id tag]
  (render-xml
   context
   "templates/sitemap-provider-tag.xml"
   (data/get-provider-tag-sitemap-landing-links context provider-id tag)))
