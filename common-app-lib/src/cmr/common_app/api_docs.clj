(ns cmr.common-app.api-docs
  "This namespace contains helpers for generating and returning a page documenting an application's
  APIs. API Documentation for an application consists of two parts, a welcome page and a single page with
  all of the api documentation.

  ## Welcome Page

  The welcome page is served if you hit the root URL of the application. It usually has the name of
  the application, a short description, and then a link to the documentation. Each application
  should define welcome page as <app-folder>/resources/public/index.html. See search app for an
  example.

  ## API Documentation Markdown

  API documentation is written in markdown in a single file located in <app-folder>/api_docs.md.
  You can refer to %CMR-ENDPOINT% in the documentation. It will be replaced with the public URL of
  the application when the page is served.

  ## Routing

  An application using this namespace for documentation must define routes to load the HTML pages.

  ```
  ;; In namespace definition
  (:require [cmr.common-app.api-docs :as api-docs])

  ;; In your routes
  (api-docs/docs-routes (:relative-root-url system))
  ```

  ## Generating Documentation

  API documentation can be generated with the generate function in this namespace. You should add
  an alias to generate the documentation in the project.clj. Performance of running tasks in
  project.clj has been a problem. If you define an empty docs profile it will run much faster than
  in th default profile.

  Replace 'App Name' with the name of the application.

  ```
  :profiles {
    ;; This profile specifically here for generating documentation. It's faster than using the regular
    ;; profile. We're not sure why though. There must be something hooking into the regular profile
    ;; that's running at the end.
    ;; Generate docs with: lein with-profile docs generate-docs
    :docs {}}
  :aliases {\"generate-docs\" [\"exec\" \"-ep\" (pr-str '(do
                                                    (use 'cmr.common-app.api-docs)
                                                    (generate
                                                      \"CMR Foo\"
                                                      \"api_docs.md\"
                                                      \"resources/public/site/foo_api_docs.html\")))]
  ```"
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.util.response :as r]
            [ring.util.request :as request]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [cmr.common-app.api.routes :as cr])
  (:import [org.pegdown
            PegDownProcessor
            Extensions]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Routing Vars

(defmacro force-trailing-slash
  "Given a ring request, if the request was made against a resource path with a trailing
  slash, performs the body form (presumably returning a valid ring response).  Otherwise,
  issues a 301 Moved Permanently redirect to the request's resource path with an appended
  trailing slash."
  [req body]
  `(if (.endsWith (:uri ~req) "/")
     ~body
     (assoc (r/redirect (str (request/request-url ~req) "/")) :status 301)))

(def ^:private resource-root "public/site/")

(defn- site-resource
  "Returns a URL for a resource in resource-root, or nil if it does not exist."
  [resource-name]
  (io/resource (str resource-root resource-name)))

(defn docs-routes
  "Defines routes for returning API documentation. Takes the public-protocol (http or https),
  relative-root-url of the application, and the location of the welcome page within the classpath."
  [public-protocol relative-root-url welcome-page-location]
  (routes
    ;; CMR Application Welcome Page
    (GET "/" req
      (force-trailing-slash req ; Without a trailing slash, the relative URLs in index.html are wrong
                            {:status 200
                             :body (slurp (io/resource welcome-page-location))}))

    (context "/site" []
      ;; Return swagger.json if the application provides one
      (GET "/swagger.json" {:keys [headers]}
        (if-let [resource (site-resource "swagger.json")]
          {:status 200
           :body (-> resource
                     slurp
                     (str/replace "%CMR-PROTOCOL%" public-protocol)
                     (str/replace "%CMR-HOST%" (headers "host"))
                     (str/replace "%CMR-BASE-PATH%" relative-root-url))
           :headers (:headers cr/options-response)}
          (route/not-found (site-resource "404.html"))))
      ;; Static HTML resources, typically API documentation which needs endpoint URLs replaced
      (GET ["/:page", :page #".*\.html$"] {headers :headers, {page :page} :params}
        (when-let [resource (site-resource page)]
          (let [cmr-root (str public-protocol "://" (headers "host") relative-root-url)]
            {:status 200
             :body (-> resource
                       slurp
                       (str/replace "%CMR-ENDPOINT%" cmr-root))})))
      ;; Other static resources (Javascript, CSS)
      (route/resources "/" {:root resource-root})
      (route/not-found (site-resource "404.html")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Documentation Generation Vars

(defn header
  "Defines the header of the generated documentation page."
  [title]
  (format
    "<!DOCTYPE html>
     <html>
     <head>
       <meta charset=\"UTF-8\" />
       <title>%s</title>
         <!--[if lt IE 9 ]>
           <script src=\"http://html5shiv.googlecode.com/svn/trunk/html5.js\"></script>
           <![endif]-->
           <link rel=\"stylesheet\" href=\"bootstrap.min.css\">
           <script src=\"jquery.min.js\"></script>
           <script src=\"bootstrap.min.js\"></script>
           <script>
            // Display markdown generated tables with bootstrap styling.
            // see http://getbootstrap.com/css/#tables
            $(function(){ $(\"table\").addClass(\"table table-bordered table-striped table-hover table-condensed table-responsive\"); });
           </script>
       </head>
       <body lang=\"en-US\">
         <div class=\"container\">
         <h1>%s</h1>"
         title title))

(def footer
  "Defines the footer of the generated documentation page."
  "</div></body></html>")

(defn renderer
  "Given a hash-map of HTML page data, renders a complete HTML page."
  [data]
  (str (header (:page-title data))
       (:page-content data)
       footer))

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

(defn generate
  "Generates the API documentation HTML page from the markdown source.
  Args
  * page-title - The title to use in the generated documentation page.
  * docs-source - The file containing the markdown API documentation.
    Example: `api_docs.md`
  * docs-target - The file that will be generated with the API documentation.
    Example: `resources/public/site/api_docs.html`
  * render-fn - An optional 0-arity function that will replace the default
     render function."
  ([page-title docs-source docs-target]
    (let [data {:page-title page-title
                :page-content (md->html (slurp docs-source))}
          render-fn (fn [] (renderer data))]
      (generate page-title docs-source docs-target render-fn)))
  ([page-title docs-source docs-target render-fn]
   (println (format "Generating %s from %s ..." docs-target docs-source))
   (io/make-parents docs-target)
   (spit docs-target (render-fn))
   (println "Done.")))
