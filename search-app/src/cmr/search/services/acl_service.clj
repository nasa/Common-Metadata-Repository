(ns cmr.search.services.acl-service
  "Performs ACL related tasks for the search application"
  (:require
   [cmr.common.api.context :as context-util]
   [cmr.common.concepts :as cc]
   [cmr.search.services.acls.acl-helper :as acl-helper]
   [cmr.transmit.config :as tc]))

(defmulti acls-match-concept?
  "Returns true if any of the catalog item acls match the concept.

  Implementations for this multimethod should be placed in the namespaces for the
  respective concept types under `cmr.search.services.acl-service.TYPE-acls`. If
  there is no namespace for the type, the multimethod can be implemented below."
  (fn [context acls concept]
    (:concept-type concept)))

;; services currently have no catalog item ACLs, so return `true` for all ACL checks
(defmethod acls-match-concept? :service
  [context acls concept]
  true)

;; tools currently have no catalog item ACLs, so return `true` for all ACL checks
(defmethod acls-match-concept? :tool
  [context acls concept]
  true)

;; tags have no catalog item acls so we always assume it matches
(defmethod acls-match-concept? :tag
  [context acls concept]
  true)

;; variables currently have no catalog item ACLs, so return `true` for all ACL checks
(defmethod acls-match-concept? :variable
  [context acls concept]
  true)

;; generic concepts currently have no catalog item ACLs, so return `true` for all ACL checks
;; except for draft concepts which checks provider object ACL PROVIDER_CONTEXT.
(doseq [concept-type (cc/get-generic-concept-types-array)]
  (if (cc/is-draft-concept? concept-type)
    (defmethod acls-match-concept? concept-type
      [context acls concept]
      (let [pc-acls (acl-helper/get-pc-acls-applicable-to-token context)]
        (some #(= (:provider-id concept) (get-in % [:provider-identity :provider-id])) pc-acls)))
    (defmethod acls-match-concept? concept-type
      [context acls concept]
      true)))

;; subscriptions checks provider object ACLs, not catalog item ACLs
(defmethod acls-match-concept? :subscription
  [context acls concept]
  (let [sm-acls (acl-helper/get-sm-acls-applicable-to-token context)
        user-id (when (:token context)
                  (context-util/context->user-id context))
        subscriber-id (get-in concept [:extra-fields :subscriber-id])]
    (or (some #(= (:provider-id concept) (get-in % [:provider-identity :provider-id])) sm-acls)
        (= subscriber-id user-id))))

(defmethod acls-match-concept? :default
  [context acls concept]
  false)

;; XXX To be fixed with CMR-4394
;;
;;     See also:
;;       * https://wiki.earthdata.nasa.gov/display/CMR/Towards+Improved+CMR+Concept+Creation
;;       * https://wiki.earthdata.nasa.gov/display/CMR/CMR+Concept+Creation+API
;;
;;     This code is a point of contention for adding new concepts: on both
;;     occasions where we added varible and service support (e.g. to the
;;     `/search/concepts` route), tests failed with ACL errors due to this ns
;;     not getting updated, both in the function below as well as above in the
;;     `acls-match-concept?` multimethod.
;;
;;     This needs to be taken into account when we plan for refactoring the
;;     APIs and development process for adding concepts to the CMR. The fact
;;     that this crucial change is tucked away in this part of the code with no
;;     logical "pointers" or hints to it anywhere else is deeply problematic ...
;;     programming by conventions is a slow path to developer insanity and
;;     software project failure! Instead, all parts of the code that need to be
;;     touched should be replaced with APIs (e.g., protocols and their impl'ns)
;;     with each method documented and, all together, being the only things a
;;     developer needs to add/change (in this instance) a new concept.
(def concept-type->applicable-field
  "A mapping of concept type to the field in the ACL indicating if it is collection or granule
  applicable."
  (merge
   {:granule :granule-applicable
    :collection :collection-applicable
    :service :service-applicable
    :tool :tool-applicable
    :variable :variable-applicable
    :subscription :subscription-applicable}
   (zipmap (cc/get-generic-concept-types-array) (map #(keyword (format "%s-applicable" (name %))) 
                                                     (cc/get-generic-concept-types-array)))))

;;TODO STEP 9
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
 (println "INSIDE filter concepts")
  (when (seq concepts)
    (if (tc/echo-system-token? context)
      ;;return all concepts if running with the system token
      (do
       (println "INSIDE filter concepts: FOUND ECHO SYSTEM TOKEN but re-routing.")
       (println "context = " (pr-str context))
       (let [acls (acl-helper/get-acls-applicable-to-token context)
             ;; drafts don't contain concept-type field, so we will need to derive it from concept-id
             ;; collections don't contain concept_id or concept-id fields, but they do contain concept-type field.
             concept-type (-> concepts first :concept-type)
             concept-type (if concept-type
                           concept-type
                           (-> concepts first :concept_id cc/concept-id->type))
             applicable-field (concept-type->applicable-field concept-type)
             ;; Note: This applicable-acls is only used for collections and granules acl matching.
             ;; For other concept types, it is not being used.
             applicable-acls (filterv (comp applicable-field :catalog-item-identity) acls)]
        (doall (remove nil? (pmap (fn [concept]
                                   (when (acls-match-concept? context applicable-acls concept)
                                    concept))
                                  concepts)))))
      (let [_ (println "INSIDE filter concepts: did not find system token")
            acls (acl-helper/get-acls-applicable-to-token context)
            _ (println "inside filter concepts, acls = " (pr-str acls))
            ;; drafts don't contain concept-type field, so we will need to derive it from concept-id
            ;; collections don't contain concept_id or concept-id fields, but they do contain concept-type field.
            concept-type (-> concepts first :concept-type)
            concept-type (if concept-type
                           concept-type
                           (-> concepts first :concept_id cc/concept-id->type))
            applicable-field (concept-type->applicable-field concept-type)
            ;; Note: This applicable-acls is only used for collections and granules acl matching.
            ;; For other concept types, it is not being used.
            applicable-acls (filterv (comp applicable-field :catalog-item-identity) acls)
            _ (println" Inside filter concepts: applicable-acls = " (pr-str applicable-acls))]
        (doall (remove nil? (pmap (fn [concept]
                                    (when (acls-match-concept? context applicable-acls concept)
                                      concept))
                                  concepts)))))))
