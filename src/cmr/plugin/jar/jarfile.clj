(ns cmr.plugin.jar.jarfile
  "Clojure-idiomatic wrappers for JAR-related methods."
 (:import
  (java.util.jar JarFile)
  (java.util.jar Attributes$Name)
  (java.util.zip ZipEntry))
 (:refer-clojure :exclude [name read]))

(defn manifest-obj
  [^JarFile this]
  (.getManifest this))

(defn manifest-attrs
  [^JarFile this]
  (when-let [mo (manifest-obj this)]
    (.getMainAttributes mo)))

(defn attr-key
  [key-name]
  (new Attributes$Name (if (keyword? key-name)
                         (clojure.core/name key-name)
                         key-name)))

(defn manifest
  [^JarFile this]
  (->> (manifest-attrs this)
       (map (fn [o]
              [(str (.getKey o)) (.getValue o)]))
       (into {})))

(defn manifest-has-key?
  [^JarFile this key-name]
  (when-let [m (manifest this)]
    (contains? (set (keys m)) key-name)))

(defn manifest-value
  [^JarFile this key-name]
  (when-let [m (manifest this)]
    (get m key-name)))

(defn name
  [^JarFile this]
  (.getName this))

(defn entry
  [^JarFile this]
  (.getEntry this))

(defn entry
  [^JarFile this in-jar-filepath]
  (.getEntry this in-jar-filepath))

(defn input-stream
  [^JarFile this ^ZipEntry entry]
  (.getInputStream this entry))

(defn read
  [^JarFile this ^String in-jar-filepath]
  (doall
    (->> in-jar-filepath
         (entry this)
         (input-stream this)
         slurp)))
