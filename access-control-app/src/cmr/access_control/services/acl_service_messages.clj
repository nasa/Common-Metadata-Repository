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

(defn provider-does-not-exist
  [provider-id]
  (format "Provider with provider-id [%s] does not exist." provider-id))

(def token-required-for-acl-modification
  "ACLs cannot be modified without a valid user token.")
