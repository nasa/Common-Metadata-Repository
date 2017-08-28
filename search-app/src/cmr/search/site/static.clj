(ns cmr.search.site.static
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [cmr.common-app.static :as static]
   [cmr.common.log :refer :all]
   [cmr.search.site.data :as data]
   [cmr.search.site.util :as util]
   [cmr.transmit.config :as transmit])
  (:gen-class))

(defn generate-api-docs
  "Generate CMR Search API docs."
  []
  (static/generate
   "resources/public/site/docs/search/api.html"
   "templates/search-docs-static.html"
   (merge (data/base-page {:cmr-application :search
                           :execution-context :cli})
          {:site-title "CMR Search"
           :page-title "API Documentation"
           :page-content (static/md-file->html "docs/api.md")})))

(defn generate-site-docs
  "Generate CMR Search docs for routes and web resources."
  []
  (static/generate
   "resources/public/site/docs/search/site.html"
   "templates/search-docs-static.html"
   (merge (data/base-page {:cmr-application :search
                           :execution-context :cli})
          {:site-title "CMR Search"
           :page-title "Site Routes & Web Resource Documentation"
           :page-content (static/md-file->html "docs/site.md")})))

(defn generate-directory-resources
  [context base]
  ;; Generate top-level XML sitemap files
  (static/generate (str base "/resources/public/sitemap.xml")
                   "templates/search-sitemap-master.xml"
                   (assoc (data/get-eosdis-directory-links context)
                          :base-url "https://cmr.earthdata.nasa.gov/search/"))
  (static/generate (str base "resources/public/site/sitemap.xml")
                   "templates/search-sitemap-top-level.xml"
                   (assoc (data/get-eosdis-directory-links context)
                          :base-url "https://cmr.earthdata.nasa.gov/search/"))
  ;; Generate top-level HTML fies
  (static/generate (str base "resources/public/site/collections/directory/eosdis/index.html")
                   "templates/search-eosdis-directory-links.html"
                   (assoc (data/get-eosdis-directory-links context)
                          :base-url (util/make-relative-parents 4)))
  ;; Now work on the provider pages ...
  (doseq [provider-id (map :provider-id (data/get-providers context))]
    (debug "Generating static files for provider" provider-id)
    (doseq [tag (:tags context)]
      ;; Generate XML sitemap files per provider+tag combination
      (static/generate (util/get-provider-sitemap base provider-id tag)
                       "templates/search-sitemap-provider-tag.xml"
                       (assoc (data/get-provider-tag-sitemap-landing-links
                               context
                               provider-id
                               tag)
                               :base-url "https://cmr.earthdata.nasa.gov/search"))
      ;; Generate directory HTML files per provider+tag combination
      (static/generate (util/get-provider-index base provider-id tag)
                       "templates/search-provider-tag-landing-links.html"
                       (assoc (data/get-provider-tag-landing-links
                               context
                               provider-id
                               tag)
                              :base-url (util/make-relative-parents 5))))))

(defn generate-site-resources
  "Generate CMR Search site resources such as directory pages and XML sitemaps
  that are too expensive to generate dynamically."
  []
  (let [supported-tags ["gov.nasa.eosdis"]
        context {:cmr-application :search
                 :execution-context :cli
                 :tags supported-tags}]
    (info "Created context:" context)
    (generate-directory-resources context (util/get-search-app-abs-path))))

(defn -main
  "The entrypoint for command-line static docs generation. Example usage:

  $ lein run -m cmr.search.site.static prep
  $ lein run -m cmr.search.site.static api
  $ lein run -m cmr.search.site.static site
  $ lein run -m cmr.search.site.static all"
  [doc-type]
  (case (keyword doc-type)
    :prep (static/prepare-docs)
    :api (generate-api-docs)
    :site (do
            (generate-site-docs)
            (generate-site-resources))
    :all (do
           (-main :prep)
           (-main :api)
           (-main :site))))
