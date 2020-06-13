(ns cmr.search.services.acls.subscription-acls
  "Contains functions for manipulating subscription acls"
  (:require
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common.util :as util]
   [cmr.search.services.acls.acl-helper :as acl-helper]
   [cmr.transmit.config :as tc]))

(defmethod qe/add-acl-conditions-to-query :subscription
  [context query]
  ;; return unmodified query if the context has a system token
  ;; otherwise, get the provider-ids from the EMAIL_SUBSCRIPTION_MANAGEMENT ACLs
  ;; that grant the read permission to the current user, and add provider-id
  ;; constraint to the query.
  (if (tc/echo-system-token? context)
    query
    (let [;; esm-acls that grant read permission to the current user
          _ (println "sids are: " (util/lazy-get context :sids))
          esm-acls (acl-helper/get-esm-acls-applicable-to-token context)
          _ (println "esm-acls with read permission to the current user: " esm-acls)
          provider-ids (map #(get-in % [:provider-identity :provider-id]) esm-acls)
          _ (println "provider-ids are: " provider-ids)
          acl-cond (qm/string-conditions :provider-id provider-ids true)]
      (update-in query [:condition] #(gc/and-conds [acl-cond %])))))
