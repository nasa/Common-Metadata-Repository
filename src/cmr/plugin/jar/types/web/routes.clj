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
  [routes-symbols]
  (apply concat (map util/resolve-fully-qualified-fn routes-symbols)))

(defn plugin-routes
  [^JarFile jarfile in-jar-filepath route-keys ^Keyword api-key ^Keyword site-key]
  (let [data (get-in (plugin/config-data jarfile in-jar-filepath) route-keys)]
    [(api-key data) (site-key data)]))

(defn plugins-routes
  [jarfiles in-jar-filepath route-keys ^Keyword api-key ^Keyword site-key]
  (let [data (map #(plugin-routes % in-jar-filepath route-keys api-key site-key)
                  jarfiles)]
    {api-key (mapv first data)
     site-key (mapv second data)}))

(defn assemble-routes
  ([^String plugin-name ^String plugin-type in-jar-filepath route-keys
    ^Keyword api-key ^Keyword site-key]
    (assemble-routes (plugin/jarfiles plugin-name plugin-type)
                     plugin-name
                     plugin-type
                     in-jar-filepath
                     route-keys))
  ([jarfiles ^String plugin-name ^String plugin-type in-jar-filepath route-keys
    ^Keyword api-key ^Keyword site-key]
    (let [data (plugins-routes jarfiles in-jar-filepath route-keys api-key site-key)]
      {api-key (resolve-routes (api-key data))
       site-key (resolve-routes (site-key data))})))
