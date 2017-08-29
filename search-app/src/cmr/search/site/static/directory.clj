(ns cmr.search.site.static.directory
  "The functions of this namespace are specifically responsible for generating
  the static resources of the directory pages and directory sitemaps."
  (:require
   [cmr.common-app.static :as static]
   [cmr.common.log :refer :all]
   [cmr.search.site.data :as data]
   [cmr.search.site.util :as util]))

(defn generate-directory-sitemap
  [context base provider-id tag]
  (static/generate (util/get-provider-sitemap base provider-id tag)
                   "templates/search-sitemap-provider-tag.xml"
                   (assoc (data/get-provider-tag-sitemap-landing-links
                           context
                           provider-id
                           tag)
                           :base-url "https://cmr.earthdata.nasa.gov/search")))

(defn generate-directory-html
  [context base provider-id tag]
  (static/generate (util/get-provider-index base provider-id tag)
                   "templates/search-provider-tag-landing-links.html"
                   (assoc (data/get-provider-tag-landing-links
                           context
                           provider-id
                           tag)
                          :base-url (util/make-relative-parents 5))))

(defn generate-directory-resources
  [context base]
  (doseq [provider-id (map :provider-id (data/get-providers context))]
    (debug "Generating directory-level static files for provider" provider-id)
    (doseq [tag (:tags context)]
      (generate-directory-sitemap context base provider-id tag)
      (generate-directory-html context base provider-id tag))))
