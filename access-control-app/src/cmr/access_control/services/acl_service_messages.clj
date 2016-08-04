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
  "Acls cannot be modified without a valid user token.")

(defn- get-acl-name-from-concept
  "Gets a acl name from a acl concept's metadata.
   This is because we lowercase the native-id so it does not match the actual group name."
  [concept]
  (-> concept :metadata edn/read-string :name))

(defn acl-already-exists
  [acl concept]
  (if (:provider-id acl)
    (format "A provider acl with name [%s] already exists with concept id [%s] for provider [%s]."
            (get-acl-name-from-concept concept) (:concept-id concept) (:provider-id acl))
    (format "A system acl with name [%s] already exists with concept id [%s]."
            (get-acl-name-from-concept concept) (:concept-id concept))))
