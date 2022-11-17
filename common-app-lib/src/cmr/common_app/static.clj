(ns cmr.common-app.static
  "This namespace contains helpers for generating and returning static
  documentation pages for an application. This usually includes API
  Documentation, Site Routes & Web Resources Documentation, and other web
  resources that would take too long to generate at request time. It is
  intended that applications use this in a `cmr.*.site.static` namespace.


  ## Markdown Support

  This namespace provides a few untility functions for converting Markdown
  (and Markdown files) to HTML. The converted string data may then be used in
  templates. Note that if you are using Selmer templates, you will need to
  'pipe' the converted HTML string data to the `safe` Selmer filter so that
  the HTML isn't escaped:

  ```html
  <div class='content'>
    {{ page-content|safe }}
  </div>
  ```


  ## Application-specific Static Namespace

  While this namespace provides generally useful functions for static content,
  and in particular its generation, you will still need to create functions in
  applications that take advantage of these. In order to do this, as mentioned
  above, create a `cmr.<YOUR-APP>.site.static` namespace in the appropriate
  file and define the necessary functions, e.g.:

  ```clj
  (defn generate-api-docs
    \"Generate CMR Ingest API docs.\"
    []
    (static/generate
     \"resources/public/site/docs/ingest/api.html\"
     \"templates/ingest-docs-static.html\"
     (merge
      (data/base-static)
      {:site-title \"CMR Ingest\"
       :page-title \"API Documentation\"
       :page-content (static/md-file->html \"docs/api.md\")})))
  ```

  Next, you'll want to define a `-main` function that will be used to generate
  the content from the command line, e.g.:

  ```clj
  (defn -main
    \"The entrypoint for command-line static file generation. Example usage:
    ```
    $ lein run -m cmr.ingest.site.static api
    $ lein run -m cmr.ingest.site.static site
    $ lein run -m cmr.ingest.site.static all
    ```\"
    [doc-type]
    (case (keyword doc-type)
      :api (generate-api-docs)
      :site (generate-site-docs)
      :all (do
            (-main :api)
            (-main :site))))
  ```

  With static-content-generating and `-main` functions defined, you'll want to
  update the  `ns` declaration with `(:gen-class)` so that you can call the
  namespace from the command line or lein alias.

  Complete examples are viewable here:

  * `cmr.access-control.site.static`
  * `cmr.ingest.site.static`
  * `cmr.search.site.static`

  Note that these static-content-generating namespaces should be very
  lightweight, not pulling any heavy namespaces in the `require`s. This will
  allow static content to be generated in mere seconds (usually 15-30s) as
  opposed to severl minutes.


  ## Profiles and `lein` Aliases

  `lein` aliases should be created in all the static-content-
  generating projects to make it easier to generate static files:

  ```clj
  :aliases {
    ...
    \"generate-static\" [\"with-profile\" \"static\"
                       \"run\" \"-m\" \"cmr.<PROJ>.site.static\" \"all\"]
    ...}
  ```

  Notes:

  * CMR projects that generate static files use an empty `static` profile in
    their `project.clj` to be used so as not to load all of CMR (the entire
    CMR in a JVM isn't needed to generate static files).
  * There is a top-level alias for generating all static files in all
    subprojects:

  ```clj
  :aliases {
    ...
    \"generate-static\" [\"modules\" \"generate-static\"]
    ...}
  ```

  ## Routing

  An application using this namespace for documentation must define routes to
  load the HTML pages. You can see examples of this in the following:

  * `cmr.access-control.site.routes`
  * `cmr.ingest.site.routes`
  * `cmr.search.site.routes`

  Additionally, some resources are made pre-available (should an app need
  them) by the `docs-routes` utility function in this namespace:

  ```
  ;; In namespace definition
  (:require [cmr.common-app.static :as static])

  ;; In your routes
  (static/docs-routes (:relative-root-url system))
  ```

  ## Generating Documentation

  At the lowest level, static files can be generated with the `generate`
  function in this namespace. As noted above, you should also add an alias in
  the `project.clj` for generating the documentation in the `project.clj`. At
  that point, static content files may be generated for a project by simply
  executing the following at the command line:

  ```
  $ lein generate-static
  ```"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common-app.api.routes :as cr]
   [cmr.common-app.config :as config]
   [cmr.common-app.site.pages :as pages]
   [cmr.common.generics :as gconfig]
   [cmr.common.generics-documentation :as gdocs]
   [compojure.handler :as handler]
   [compojure.route :as route]
   [compojure.core :refer :all]
   [ring.util.response :as response]
   [ring.util.request :as request]
   [selmer.parser :as selmer])
  (:import
   (org.pegdown Extensions PegDownProcessor)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utility Functions & Constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn md->html
  "Given markdown and an optional markdown processor, converts it to HTML.
  If a processor is not provided, one will be created."
  ([markdown]
   (let [processor (PegDownProcessor.
                    (int (bit-or Extensions/AUTOLINKS
                                 Extensions/HARDWRAPS
                                 Extensions/TABLES
                                 Extensions/FENCED_CODE_BLOCKS)))]
     (md->html processor markdown)))
  ([processor markdown]
   (.markdownToHtml processor markdown)))

(defn- read-generic-markdown
  "Reads the file-name mark-down for all concepts"
  [file-name]
  (->  (gdocs/all-generic-docs file-name)
       (md->html)
       (selmer/render {})))

(defn- read-generic-markdown-toc
  "Reads the file-name mark-down for all concepts"
  [markdown]
  (-> (md->html markdown)
      (selmer/render {})))

(def ^:private resource-root "public/site/")

(defn default-renderer
  "This is the function used by default to render templates, given data that
  the template needs to render."
  [template-file data]
  (selmer/render-file template-file data))

(defmacro force-trailing-slash
  "Given a ring request, if the request was made against a resource path with a trailing
  slash, performs the body form (presumably returning a valid ring response).  Otherwise,
  issues a 301 Moved Permanently redirect to the request's resource path with an appended
  trailing slash."
  [req body]
  `(if (.endsWith (:uri ~req) "/")
     ~body
     (assoc (response/redirect (str (request/request-url ~req) "/")) :status 301)))

(defn site-resource
  "Returns a URL for a resource in resource-root, or nil if it does not exist."
  [resource-name]
  (io/resource (str resource-root resource-name)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Routing Setup
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def site-provider-map
  "Defines site and a sample provider map for all sites"
  {"cmr.earthdata.nasa.gov" "PODAAC"
   "cmr.uat.earthdata.nasa.gov" "DEMO_89"
   "cmr.sit.earthdata.nasa.gov" "DEMO_PROV"
   "cmr.wl.earthdata.nasa.gov" "LAADS"})

(defn docs-routes
  "Defines routes for returning API documentation. Takes the public-protocol (http or https),
  relative-root-url of the application, and the location of the welcome page within the classpath."
  ([public-protocol relative-root-url] (docs-routes public-protocol relative-root-url {}))
  ([public-protocol relative-root-url options]
   (routes
    (context "/site" []
       ;; Return swagger.json if the application provides one
      (GET "/swagger.json" {:keys [headers]}
        (if-let [resource (site-resource "swagger.json")]
          {:status 200
           :body (-> resource
                     slurp
                     (string/replace "%CMR-PROTOCOL%" public-protocol)
                     (string/replace "%CMR-HOST%" (headers "host"))
                     (string/replace "%CMR-BASE-PATH%" relative-root-url))
           :headers (:headers (cr/options-response))}
          (route/not-found (site-resource "404.html"))))
       ;; Static HTML resources, typically API documentation which needs endpoint URLs replaced.
       ;; Other values also need to be replaced since they are only known at run time, such as
       ;; the configured Generics to be documented.
      (GET ["/:page", :page #".*\.html$"] {headers :headers, {page :page} :params}
        (when-let [resource (site-resource page)]
          (let [cmr-root (str public-protocol "://" (headers "host") relative-root-url)
                site-example-provider (get site-provider-map (headers "host") "PROV1")
                cmr-example-collection-id (str "C1234567-" site-example-provider)
                doc-type (nth (re-find #"/(.*)/" (str page)) 1)
                generic-doc-body (read-generic-markdown doc-type)
                generic-doc-toc (-> doc-type
                                    (gdocs/all-generic-docs-toc options)
                                    read-generic-markdown-toc
                                    gdocs/format-toc-into-doc)
                generic-versions (md->html (gdocs/generic-document-versions->markdown))]
            {:status 200
             :headers {"Content-Type" "text/html; charset=utf-8"}
             :body (-> resource
                       slurp
                       (string/replace "%GENERIC-TABLE-OF-CONTENTS%" generic-doc-toc)
                       (string/replace "%GENERIC-DOCS%" generic-doc-body)
                       (string/replace "%CMR-ENDPOINT%" cmr-root)
                       (string/replace "%CMR-RELEASE-VERSION%" (config/release-version))
                       (string/replace "%CMR-EXAMPLE-COLLECTION-ID%" cmr-example-collection-id)
                       (string/replace "%ALL-GENERIC-DOCUMENT-VERSIONS%" generic-versions))})))
       ;; Other static resources (Javascript, CSS)
      (route/resources "/" {:root resource-root})
      (pages/not-found))
    (context "/" []
      (route/resources "/"))))
  ([public-protocol relative-root-url welcome-page-location _options]
   (routes
     ;; CMR Application Welcome Page
    (GET "/" req
      (force-trailing-slash req ; Without a trailing slash, the relative URLs in index.html are wrong
                            {:status 200
                             :body (slurp (io/resource welcome-page-location))}))
    (docs-routes public-protocol relative-root-url {}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Core API Documentation Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
    (println " * Copying swagger.json file to" (str swagger-target))
    (io/copy (io/file "resources/schema/swagger.json")
             swagger-target)))

(defn md-file->html
  "Given a markdown filename, slurp it and convert to HTML."
  [md-file]
  (md->html (slurp md-file)))

(defn generate
  "Generates the API documentation HTML page from the markdown source.
  Args
  * docs-target - The file that will be generated with the API documentation.
    Example: `resources/public/site/api_docs.html`
  * template-file - The Selmer template file to use for generation.
    Example: `templates/search-docs-static.html`
  * data - The page data that your template expects to have access to;
    usually a hash map containing such things as page title, lists of links,
    HTML converted from Markdown, etc.
  * render-fn - An optional 2-arity function that will replace the default
    render function, expecting a template filename and template data as
    arguments."
  ([docs-target template-file data]
   (generate docs-target template-file data default-renderer))
  ([docs-target template-file data render-fn]
   (println (format "Generating %s ..." docs-target))
   (io/make-parents docs-target)
   (->> data
        (render-fn template-file)
        (spit docs-target))
   (println "Done.")))
