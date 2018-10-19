(ns cmr.plugin.jar.types.web.routes
  (:require
   [clojure.edn :as edn]
   [clojure.java.classpath :as classpath]
   [clojure.string :as string]
   [cmr.exchange.common.util :as util]
   [cmr.plugin.jar.core :as plugin]
   [taoensso.timbre :as log])
 (:import
  (clojure.lang Keyword)
  (java.util.jar JarFile)))

(defn resolve-routes
  "This function wraps a utility function for resolving fully-qualified
  function names. What this offers over the utility function is the capability
  of wrapping multiple functions at ones, and removing any functions that
  don't resolve."
  [routes-symbols]
  (->> routes-symbols
       (map #(util/resolve-fully-qualified-fn %))
       (remove nil?)))

(defn plugin-routes
  "Given a:
    * jarfile
    * an in-jarfile path to the plugin's configuration file
    * the configuration value that points to the route-keys, and
    * the configuration values that point to the api-key and site-key
  perform the actual lookup of the plugin routes in the plugin configuration,
  and assemble these into a data structure that can later be transformed into
  a hashmap."
  [^JarFile jarfile in-jar-filepath route-keys ^Keyword api-key ^Keyword site-key]
  (let [data (get-in (plugin/config-data jarfile in-jar-filepath) route-keys)]
    [(api-key data) (site-key data)]))

(defn plugins-routes
  "Perform the assembly done in `plugin-routes` over one or more jarfiles,
  converting the final data structure into a hashmap for easy lookup by the
  application or service that is managing these plugins."
  [jarfiles in-jar-filepath route-keys ^Keyword api-key ^Keyword site-key]
  (let [data (map #(plugin-routes % in-jar-filepath route-keys api-key site-key)
                  jarfiles)]
    {api-key (vec (remove nil? (map first data)))
     site-key (vec (remove nil? (map second data)))}))

(defn resolved-routes-fns
  "This function offers the convenience of locating all of the plugins that
  had declared themselves of the given name (e.g. 'CMR-Plugin') and type
  (e.g. 'service-bridge-app'), and once located, performing the function
  resolution and final data structure creation as done above.

  Note: this function has been refactored several times, and the need for
  this function vs the `plugins-routes` function above is now questionable,
  worth determining, and possibly refactoring."
  ([^String plugin-name ^String plugin-type in-jar-filepath route-keys
    ^Keyword api-key ^Keyword site-key]
    (resolved-routes-fns (plugin/jarfiles plugin-name plugin-type)
                         plugin-name
                         plugin-type
                         in-jar-filepath
                         route-keys))
  ([jarfiles ^String plugin-name ^String plugin-type in-jar-filepath route-keys
    ^Keyword api-key ^Keyword site-key]
    (let [data (plugins-routes
                jarfiles in-jar-filepath route-keys api-key site-key)]
      {;; Note that the first arg for both below will be the
       ;; system/httpd-component; the API routes take an additional arg: the
       ;; API version.
       api-key (resolve-routes (api-key data))
       site-key (resolve-routes (site-key data))})))
