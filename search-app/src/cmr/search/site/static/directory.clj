(ns cmr.search.site.static.directory
  "The functions of this namespace are specifically responsible for generating
  the static resources of the directory pages and directory sitemaps."
  (:require
   [cmr.common-app.static :as static]
   [cmr.common.log :refer :all]
   [cmr.search.site.data :as data]
   [cmr.search.site.util :as util]))

(defn generate-directory-sitemap
  "Generate the sitemap.xml file for a given provider and tag combination."
  [context base-path provider-id tag]
  (static/generate (util/get-provider-sitemap base-path provider-id tag)
                   "templates/search-sitemap-provider-tag.xml"
                   (assoc (data/get-provider-tag-sitemap-landing-links
                           context
                           provider-id
                           tag)
                           :base-url (util/get-app-url context))))

(defn generate-directory-html
  "Generate the search directory page file for a given provider and tag
  combination."
  [context base-path provider-id tag]
  (static/generate (util/get-provider-index base-path provider-id tag)
                   "templates/search-provider-tag-landing-links.html"
                   (assoc (data/get-provider-tag-landing-links
                           context
                           provider-id
                           tag)
                          :base-url (util/make-relative-parents 5))))

(defn generate-directory-resources
  "A convenience function that pulls together all the static content generators
  in this namespace. This is the function that should be called in the parent
  static generator namespace."
  [context base-path tags]
  (doseq [provider-id (map :provider-id (data/get-providers context))]
    (debug "Generating directory-level static files for provider" provider-id)
    (doseq [tag tags]
      (generate-directory-sitemap context base-path provider-id tag)
      (generate-directory-html context base-path provider-id tag))))
