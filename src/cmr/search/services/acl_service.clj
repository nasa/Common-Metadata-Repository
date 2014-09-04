(ns cmr.search.services.acl-service
  "Performs ACL related tasks for the search application"
  (:require [cmr.search.services.acls.acl-helper :as acl-helper]))

(defmulti add-acl-conditions-to-query
  "Adds conditions to the query to enforce ACLs."
  (fn [context query]
    (:concept-type query)))

(defmulti acls-match-concept?
  "Returns true if any of the acls match the concept."
  (fn [acls concept]
    (:concept-type concept)))

(defn filter-concepts
  "Filters out the concepts that the current user does not have access to. Concepts are the maps
  of concept metadata as returned by the metadata db."
  [context concepts]
  (let [acls (acl-helper/get-acls-applicable-to-token context)
        coll-acls (filter (comp :collection-applicable :catalog-item-identity) acls)]
    ;; This assumes collection concepts for now.
    (filter (partial acls-match-concept? coll-acls) concepts)))


