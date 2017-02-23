(ns cmr.search.services.acls.collection-acls
  "Contains functions for manipulating collection acls"
  (:require
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common.util :as util]
   [cmr.search.api.request-context-user-augmenter :as context-augmenter]
   [cmr.search.services.acl-service :as acl-service]
   [cmr.umm.acl-matchers :as umm-matchers]))

(defmethod qe/add-acl-conditions-to-query :collection
  [context query]
  (let [group-ids (map #(if (keyword? %) (name %) %)
                   (util/lazy-get context :sids))
        acl-cond (qm/string-conditions :permitted-group-ids group-ids true)]
    (update-in query [:condition] #(gc/and-conds [acl-cond %]))))


(defmethod acl-service/acls-match-concept? :collection
  [context acls concept]
  (some #(umm-matchers/coll-applicable-acl? (:provider-id concept) concept %) acls))
