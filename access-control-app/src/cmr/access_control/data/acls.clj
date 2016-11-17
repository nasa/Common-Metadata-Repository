(ns cmr.access-control.data.acls
  "Misc. functions for working with ACL structures.")

;; TODO Does it make sense to have a namespace with one function?


;; TODO if the ACL is a single instance identity then you should parse the target id which has the concept id
;; If it's CMR don't return it otherwise return that.
(defn acl->provider-id
  "Returns the provider id which the acl explicitly targets, if any."
  [acl]
  (or (:provider-id (:catalog-item-identity acl))
      (:provider-id (:provider-identity acl))))
