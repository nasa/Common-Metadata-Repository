(ns cmr.search.services.acls.collection-acls
  "Contains functions for manipulating collection acls"
  (:require
   [cmr.elastic-utils.search.es-group-query-conditions :as gc]
   [cmr.elastic-utils.search.query-execution :as qe]
   [cmr.common.services.search.query-model :as qm]
   [cmr.common.util :as util]
   [cmr.search.services.acl-service :as acl-service]
   [cmr.transmit.config :as tc]
   [cmr.umm-spec.acl-matchers :as umm-matchers]))

(defmethod qe/add-acl-conditions-to-query :collection
  [context query]
  (println "INSIDE qe/add-acl-conditions-to-query :collection")
  ;; return unmodified query if the context has a system token
  (if (tc/echo-system-token? context)
    query
    (let [group-ids (map #(if (keyword? %) (name %) %)
                         (util/lazy-get context :sids))
          acl-cond (qm/string-conditions :permitted-group-ids group-ids true)]
      (println "group ids = " group-ids " and acl-cond = " acl-cond)
      (update-in query [:condition] #(gc/and-conds [acl-cond %])))))


(defmethod acl-service/acls-match-concept? :collection
  [context acls concept]
  (some #(umm-matchers/coll-applicable-acl? (:provider-id concept) concept %) acls))
