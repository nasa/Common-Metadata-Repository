(ns cmr.opendap.site.static
  "The functions of this namespace are specifically responsible for generating
  the static resources of the top-level and site pages and sitemaps."
  (:require
   [clojure.java.io :as io]
   [clojusc.twig :as logger]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.components.core :as components]
   [cmr.opendap.site.data :as data]
   [com.stuartsierra.component :as component]
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

(defn generate-home
  "Generate the HTML for the CMR OPeNDAP home page."
  [docroot base-url]
  (generate
   (format "resources/%s/index.html" docroot)
   "templates/opendap-home.html"
   {:base-url base-url}))

(defn generate-rest-api-docs
  "Generate the HTML for the CMR OPeNDAP REST API docs page."
  [docsdir base-url]
  (generate
   (format "resources/%s/index.html" docsdir)
   "templates/opendap-docs-static.html"
   {:base-url base-url}))

(defn generate-all
  "A convenience function that pulls together all the static content generators
  in this namespace. This is the function that should be called in the parent
  static generator namespace."
  [docroot docsdir base-url]
  (log/debug "Generating static site files ...")
  ;;(generate-home docroot base-url)
  ;;(generate-rest-api-docs docsdir base-url)
  )

(defn -main
  [& args]
  (let [system-init (components/init :basic)
        system (component/start system-init)]
    (trifl/add-shutdown-handler #(component/stop system))
    (log/debug "system:" (into {} system))
    (generate-all
      (config/http-docroot system)
      (config/http-docs system)
      (config/opendap-url system))))
