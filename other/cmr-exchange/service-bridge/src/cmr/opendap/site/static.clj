(ns cmr.opendap.site.static
  "The functions of this namespace are specifically responsible for generating
  the static resources of the top-level and site pages and sitemaps."
  (:require
   [clojure.java.io :as io]
   [clojusc.twig :as logger]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.components.core :as components]
   [com.stuartsierra.component :as component]
   [markdown.core :as markdown]
   [selmer.parser :as selmer]
   [taoensso.timbre :as log]
   [trifl.java :as trifl])
  (:gen-class))

(logger/set-level! '[cmr.opendap] :info)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn generate
  "This is the function used by default to render templates, given data that
  the template needs to render."
  [target template-file data]
  (log/debug "Rendering data from template to:" target)
  (log/debug "Template:" template-file)
  (log/debug "Data:" data)
  (io/make-parents target)
  (->> data
       (selmer/render-file template-file)
       (spit target)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Content Generators   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn generate-rest-api-docs
  "Generate the HTML for the CMR OPeNDAP REST API docs page."
  [docs-source docs-dir base-url]
  (generate
   (format "%s/index.html" docs-dir)
   "templates/opendap-docs-static.html"
   {:base-url base-url
    :page-content (markdown/md-to-html-string (slurp docs-source))}))

(defn generate-all
  "A convenience function that pulls together all the static content generators
  in this namespace. This is the function that should be called in the parent
  static generator namespace."
  [docs-source docs-dir base-url]
  (log/debug "Generating static site files ..."))

(defn -main
  [& args]
  (let [system-init (components/init :basic)
        system (component/start system-init)]
    (trifl/add-shutdown-handler #(component/stop system))
    (generate-all
      (config/http-rest-docs-source system)
      (config/http-rest-docs-outdir system)
      (config/http-rest-docs-base-url-template system))))
