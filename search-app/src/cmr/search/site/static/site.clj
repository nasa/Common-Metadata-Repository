(ns cmr.search.site.static.site
  "The functions of this namespace are specifically responsible for generating
  the static resources of the top-level and site pages and sitemaps."
  (:require
   [cmr.common-app.static :as static]
   [cmr.common.log :refer :all]
   [cmr.search.site.data :as data]
   [cmr.search.site.util :as util]))

(defn generate-master-sitemap
  [context base]
  (static/generate (str base "/resources/public/sitemap.xml")
                   "templates/search-sitemap-master.xml"
                   (assoc (data/get-eosdis-directory-links context)
                          :base-url "https://cmr.earthdata.nasa.gov/search/")))

(defn generate-site-level-sitemap
  [context base]
  (static/generate (str base "resources/public/site/sitemap.xml")
                   "templates/search-sitemap-top-level.xml"
                   (assoc (data/get-eosdis-directory-links context)
                          :base-url "https://cmr.earthdata.nasa.gov/search/")))

(defn generate-top-level-html
  [context base]
  (static/generate
   (str base "resources/public/site/collections/directory/eosdis/index.html")
             "templates/search-eosdis-directory-links.html"
             (assoc (data/get-eosdis-directory-links context)
                    :base-url (util/make-relative-parents 4))))

(defn generate-top-level-resources
  [context base]
  (debug "Generating top-level static files")
  (generate-master-sitemap context base)
  (generate-site-level-sitemap context base)
  (generate-top-level-html context base))
