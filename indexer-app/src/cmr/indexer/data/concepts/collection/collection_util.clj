(ns cmr.indexer.data.concepts.collection.collection-util
  "Contains util functions for collection indexing"
  (:require
   [cmr.acl.acl-fetcher :as acl-fetcher]
   [cmr.umm-spec.acl-matchers :as umm-matchers]))

(defn parse-version-id
  "Safely parse the version-id to an integer and then return as a string. This
  is so that collections with the same short name and differently formatted
  versions (i.e. 1 vs. 001) can be more accurately sorted. If the version
  cannot be parsed to an integer, return the original version-id"
  [version-id]
  (try
    (str (Integer/parseInt version-id))
    (catch Exception _ version-id)))

(defn get-coll-permitted-group-ids
  "Returns the groups ids (group guids, 'guest', 'registered') that have permission to read
  this collection"
  [context provider-id coll]
  (->> (acl-fetcher/get-acls context [:catalog-item])
       ;; Find only acls that are applicable to this collection
       (filter (partial umm-matchers/coll-applicable-acl? provider-id coll))
       ;; Get the permissions they grant
       (mapcat :group-permissions)
       ;; Find permissions that grant read
       (filter #(some (partial = "read") (:permissions %)))
       ;; Get the group guids or user type of those permissions
       (map #(or (:group-id %) (some-> % :user-type name)))
       distinct))
