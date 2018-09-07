(ns cmr.plugin.jar.core
  (:require
   [clojure.edn :as edn]
   [clojure.java.classpath :as classpath]
   [cmr.plugin.jar.util :refer [when-let*]]
   [taoensso.timbre :as log])
 (:import
  (java.util.jar JarFile)
  (java.util.jar Attributes$Name)))

(defn named-jars
  "Generate a collection of `JarFile` maps, each with a `:file` and `:object`
  key for easy readability and use."
  [jarfiles]
  (mapv #(hash-map :file (.getName %) :object %) jarfiles))

(defn create-has-manifest-reducer
  "This reducer will generate a collection of JAR files that have a MANIFEST file."
  [plugin-name plugin-type]
  (fn [acc x]
    (conj acc
          (when-let* [m (.getManifest x)]
            x))))

(defn create-plugin-name-reducer
  [plugin-name plugin-type]
  (fn [acc x]
    (conj acc
          (when-let* [m (.getManifest x)
                      p (.getMainAttributes m)]
            (when (.containsKey p (new Attributes$Name plugin-name))
              x)))))

(defn create-plugin-type-reducer
  [plugin-name plugin-type]
  (fn [acc x]
    (conj acc
          (when-let* [m (.getManifest x)
                      p (.getMainAttributes m)
                      p-type (.getValue p plugin-name)]
            (when (re-matches (re-pattern plugin-type) p-type)
              x)))))

(defn config-data
  [^JarFile jarfile filepath]
  (doall
    (->> filepath
         (.getEntry jarfile)
         (.getInputStream jarfile)
         slurp
         edn/read-string)))

(defn jarfiles
  [^String plugin-name ^String plugin-type]
  (doall
    (->> (classpath/classpath-jarfiles)
         (reduce (create-plugin-type-reducer plugin-name plugin-type)
                 [])
         (remove nil?))))
