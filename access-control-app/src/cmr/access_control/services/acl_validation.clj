(ns cmr.access-control.services.acl-validation
  (:require [cmr.common.validations.core :as v]
            [cmr.common.date-time-parser :as dtp]
            [clj-time.core :as t]
            [cmr.transmit.metadata-db :as mdb1]
            [cmr.transmit.metadata-db2 :as mdb]
            [cmr.access-control.data.acls :as acls]
            [cmr.access-control.services.messages :as msg]))

(defn- catalog-item-identity-collection-applicable-validation
  "Validates the relationship between collection_applicable and collection_identifier."
  [key-path cat-item-id]
  (when (and (:collection-identifier cat-item-id)
             (not (:collection-applicable cat-item-id)))
    {key-path ["collection_applicable must be true when collection_identifier is specified"]}))

(defn- catalog-item-identity-granule-applicable-validation
  "Validates the relationship between granule_applicable and granule_identifier."
  [key-path cat-item-id]
  (when (and (:granule-identifier cat-item-id)
             (not (:granule-applicable cat-item-id)))
    {key-path ["granule_applicable must be true when granule_identifier is specified"]}))

(defn- catalog-item-identity-collection-or-granule-validation
  "Validates minimal catalog_item_identity fields."
  [key-path cat-item-id]
  (when-not (or (:collection-applicable cat-item-id)
                (:granule-applicable cat-item-id))
    {key-path ["when catalog_item_identity is specified, one or both of collection_applicable or granule_applicable must be true"]}))

(defn- make-collection-entry-titles-validation
  "Returns a validation for the entry_titles part of a collection identifier, closed over the context and ACL to be validated."
  [context acl]
  (let [provider-id (-> acl :catalog-item-identity :provider-id)]
    (v/every (fn [key-path entry-title]
               (when-not (seq (mdb1/find-concepts context {:provider-id provider-id :entry-title entry-title} :collection))
                 {key-path [(format "collection with entry-title [%s] does not exist in provider [%s]" entry-title provider-id)]})))))

(defn- access-value-validation
  "Validates the access_value part of a collection or granule identifier."
  [key-path access-value-map]
  (let [{:keys [min-value max-value include-undefined-value]} access-value-map]
    (cond
      (and include-undefined-value (or min-value max-value))
      {key-path ["min_value and/or max_value must not be specified if include_undefined_value is true"]}

      (and (not include-undefined-value) (not (or min-value max-value)))
      {key-path ["min_value and/or max_value must be specified when include_undefined_value is false"]})))

(defn temporal-identifier-validation
  "A validation for the temporal part of an ACL collection or granule identifier."
  [key-path temporal]
  (let [{:keys [start-date stop-date]} temporal]
    (when (and start-date stop-date
               (t/after? (dtp/parse-datetime start-date) (dtp/parse-datetime stop-date)))
      {key-path ["start_date must be before stop_date"]})))

(defn- make-collection-identifier-validation
  "Returns a validation for an ACL catalog_item_identity.collection_identifier closed over the given context and ACL to be validated."
  [context acl]
  {:entry-titles (v/when-present (make-collection-entry-titles-validation context acl))
   :access-value (v/when-present access-value-validation)
   :temporal (v/when-present temporal-identifier-validation)})

(def granule-identifier-validation
  "Validation for the catalog_item_identity.granule_identifier portion of an ACL."
  {:access-value (v/when-present access-value-validation)
   :temporal (v/when-present temporal-identifier-validation)})

(defn- make-catalog-item-identity-validations
  "Returns a standard validation for an ACL catalog_item_identity field closed over the given context and ACL to be validated."
  [context acl]
  [catalog-item-identity-collection-or-granule-validation
   catalog-item-identity-collection-applicable-validation
   catalog-item-identity-granule-applicable-validation
   {:collection-identifier (v/when-present (make-collection-identifier-validation context acl))
    :granule-identifier (v/when-present granule-identifier-validation)}])

(defn validate-provider-exists
  "Validates that the acl provider exists."
  [context fieldpath acl]
  (let [provider-id (acls/acl->provider-id acl)]
    (when (and provider-id
               (not (some #{provider-id} (map :provider-id (mdb/get-providers context)))))
      {fieldpath [(msg/provider-does-not-exist provider-id)]})))

(defn- make-acl-validations
  "Returns a sequence of validations closed over the given context for validating ACL records."
  [context acl]
  [#(validate-provider-exists context %1 %2)
   {:catalog-item-identity (v/when-present (make-catalog-item-identity-validations context acl))}])

(defn validate-acl-save!
  "Throws service errors if ACL is invalid."
  [context acl]
  (v/validate! (make-acl-validations context acl) acl))
