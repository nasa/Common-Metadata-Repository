(ns cmr.search.services.acls.subscription-acls
  "Contains functions for manipulating subscription acls"
  (:require
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common.api.context :as context-util]
   [cmr.common.util :as util]
   [cmr.search.services.acls.acl-helper :as acl-helper]
   [cmr.transmit.config :as tc]))

(defmethod qe/add-acl-conditions-to-query :subscription
  [context query]
  ;; return unmodified query if the context has a system token
  ;; otherwise, get the provider-ids from the SUBSCRIPTION_MANAGEMENT ACLs
  ;; that grant the read permission to the current user, or if the user is subject
  ;; of the subcription
  (if (tc/echo-system-token? context)
    query
    (let [;; sm-acls that grant read permission to the current user
          sm-acls (acl-helper/get-sm-acls-applicable-to-token context)
          subscriber-cond (if (:token context)
                            (qm/string-condition :subscriber-id
                                                 (context-util/context->user-id context))
                            qm/match-none)
          provider-ids (if (seq sm-acls)
                         (map #(get-in % [:provider-identity :provider-id]) sm-acls)
                         ["non-existing-provider-id"])
          provider-ids (if (acl-helper/has-system-read-permission? context)
                         (concat provider-ids ["CMR"])
                         provider-ids)
          acl-cond (gc/or-conds [(qm/string-conditions :provider-id provider-ids true)
                                 subscriber-cond])]
      (update-in query [:condition] #(gc/and-conds [acl-cond %])))))
