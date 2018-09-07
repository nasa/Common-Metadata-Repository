(ns cmr.plugin.jar.types.web.core
  (:require
   [clojure.edn :as edn]
   [clojure.java.classpath :as classpath]
   [clojure.string :as string]
   [cmr.plugin.jar.core :as plugin])
 (:import
  (clojure.lang Keyword)
  (java.util.jar JarFile)))

(defn resolve-route
  [route]
  (let [[namesp fun] (mapv symbol (string/split (str route) #"/"))]
    (require namesp)
    (var-get (ns-resolve namesp fun))))

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
      {api-key (apply concat (map resolve-route (api-key data)))
       site-key (apply concat (map resolve-route (site-key data)))})))
