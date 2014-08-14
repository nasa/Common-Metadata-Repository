(ns cmr.acl.core
  "Contains code for retrieving and manipulating ACLs."
  (:require [cmr.common.services.errors :as errors]
            [cmr.acl.acl-cache :as acl-cache]
            [cmr.acl.collection-matchers :as cm]))



(defn get-coll-permitted-group-ids
  "Returns the groups ids (group guids, 'guest', 'registered') that have permission to read
  this collection"
  [context provider-id coll]

  (->> (acl-cache/get-acls context)
       ;; Find only acls that are applicable to this collection
       (filter (partial cm/coll-applicable-acl? provider-id coll))
       ;; Get the permissions they grant
       (mapcat :aces)
       ;; Find permissions that grant read
       (filter #(some (partial = :read) (:permissions %)))
       ;; Get the group guids or user type of those permissions
       (map #(or (:group-guid %) (some-> % :user-type name)))
       distinct))


(comment

  (require '[cmr.umm.collection :as umm-c])
  (def coll (umm-c/map->UmmCollection {:entry-title  "GHRSST Level 4 USA NAVOCEANO K10_SST Global SST:1"}))

  (acl-cache/get-acls {:system user/system})

  (get-coll-permitted-group-ids {:system user/system} "PODAAC" coll)




  )
