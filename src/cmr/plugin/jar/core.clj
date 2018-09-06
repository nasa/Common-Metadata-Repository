(ns cmr.plugin.jar.core
  (:require
   [clojure.edn :as edn]
   [clojure.java.classpath :as classpath]
   [cmr.plugin.jar.util :refer [when-let*]])
 (:import
  (java.util.jar JarFile)))

(defn create-jarfile-reducer
  [plugin-name plugin-type]
  (fn [acc x]
    (conj acc
          (when-let* [m (.getManifest x)
                      p (.getMainAttributes m)
                      p-type (.getValue p plugin-name)]
            (when (= p-type plugin-type)
              x)))))

(defn config-data
  [^JarFile jarfile filepath]
  (->> filepath
       (.getEntry jarfile)
       (.getInputStream jarfile)
       slurp
       edn/read-string))

(defn jarfiles
  [^String plugin-name ^String plugin-type]
  (->> (classpath/classpath-jarfiles)
             (reduce (create-jarfile-reducer plugin-name plugin-type)
                     [])
             (remove nil?)))
