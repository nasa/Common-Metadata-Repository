(ns cmr.search.services.acls.collection-acls
  "Contains functions for manipulating collection acls"
  (:require [cmr.search.models.query :as qm]
            [cmr.search.models.group-query-conditions :as gc]
            [cmr.search.services.acl-service :as acl-service]
            [cmr.search.services.acls.acl-helper :as acl-helper]
            [cmr.acl.collection-matchers :as coll-matchers]))


(defmethod acl-service/add-acl-conditions-to-query :collection
  [context query]
  (let [group-ids (map #(if (keyword? %) (name %) %) (acl-helper/context->sids context))
        acl-cond (qm/string-conditions :permitted-group-ids group-ids true)]
    (update-in query [:condition] #(gc/and-conds [acl-cond %]))))


(defmethod acl-service/acls-match-concept? :collection
  [context acls concept]
  (some (partial coll-matchers/coll-applicable-acl? (:provider-id concept) concept) acls))