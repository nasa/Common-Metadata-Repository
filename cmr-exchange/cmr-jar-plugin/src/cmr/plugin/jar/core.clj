(ns cmr.plugin.jar.core
  (:require
   [clojure.edn :as edn]
   [cmr.plugin.jar.jarfile :as jarfile]
   [taoensso.timbre :as log])
 (:import
  (java.util.jar JarFile)
  (java.util.jar Attributes$Name)))

(defn tagged-jars
  "Generate a collection of `JarFile` maps, each with a `:file` and `:object`
  key for easy readability and use."
  [jarfiles plugin-name plugin-type]
  (mapv #(hash-map :file (jarfile/name %)
                   :plugin-name (first (jarfile/matched-manifest-keys % plugin-name))
                   :plugin-type (first (jarfile/matched-manifest-values % plugin-name plugin-type))
                   :object %)
        jarfiles))

(defn no-manifest-reducer
  "This reducer will generate a collection of JAR files that have no MANIFEST file.
  Primarily useful for debugging/curiosity."
  [acc ^JarFile jar]
  (conj acc
        (when-not (jarfile/manifest jar)
          jar)))

(defn has-manifest-reducer
  "This is a reducer that will generate a collection of JAR files that
  have a MANIFEST file."
  [acc ^JarFile jar]
  (conj acc
        (when (jarfile/manifest jar)
          jar)))

(defn create-has-plugin-name-reducer
  "This creates a reducer that will generate a collection of JAR files that
  have a MANIFEST file and that also have a key in the MANIFEST file which
  exactly matches the given plugin name."
  [plugin-name _]
  (fn [acc ^JarFile jar]
    (conj acc
          (when (jarfile/manifest-has-key? jar plugin-name)
            jar))))

(defn create-has-plugin-type-reducer
  "This creates a reducer that will generate a collection of JAR files that
  have a MANIFEST file and that also have a key in the MANIFEST file which
  exactly matches both the given plugin name as well as the plugin type."
  [plugin-name plugin-type]
  (fn [acc ^JarFile jar]
    (conj acc
          (when (jarfile/manifest-has-value? jar plugin-name plugin-type)
            jar))))

(defn create-regex-plugin-name-reducer
  "This creates a reducer that will generate a collection of JAR files that
  have a MANIFEST file and that also have a key in the MANIFEST file which
  matches the given plugin name (possibly a regular expression)."
  [plugin-name _]
  (fn [acc ^JarFile jar]
    (conj acc
          (when (jarfile/matches-manifest-key? jar plugin-name)
            jar))))

(defn create-regex-plugin-type-reducer
  "This creates a reducer that will generate a collection of JAR files that
  have a MANIFEST file and that:
  1) have a key in the MANIFEST file which exactly matches the given plugin
     name, and
  2) have a value for the plugin key that matches the configured plugin type,
     where the plugin type is possibly a regular expression."
  [plugin-name plugin-type]
  (fn [acc ^JarFile jar]
    (conj acc
          (when-let [p-type (jarfile/manifest-value jar plugin-name)]
            (when (re-matches (re-pattern plugin-type) p-type)
              jar)))))

(defn create-regex-plugin-reducer
  "This creates a reducer that will generate a collection of JAR files that
  have a MANIFEST file and that:
  1) have a key in the MANIFEST file which regex-matches (Clojure-style regex
     string) the configured plugin name, and
  2) have a value for the plugin key which regex-matches (Clojure-style regex
     string) the configured plugin type."
  [plugin-name plugin-type]
  (fn [acc ^JarFile jar]
    (conj acc
          (when (jarfile/matches-manifest-value? jar plugin-name plugin-type)
            jar))))

(defn config-data
  "Extract the EDN configuration data stored in a jarfile at the given location
  in the JAR."
  [^JarFile jar in-jar-filepath]
  (->> in-jar-filepath
       (jarfile/read jar)
       edn/read-string))

(defn jarfiles
  "Given a plugin name (MANIFEST file entry), plugin type (the MANIFEST file
  entry's value), and a reducer-factory function, return all the JAR files
  that are accumulated by the redcuer."
  [^String plugin-name ^String plugin-type reducer-factory]
  (->> (jarfile/all-jars)
       (reduce (reducer-factory plugin-name plugin-type)
               [])
       (remove nil?)))
