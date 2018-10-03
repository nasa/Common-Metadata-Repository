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
  (map #(util/resolve-fully-qualified-fn %) routes-symbols))

(defn plugin-routes
  [^JarFile jarfile in-jar-filepath route-keys ^Keyword api-key ^Keyword site-key]
  (let [data (get-in (plugin/config-data jarfile in-jar-filepath) route-keys)]
    [(api-key data) (site-key data)]))

(defn plugins-routes
  [jarfiles in-jar-filepath route-keys ^Keyword api-key ^Keyword site-key]
  (let [data (map #(plugin-routes % in-jar-filepath route-keys api-key site-key)
                  jarfiles)]
    {api-key (vec (remove nil? (map first data)))
     site-key (vec (remove nil? (map second data)))}))

(defn assemble-routes-fns
  ([^String plugin-name ^String plugin-type in-jar-filepath route-keys
    ^Keyword api-key ^Keyword site-key]
    (assemble-routes-fns (plugin/jarfiles plugin-name plugin-type)
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
       api-key #(resolve-routes (api-key data))
       site-key #(resolve-routes (site-key data))})))
