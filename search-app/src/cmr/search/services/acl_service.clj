(ns cmr.search.services.acl-service
  "Performs ACL related tasks for the search application"
  (:require
    [cmr.search.services.acls.acl-helper :as acl-helper]
    [cmr.transmit.config :as tc]))

(defmulti acls-match-concept?
  "Returns true if any of the acls match the concept.

  Implementations for this multimethod should be placed in the namespaces for the
  respective concept types under `cmr.search.services.acl-service.TYPE-acls`. If
  there is no namespace for the type, the multimethod can be implemented below."
  (fn [context acls concept]
    (:concept-type concept)))

;; tags have no acls so we always assume it matches
(defmethod acls-match-concept? :tag
  [context acls concept]
  true)

;; When plans solidify around acl checks for variable concepts, we'll update this.
(defmethod acls-match-concept? :variable
  [context acls concept]
  true)

(defmethod acls-match-concept? :default
  [context acls concept]
  false)

(def concept-type->applicable-field
  "A mapping of concept type to the field in the ACL indicating if it is collection or granule
  applicable."
  {:granule :granule-applicable
   :collection :collection-applicable
   :variable :variable-applicable})

(defn filter-concepts
  "Filters out the concepts that the current user does not have access to. Concepts are the maps
  of concept metadata as returned by the metadata db.

  The following fields are required for each concept, depending on type:

  Granules:
  * :concept-type
  * :provider-id
  * :access-value
  * :collection-concept-id

  Collections:
  * :concept-type
  * :provider-id
  * :access-value
  * :entry-title

  Variables:
  * :concept-type
  * :provider-id"
  [context concepts]
  (when (seq concepts)
    (if (tc/echo-system-token? context)
      ;;return all concepts if running with the system token
      concepts
      (let [acls (acl-helper/get-acls-applicable-to-token context)
            applicable-field (-> concepts first :concept-type concept-type->applicable-field)
            applicable-acls (filterv (comp applicable-field :catalog-item-identity) acls)]
        (doall (remove nil? (pmap (fn [concept]
                                    (when (acls-match-concept? context applicable-acls concept)
                                      concept))
                                  concepts)))))))
