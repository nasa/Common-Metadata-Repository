(ns cmr.access-control.services.acl-service-messages
  (:require [clojure.string :as str]
            [clojure.edn :as edn]))

(defn acl-does-not-exist
  [concept-id]
  (format "ACL could not be found with concept id [%s]" concept-id))

(defn bad-acl-concept-id
  [concept-id]
  (format "[%s] is not a valid ACL concept id." concept-id))

(defn acl-deleted
  [concept-id]
  (format "ACL with concept id [%s] was deleted." concept-id))
