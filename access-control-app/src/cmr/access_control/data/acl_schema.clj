(ns cmr.access-control.data.acl-schema
  (:require [cmr.common.validations.json-schema :as js]))

(defn- ref-def
  "Returns a JSON Schema $ref object pointing to #/definitions/name.
  Short for \"reference to definitions\"."
  [id]
  {:$ref (str "#/definitions/"
              (if (keyword? id)
                (name id)
                id))})

;; NOTE: certain target values that are used in code are defined as vars here

(def system-any-acl-target "ANY_ACL")

(def system-object-targets
  "A collection of valid system_object.target values."
  ["SYSTEM_AUDIT_REPORT"
   "METRIC_DATA_POINT_SAMPLE"
   "SYSTEM_INITIALIZER"
   "ARCHIVE_RECORD"
   "ERROR_MESSAGE"
   "TOKEN"
   "TOKEN_REVOCATION"
   "EXTENDED_SERVICE_ACTIVATION"
   "ORDER_AND_ORDER_ITEMS"
   "PROVIDER"
   "TAG_GROUP"
   "TAXONOMY"
   "TAXONOMY_ENTRY"
   "USER_CONTEXT"
   "USER"
   "GROUP"
   system-any-acl-target
   "EVENT_NOTIFICATION"
   "EXTENDED_SERVICE"
   "SYSTEM_OPTION_DEFINITION"
   "SYSTEM_OPTION_DEFINITION_DEPRECATION"
   "INGEST_MANAGEMENT_ACL"
   "SYSTEM_CALENDAR_EVENT"
   "DASHBOARD_ADMIN"
   "DASHBOARD_ARC_CURATOR"
   "DASHBOARD_MDQ_CURATOR"])

(def ingest-management-acl-target "INGEST_MANAGEMENT_ACL")
(def provider-catalog-item-acl-target "CATALOG_ITEM_ACL")
(def provider-object-acl-target "PROVIDER_OBJECT_ACL")

(def provider-object-targets
  "A collection of valid provider_object.target values."
  ["AUDIT_REPORT"
   "OPTION_ASSIGNMENT"
   "OPTION_DEFINITION"
   "OPTION_DEFINITION_DEPRECATION"
   "DATASET_INFORMATION"
   "PROVIDER_HOLDINGS"
   "EXTENDED_SERVICE"
   "PROVIDER_ORDER"
   "PROVIDER_ORDER_RESUBMISSION"
   "PROVIDER_ORDER_ACCEPTANCE"
   "PROVIDER_ORDER_REJECTION"
   "PROVIDER_ORDER_CLOSURE"
   "PROVIDER_ORDER_TRACKING_ID"
   "PROVIDER_INFORMATION"
   "PROVIDER_CONTEXT"
   "AUTHENTICATOR_DEFINITION"
   "PROVIDER_POLICIES"
   "USER"
   "GROUP"
   "DASHBOARD_DAAC_CURATOR"
   provider-object-acl-target
   provider-catalog-item-acl-target
   ingest-management-acl-target
   "DATA_QUALITY_SUMMARY_DEFINITION"
   "DATA_QUALITY_SUMMARY_ASSIGNMENT"
   "PROVIDER_CALENDAR_EVENT"
   "NON_NASA_DRAFT_USER"
   "NON_NASA_DRAFT_APPROVER"
   "SUBSCRIPTION_MANAGEMENT"])

(def valid-permissions
  "A collection of valid permissions for group permissions"
  ["create" "read" "update" "delete" "order"])

(def acl-schema
  (js/parse-json-schema
    {:type :object
     :additionalProperties false
     :properties {:legacy_guid (ref-def :IdentifierType)
                  :group_permissions {:type :array
                                      :items (ref-def :GroupPermissionsType)}
                  :system_identity (ref-def :SystemIdentityType)
                  :provider_identity (ref-def :ProviderIdentityType)
                  :single_instance_identity (ref-def :SingleInstanceIdentityType)
                  :catalog_item_identity (ref-def :CatalogItemIdentityType)}
     :oneOf [{:required [:group_permissions :system_identity]}
             {:required [:group_permissions :provider_identity]}
             {:required [:group_permissions :single_instance_identity]}
             {:required [:group_permissions :catalog_item_identity]}]
     :definitions {:IdentifierType {:type :string
                                    :minLength 1
                                    :maxLength 100}
                   :GroupPermissionsType {:type :object
                                          :additionalProperties false
                                          :properties {:permissions {:type :array
                                                                     :items {:enum valid-permissions}
                                                                     :minLength 1}
                                                       :group_id {:type :string
                                                                  :minLength 1
                                                                  :maxLength 100}
                                                       :user_type {:enum ["registered"
                                                                          "guest"]}}
                                          :oneOf [{:required [:permissions :group_id]}
                                                  {:required [:permissions :user_type]}]}
                   :SystemIdentityType {:type :object
                                        :additionalProperties false
                                        :properties {:target {:enum system-object-targets}}
                                        :required [:target]}
                   :ProviderIdentityType {:type :object
                                          :additionalProperties false
                                          :properties {:provider_id (ref-def :IdentifierType)
                                                       :target {:enum provider-object-targets}}
                                          :required [:provider_id :target]}
                   :SingleInstanceIdentityType {:type :object
                                                :additionalProperties false
                                                :properties {:target_id (ref-def :IdentifierType)
                                                             :target {:enum ["GROUP_MANAGEMENT"]}}
                                                :required [:target_id :target]}
                   :CatalogItemIdentityType {:type :object
                                             :additionalProperties false
                                             :properties {:name (ref-def :IdentifierType)
                                                          :provider_id (ref-def :IdentifierType)
                                                          :collection_applicable {:type :boolean}
                                                          :granule_applicable {:type :boolean}
                                                          :collection_identifier (ref-def :CollectionIdentifierType)
                                                          :granule_identifier (ref-def :GranuleIdentifierType)}
                                             :required [:name :provider_id]}
                   :AccessValueType {:type :object
                                     :additionalProperties false
                                     :properties {:min_value {:type :number}
                                                  :max_value {:type :number}
                                                  :include_undefined_value {:type :boolean}}}
                   :TemporalIdentifierType {:type :object
                                            :additionalProperties false
                                            :properties {:start_date {:type :string
                                                                      :format :date-time}
                                                         :stop_date {:type :string
                                                                     :format :date-time}
                                                         :mask {:enum ["intersect"
                                                                       "contains"
                                                                       "disjoint"]}}
                                            :required [:start_date :stop_date :mask]}
                   :CollectionIdentifierType {:type :object
                                              :additionalProperties false
                                              :properties {:entry_titles {:type :array
                                                                          :items {:type :string
                                                                                  :minLength 1}}
                                                           :concept_ids {:type :array
                                                                         :items (ref-def :IdentifierType)}
                                                           :access_value (ref-def :AccessValueType)
                                                           :temporal (ref-def :TemporalIdentifierType)}}
                   :GranuleIdentifierType {:type :object
                                           :additionalProperties false
                                           :properties {:access_value (ref-def :AccessValueType)
                                                        :temporal (ref-def :TemporalIdentifierType)}}}}))

(defn validate-acl-json
  "Validates the acl JSON string against the schema. Throws a service error if it is invalid."
  [json-str]
  (js/validate-json! acl-schema json-str))
