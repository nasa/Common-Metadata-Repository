(ns cmr.plugin.jar.types.web.core
  (:require
   [clojure.edn :as edn]
   [clojure.java.classpath :as classpath]
   [clojure.string :as string]
   [cmr.plugin.jar.core :as plugin])
 (:import
  (java.util.jar JarFile)))

(defn resolve-route
  [route]
  (let [[namesp fun] (mapv symbol (string/split (str route) #"/"))]
    (require namesp)
    (var-get (ns-resolve namesp fun))))

(defn plugin-routes
  [^JarFile jarfile in-jar-filepath route-keys]
  (let [data (get-in (plugin/config-data jarfile in-jar-filepath) route-keys)]
    [(:api data) (:site data)]))

(defn plugins-routes
  [jarfiles in-jar-filepath route-keys]
  (let [data (map #(plugin-routes % in-jar-filepath route-keys) jarfiles)]
    {:api (mapv first data)
     :site (mapv second data)}))

(defn assemble-routes
  ([^String plugin-name ^String plugin-type in-jar-filepath route-keys]
    (assemble-routes (plugin/jarfiles plugin-name plugin-type)
                     plugin-name
                     plugin-type
                     in-jar-filepath
                     route-keys))
  ([jarfiles ^String plugin-name ^String plugin-type in-jar-filepath route-keys]
    (let [data (plugins-routes jarfiles in-jar-filepath route-keys)]
      {:api (apply concat (map resolve-route (:api data)))
       :site (apply concat (map resolve-route (:site data)))})))
