(ns cmr.plugin.jar.types.web.core
  (:require
   [clojure.edn :as edn]
   [clojure.java.classpath :as classpath]
   [cmr.plugin.jar.core :as plugin])
 (:import
  (java.util.jar JarFile)))

(defn plugin-routes
  [^JarFile jarfile filepath route-keys]
  (get-in (plugin/config-data jarfile filepath) route-keys))

(defn plugins-routes
  [jarfiles filepath route-keys]
  (mapcat (comp vals #(plugin-routes % filepath route-keys)) jarfiles))

(defn assemble-routes
  ([^String plugin-name ^String plugin-type filepath route-keys]
    (assemble-routes (plugin/jarfiles plugin-name plugin-type)
                     plugin-name
                     plugin-type
                     filepath
                     route-keys))
  ([jarfiles ^String plugin-name ^String plugin-type filepath route-keys]
    (-> jarfiles
        (plugins-routes filepath route-keys)
        (#(apply merge %)))))
