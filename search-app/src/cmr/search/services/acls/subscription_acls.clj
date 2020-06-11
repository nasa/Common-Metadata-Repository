(ns cmr.search.services.acls.subscription-acls
  "Contains functions for manipulating subscription acls"
  (:require
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common.util :as util]
   [cmr.search.services.acl-service :as acl-service]
   [cmr.transmit.config :as tc]))

(defmethod qe/add-acl-conditions-to-query :subscription
  [context query]
  ;; return unmodified query if the context has a system token
  (if (tc/echo-system-token? context)
    query
    (let [group-ids (map #(if (keyword? %) (name %) %)
                         (util/lazy-get context :sids))
          acl-cond (qm/string-conditions :permitted-group-ids group-ids true)]
      (update-in query [:condition] #(gc/and-conds [acl-cond %])))))

(defmethod acl-service/esm-acls-match-concept-provider? :subscription
  [esm-acls concept]
  ;; the esm-acls are all the EMAIL_SUBSCRIPTION_MANAGEMENT ACLs that
  ;; grant the current user read permissions. All we need to check is
  ;; if any of these read permissions are granted on the provider-id of the concept.
  (some #(= (:provider-id concept) (get-in % [:provider-identity :provider-id])) esm-acls))
