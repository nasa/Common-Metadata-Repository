(ns cmr.search.services.acl-service
  "Performs ACL related tasks for the search application"
  (:require [cmr.search.services.acls.acl-helper :as acl-helper]))

(defmulti acls-match-concept?
  "Returns true if any of the acls match the concept."
  (fn [context acls concept]
    (:concept-type concept)))

(def concept-type->applicable-field
  "A mapping of concept type to the field in the ACL indicating if it is collection or granule applicable."
  {:granule :granule-applicable
   :collection :collection-applicable})

(defn filter-concepts
  "Filters out the concepts that the current user does not have access to. Concepts are the maps
  of concept metadata as returned by the metadata db. The following fields are required for each
  concept depending on type.
  Granules:
  * :concept-type
  * :provider-id
  * :access-value
  * :collection-concept-id
  Collections:
  * :concept-type
  * :provider-id
  * :access-value
  * :entry-title"
  [context concepts]
  (when (seq concepts)
    (let [acls (acl-helper/get-acls-applicable-to-token context)
          applicable-field (-> concepts first :concept-type concept-type->applicable-field)
          applicable-acls (filterv (comp applicable-field :catalog-item-identity) acls)]
      (doall (remove nil? (pmap (fn [concept]
                                  (when (acls-match-concept? context applicable-acls concept)
                                    concept))
                                concepts))))))


