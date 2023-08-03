(ns cmr.search.services.acls.draft-acls
  "Contains functions for manipulating draft acls"
  (:require
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common.concepts :as cc]
   [cmr.search.services.acls.acl-helper :as acl-helper]
   [cmr.transmit.config :as tc]))

(doseq [concept-type (cc/get-generic-concept-types-array)]
  (when (cc/is-draft-concept? concept-type)
    (defmethod qe/add-acl-conditions-to-query concept-type
      [context query]
      ;; return unmodified query if the context has a system token
      ;; otherwise, get the provider-ids from the PROVIDER_CONTEXT ACLS
      ;; that grant the read permission to the current user.
      (if (tc/echo-system-token? context)
        query
        (let [pc-acls (acl-helper/get-pc-acls-applicable-to-token context)
              provider-ids (if (seq pc-acls)
                             (map #(get-in % [:provider-identity :provider-id]) pc-acls)
                             ["non-existing-provider-id"])
              acl-cond (qm/string-conditions :provider-id provider-ids true)]
          (update-in query [:condition] #(gc/and-conds [acl-cond %])))))))  
