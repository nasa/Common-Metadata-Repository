(ns cmr.search.site.static.site
  "The functions of this namespace are specifically responsible for generating
  the static resources of the top-level and site pages and sitemaps."
  (:require
   [cmr.common-app.static :as static]
   [cmr.common.log :refer :all]
   [cmr.search.site.data :as data]
   [cmr.search.site.util :as util]))

(defn generate-master-sitemap
  "Generate the master sitemap.xml file that sits at HOST/sitemap.xml.

  This file contains only locations of other sitemap XML files."
  [context base-path]
  (static/generate (str base-path "/resources/public/sitemap.xml")
                   "templates/search-sitemap-master.xml"
                   (assoc (data/get-eosdis-directory-links context)
                          :base-url (util/get-app-url context))))

(defn generate-site-level-sitemap
  "Generate the master sitemap.xml file that sits at HOST/site/sitemap.xml.

  This file contains locations to top-level HTML content as well as links to
  other sitemap XML files."
  [context base-path]
  (static/generate (str base-path "resources/public/site/sitemap.xml")
                   "templates/search-sitemap-top-level.xml"
                   (assoc (data/get-eosdis-directory-links context)
                          :base-url (util/get-app-url context))))

(defn generate-top-level-html
  "Generate the HTML directory page(s) for the tag(s) currently supported by
  the CMR directory pages. Each page generated here should have links to
  providers that match the underlying directory search criteria."
  [context base-path]
  (static/generate
   (str base-path "resources/public/site/collections/directory/eosdis/index.html")
             "templates/search-eosdis-directory-links.html"
             (assoc (data/get-eosdis-directory-links context)
                    :base-url (util/make-relative-parents 4))))

(defn generate-top-level-resources
  "A convenience function that pulls together all the static content generators
  in this namespace. This is the function that should be called in the parent
  static generator namespace."
  [context base-path]
  (debug "Generating top-level static files")
  (generate-master-sitemap context base-path)
  (generate-site-level-sitemap context base-path)
  (generate-top-level-html context base-path))
