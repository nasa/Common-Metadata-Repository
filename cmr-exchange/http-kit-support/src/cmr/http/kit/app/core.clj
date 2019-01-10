(ns cmr.http.kit.app.core
  (:require
   [cmr.http.kit.app.middleware :as middleware]
   [cmr.http.kit.components.config :as config]
   [cmr.plugin.jar.components.registry :as registry]
   [cmr.plugin.jar.types.web.routes :as jar-routes]
   [taoensso.timbre :as log]))

(defn site-routes
  [httpd-component routes-fns]
  (->> routes-fns
       (map #(% httpd-component))
       (remove nil?)
       vec))

(defn collected-routes
  "This function checks to see if there are any plugins with the configured
  plugin name (e.g. 'CMR-Plugin') and plugin type (e.g. 'service-bridge-app')
  and if there are, performs some initials steps, if possible. Note that the
  plugin name and plugin type are respectively the key and value entries in a
  JAR file's MANIFEST.mf for a dep that has declared itself as a plugin. They
  are supplied in configuration and determine which routes are returned by this
  function.

  In particular, anything that can be built with just the system/component data
  is fair game right now; anything that requires request data will have to wait
  until request time (e.g. inside middleware). The routes for app webpages fall
  into the first category, so we assemble those in full. Since our REST APIs are
  versioned based upon the 'Accept' header in requests, we have to wait to
  finish plugin route assembly until later.

  For more information about JAR-file-based plugins, see:
  * https://github.com/cmr-exchange/cmr-jar-plugin"
  [httpd-component]
  (let [{plugins-site-routes-fns :site
         plugins-api-routes-fns :api} (registry/resolved-routes httpd-component)
         main-site-routes-fn (config/site-routes httpd-component)
         main-api-routes-fn (config/api-routes httpd-component)]
    ;; Note that the following calls don't call the routes, rather they call
    ;; the configuration function which extract the routes from the config
    ;; data. The route functions provided in the configuration data will be
    ;; called by a middleware wrapper.
    {:site-routes (concat (site-routes httpd-component plugins-site-routes-fns)
                          (main-site-routes-fn httpd-component))
     :plugins-api-routes-fns plugins-api-routes-fns
     :main-api-routes-fn main-api-routes-fn}))
