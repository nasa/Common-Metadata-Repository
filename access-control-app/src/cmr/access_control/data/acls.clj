(ns cmr.access-control.data.acls
  "Misc. functions for working with ACL structures.")

(defn acl->provider-id
  "Returns the provider id which the acl explicitly targets, if any."
  [acl]
  (or (:provider-id (:catalog-item-identity acl))
      (:provider-id (:provider-identity acl))))
