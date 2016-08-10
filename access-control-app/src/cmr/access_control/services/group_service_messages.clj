(ns cmr.access-control.services.group-service-messages
  (:require [clojure.string :as str]
            [clojure.edn :as edn]))

(defn- get-group-name-from-concept
  "Gets a group name from a access-group concept's metadata.
   This is because we lowercase the native-id so it does not match the actual group name."
  [concept]
  (-> concept :metadata edn/read-string :name))

(defn group-already-exists
  [group concept]
  (if (:provider-id group)
    (format "A provider group with name [%s] already exists with concept id [%s] for provider [%s]."
            (get-group-name-from-concept concept) (:concept-id concept) (:provider-id group))
    (format "A system group with name [%s] already exists with concept id [%s]."
            (get-group-name-from-concept concept) (:concept-id concept))))

(defn group-does-not-exist
  [concept-id]
  (format "Group could not be found with concept id [%s]" concept-id))

(defn bad-group-concept-id
  [concept-id]
  (format "[%s] is not a valid group concept id." concept-id))

(defn group-deleted
  [concept-id]
  (format "Group with concept id [%s] was deleted." concept-id))
