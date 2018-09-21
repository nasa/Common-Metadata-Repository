(ns cmr.http.kit.app.core
  (:require
   [cmr.http.kit.app.middleware :as middleware]
   [cmr.http.kit.components.config :as config]
   [cmr.plugin.jar.components.registry :as registry]
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
  (let [{plugins-site-routes :site
         plugins-api-routes :api} (registry/assembled-routes httpd-component)
         main-site-routes-fn (config/site-routes httpd-component)]
    (log/trace "plugins-site-routes:" (vec plugins-site-routes))
    (log/trace "plugins-api-routes:" (vec plugins-api-routes))
    (log/trace "main-site-routes-fn:" main-site-routes-fn)
    ;; Note that the following calls don't call the routes, rather they call
    ;; the configuration function which extract the routes from the config
    ;; data. The route functions provided in the configuration data will be
    ;; called by a middleware wrapper.
    {:site-routes (concat plugins-site-routes (main-site-routes-fn httpd-component))
     :plugins-api-routes plugins-api-routes
     :main-api-routes-fn (config/api-routes httpd-component)}))
