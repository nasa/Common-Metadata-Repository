(ns cmr.http.kit.app.core
  (:require
   [cmr.http.kit.app.middleware :as middleware]
   [cmr.http.kit.components.config :as config]
   [cmr.plugin.jar.components.registry :as registry]
   [cmr.plugin.jar.types.web.routes :as jar-routes]
   [taoensso.timbre :as log]))

(defn collected-routes
  "This function checks to see if there are any plugins with the configured
  plugin name and plugin type and if there are, combines them with the site
  and API routes defined in configuration.

  An example of the plugin routes would be `CMR-Plugin` and
  `service-bridge-app`. These are, respectively, the key and value
  entries in a JAR file's MANIFEST.mf for a dep that has declared itself as
  a plugin. They are supplied in configuration, but determine which routes
  are returned by this function.

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
    {:site-routes (concat (map #(% httpd-component) plugins-site-routes-fns)
                          (main-site-routes-fn httpd-component))
     :plugins-api-routes-fns plugins-api-routes-fns
     :main-api-routes-fn main-api-routes-fn}))
