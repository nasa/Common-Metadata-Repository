(ns cmr.search.services.acls.collection-acls
  "Contains functions for manipulating collection acls"
  (:require
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common.util :as util]
   [cmr.search.services.acl-service :as acl-service]
   [cmr.transmit.config :as tc]
   [cmr.umm-spec.acl-matchers :as umm-matchers]
   [cmr.common.log :refer (debug)]))

(defmethod qe/add-acl-conditions-to-query :collection
  [context query]
  ;; return unmodified query if the context has a system token
 ;;(debug "INSIDE qe/add-acl-conditions-to-query :collection")
  (if (tc/echo-system-token? context)
    query
    (let [group-ids (map #(if (keyword? %) (name %) %)
                         (util/lazy-get context :sids))
          acl-cond (qm/string-conditions :permitted-group-ids group-ids true)
          updated_query (update-in query [:condition] #(gc/and-conds [acl-cond %]))
          ;; _ (debug (str "query after adding acl conditions: " updated_query))
          ]
      updated_query)))


(defmethod acl-service/acls-match-concept? :collection
  [context acls concept]
  (debug "INSIDE acl-service/acls-match-concept? :collection")
  (some #(umm-matchers/coll-applicable-acl? (:provider-id concept) concept %) acls))
