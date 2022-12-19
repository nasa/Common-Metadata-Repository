(ns cmr.access-control.services.acl-service-messages
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [cmr.common.util :as util]))

(defn acl-does-not-exist
  [concept-id]
  (format "ACL could not be found with concept id [%s]" (util/html-escape concept-id)))

(defn bad-acl-concept-id
  [concept-id]
  (format "[%s] is not a valid ACL concept id." (util/html-escape concept-id)))

(defn acl-deleted
  [concept-id]
  (format "ACL with concept id [%s] was deleted." (util/html-escape concept-id)))
