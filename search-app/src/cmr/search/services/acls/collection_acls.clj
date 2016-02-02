(ns cmr.search.services.acls.collection-acls
  "Contains functions for manipulating collection acls"
  (:require [cmr.common-app.services.search.query-model :as qm]
            [cmr.common-app.services.search.group-query-conditions :as gc]
            [cmr.common-app.services.search.query-execution :as qe]
            [cmr.search.services.acls.acl-helper :as acl-helper]
            [cmr.acl.umm-matchers :as umm-matchers]))


(defmethod qe/add-acl-conditions-to-query :collection
  [context query]
  (let [group-ids (map #(if (keyword? %) (name %) %) (acl-helper/context->sids context))
        acl-cond (qm/string-conditions :permitted-group-ids group-ids true)]
    (update-in query [:condition] #(gc/and-conds [acl-cond %]))))


(defmethod acl-service/acls-match-concept? :collection
  [context acls concept]
  (some #(umm-matchers/coll-applicable-acl? (:provider-id concept) concept %) acls))