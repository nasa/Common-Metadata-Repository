(ns cmr.search.services.acls.collection-acls
  "Contains functions for manipulating collection acls"
  (:require [cmr.search.models.query :as qm]
            [cmr.search.services.acl-service :as acl-service]
            [cmr.search.services.acls.acl-helper :as acl-helper]
            [cmr.acl.collection-matchers :as coll-matchers]))


(defmethod acl-service/add-acl-conditions-to-query :collection
  [context query]
  (let [group-ids (map #(if (keyword? %) (name %) %) (acl-helper/context->sids context))
        acl-cond (qm/string-conditions :permitted-group-ids group-ids true)]
    (update-in query [:condition] #(qm/and-conds [acl-cond %]))))


(defmulti extract-access-value
  "Extracts access value (aka. restriction flag) from the concept."
  (fn [concept]
    (:format concept)))

(defmethod extract-access-value "application/echo10+xml"
  [concept]
  (when-let [[_ restriction-flag-str] (re-matches #"(?s).*<RestrictionFlag>(.+)</RestrictionFlag>.*"
                                                  (:metadata concept))]
    (Double. ^String restriction-flag-str)))

(defmethod extract-access-value "application/dif+xml"
  [concept]
  ;; DIF doesn't support restriction flag yet.
  nil)

(defmethod acl-service/acls-match-concept? :collection
  [acls concept]
  (let [;; Create a equivalent umm collection that will work with collection matchers.
        coll {:entry-title (get-in concept [:extra-fields :entry-title])
              :access-value (extract-access-value concept)}]
    (some #(coll-matchers/coll-applicable-acl? (:provider-id concept) coll % [:collections]) acls)))