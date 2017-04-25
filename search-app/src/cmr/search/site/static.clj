(ns cmr.search.site.static
  "The functions of this namespace are specifically responsible for returning
  ready-to-serve pages."
  (:require
   [clojure.java.io :as io]
   [cmr.search.site.util :as util])
  (:gen-class))

(defn prepare-docs
  "Copy the static docs support files to where they will be served as static
  content."
  []
  (println "Preparing docs generator ...")
  (let [json-target (io/file "resources/public/site/JSONQueryLanguage.json")
        aql-target (io/file "resources/public/site/IIMSAQLQueryLanguage.xsd")
        swagger-target (io/file "resources/public/site/swagger.json")]
    (println " * Copying JSON Query Language Schema to" (str json-target))
    (io/make-parents json-target)
    (io/copy (io/file "resources/schema/JSONQueryLanguage.json")
             json-target)
    (println " * Copying AQL Schema to" (str aql-target))
    (io/copy (io/file "resources/schema/IIMSAQLQueryLanguage.xsd")
             aql-target)
    (println " * Copying swagger.json file to " (str swagger-target))
    (io/copy (io/file "resources/schema/swagger.json")
             swagger-target)))

(defn generate-api-docs
  "Generate CMR Search API docs."
  []
  (util/generate-docs "CMR Search"
                      "API Documentation"
                      "docs/api.md"
                      "templates/search-docs-static.html"
                      "resources/public/site/docs/api.html"))


(defn generate-site-docs
  "Generate CMR Search docs for routes and web resources."
  []
  (util/generate-docs "CMR Search"
                      "Site Routes & Web Resource Documentation"
                      "docs/site.md"
                      "templates/search-docs-static.html"
                      "resources/public/site/docs/site.html"))

(defn -main
  "The entrypoint for command-line static docs generation. Example usage:

  $ lein run -m cmr.search.site.static prep
  $ lein run -m cmr.search.site.static api
  $ lein run -m cmr.search.site.static site
  $ lein run -m cmr.search.site.static all"
  [doc-type]
  (case (keyword doc-type)
    :prep (prepare-docs)
    :api (generate-api-docs)
    :site (generate-site-docs)
    :all (do
          (-main :prep)
          (-main :api)
          (-main :site))))
