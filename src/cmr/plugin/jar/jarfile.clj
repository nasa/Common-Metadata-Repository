(ns cmr.plugin.jar.jarfile
  "Clojure-idiomatic wrappers for JAR-related methods."
 (:require
  [clojure.java.classpath :as classpath]
  [cmr.plugin.jar.util :as util]
  [taoensso.timbre :as log])
 (:import
  (java.util.jar JarFile)
  (java.util.jar Attributes$Name)
  (java.util.zip ZipEntry))
 (:refer-clojure :exclude [name read]))

(defn all-jars
  []
  (classpath/classpath-jarfiles))

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

(defn manifest-has-value?
  [^JarFile this key-name key-value]
  (and (manifest-has-key? this key-name)
       (= key-value (manifest-value this key-name))))

(defn matches-manifest-key?
  [^JarFile this key-pattern]
  (when-let [m (manifest this)]
    (util/matches-key? m key-pattern)))

(defn matches-manifest-value?
  [^JarFile this key-pattern value-pattern]
  (when-let [m (manifest this)]
    (and (util/matches-key? m key-pattern)
         (util/matches-val? m value-pattern))))

(defn matched-manifest-keys
  [^JarFile this key-pattern]
  (when-let [m (manifest this)]
    (util/matched-keys m key-pattern)))

(defn matched-manifest-values
  [^JarFile this key-pattern value-pattern]
  (when-let [m (manifest this)]
    (util/matched-vals m key-pattern value-pattern)))

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
  (try
    (when-let [e (entry this in-jar-filepath)]
      (when-let [s (input-stream this e)]
        (slurp s)))
    (catch Exception _
      (log/error "Error reading " (name this)))))
