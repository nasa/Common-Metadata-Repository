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

(defmulti update-mapping
  "Register or modify specific mapping definition for a specific type."
  (fn [conn index-name-or-names type-name opts]
    (common-config/index-es-engine-key)))

(defmethod update-mapping :old
  [conn index-name-or-names type-name opts]
  (esi/update-mapping conn index-name-or-names type-name opts))

(defmethod update-mapping :new
  [conn index-name-or-names type-name opts]
  ;; TODO: add implementation
  )

(defmethod update-mapping :both
  [conn index-name-or-names type-name opts]
  ;; TODO: add implementation
  )

(defmulti create
  "Create an index"
  (fn [conn index-name opts]
    (common-config/index-es-engine-key)))

(defmethod create :old
  [conn index-name opts]
  (esi/create conn index-name opts))

(defmethod create :new
  [conn index-name opts]
  ;; TODO: add implementation
  )

(defmethod create :both
  [conn index-name opts]
  ;; TODO: add implementation
  )

(defmulti refresh
  "refresh an index"
  (fn [conn index-name]
    (common-config/index-es-engine-key)))

(defmethod refresh :old
  [conn index-name]
  (esi/refresh conn index-name))

(defmethod refresh :new
  [conn index-name]
  ;; TODO: add implementation
  )

(defmethod refresh :both
  [conn index-name]
  ;; TODO: add implementation
  )

(defmulti delete
  "delete an index"
  (fn [conn index-name]
    (common-config/index-es-engine-key)))

(defmethod delete :old
  [conn index-name]
  (esi/delete conn index-name))

(defmethod delete :new
  [conn index-name]
  ;; TODO: add implementation
  )

(defmethod delete :both
  [conn index-name]
  ;; TODO: add implementation
  )

(defmulti update-aliases
  "update index aliases"
  (fn [conn actions]
    (common-config/index-es-engine-key)))

(defmethod update-aliases :old
  [conn actions]
  (esi/update-aliases conn actions))

(defmethod update-aliases :new
  [conn actions]
  ;; TODO: add implementation
  )

(defmethod update-aliases :both
  [conn actions]
  ;; TODO: add implementation
  )
