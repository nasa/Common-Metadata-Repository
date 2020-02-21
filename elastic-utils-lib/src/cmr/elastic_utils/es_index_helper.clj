(ns cmr.elastic-utils.es-index-helper
  "Defines helper functions for invoking ES index"
  (:require
   [clojurewerkz.elastisch.rest.index :as esi]))

(defn exists?
  [conn index-name]
  (esi/exists? conn index-name))

(defn update-mapping
  "Register or modify specific mapping definition"
  [conn index-name-or-names type-name opts]
  (esi/update-mapping conn index-name-or-names type-name opts))

(defn create
  "Create an index"
  [conn index-name opts]
  (esi/create conn index-name opts))

(defn refresh
 "refresh an index"
  [conn index-name]
  (esi/refresh conn index-name))

(defn delete
  "delete an index"
  [conn index-name]
  (esi/delete conn index-name))

(defn update-aliases
  "update index aliases"
  [conn actions]
  (esi/update-aliases conn actions))
