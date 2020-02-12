(ns cmr.elastic-utils.es-index-helper
  "Defines helper functions for invoking ES index"
  (:require
   [cmr.common-app.config :as common-config]
   [clojurewerkz.elastisch.rest.index :as esi]))

(defmulti exists?
  "Used to check if the index (indices) exists or not."
  (fn [conn index-name]
    (common-config/index-es-engine-key)))

(defn- old-exists?
  [conn index-name]
  (esi/exists? conn index-name))

(defn- new-exists?
  [conn index-name]
  ;; TODO: add implementation
  )

(defmethod exists? :old
  [conn index-name]
  (old-exists? conn index-name))

(defmethod exists? :new
  [conn index-name]
  (new-exists? conn index-name))

(defmethod exists? :both
  [conn index-name]
  (let [{old-conn :old
         new-conn :new} conn]
    (and (old-exists? conn index-name)
         (new-exists? conn index-name))))
