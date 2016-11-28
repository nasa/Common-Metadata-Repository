(ns cmr.access-control.data.acls
  "Misc. functions for working with ACL structures."
  (:require
    [cmr.common.concepts :as concepts]))

(defn acl->provider-id
  "Returns the provider id which the acl explicitly targets. Returns nil for system objects."
  [{:keys [catalog-item-identity provider-identity single-instance-identity]}]
  (cond
    catalog-item-identity (:provider-id catalog-item-identity)
    provider-identity (:provider-id provider-identity)
    single-instance-identity (let [provider-id (:provider-id
                                                 (concepts/parse-concept-id
                                                   (:target-id single-instance-identity)))]
                               (when-not (= "CMR" provider-id)
                                 provider-id))))
