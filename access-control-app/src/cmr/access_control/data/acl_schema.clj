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

(def system-object-targets-with-definitions
  "A collection of valid system_object.target values."
  [{"SYSTEM_AUDIT_REPORT" "Audit log data"}
   {"METRIC_DATA_POINT_SAMPLE" "Sample metrics"}
   {"SYSTEM_INITIALIZER" "Init config"}
   {"ARCHIVE_RECORD" "Archived data"}
   {"ERROR_MESSAGE" "Error logs"}
   {"TOKEN" "Auth tokens"}
   {"TOKEN_REVOCATION" "Revoked tokens"}
   {"EXTENDED_SERVICE_ACTIVATION" "Service status"}
   {"ORDER_AND_ORDER_ITEMS" "Order details"}
   {"PROVIDER" "Service provider info"}
   {"TAG_GROUP" "Grouped tags"}
   {"TAXONOMY" "Classification system"}
   {"TAXONOMY_ENTRY" "Taxonomy item"}
   {"USER_CONTEXT" "User environment"}
   {"USER" "User profile"}
   {"GROUP" "User group data"}
   {system-any-acl-target "Universal ACL"}
   {"EVENT_NOTIFICATION" "Event alerts"}
   {"EXTENDED_SERVICE" "Additional services"}
   {"SYSTEM_OPTION_DEFINITION" "System settings"}
   {"SYSTEM_OPTION_DEFINITION_DEPRECATION" "Deprecated options"}
   {"INGEST_MANAGEMENT_ACL" "Ingest permissions"}
   {"SYSTEM_CALENDAR_EVENT" "Scheduled events"}
   {"DASHBOARD_ADMIN" "Admin dashboard"}
   {"DASHBOARD_ARC_CURATOR" "ARC curator view"}
   {"DASHBOARD_MDQ_CURATOR" "MDQ curator view"}]
)

(def system-object-targets
  "A collection of valid system_object.target values"
  (vec (mapcat keys system-object-targets-with-definitions)))

system-object-targets

(def ingest-management-acl-target "INGEST_MANAGEMENT_ACL")
(def provider-catalog-item-acl-target "CATALOG_ITEM_ACL")
(def provider-object-acl-target "PROVIDER_OBJECT_ACL")

(def provider-object-targets-with-definitions
  "A collection of valid provider_object.target values."
  [{"AUDIT_REPORT" "Access to audit reporting functionality"}
   {"OPTION_ASSIGNMENT" "Ability to assign options"}
   {"OPTION_DEFINITION" "Permission to define new options"}
   {"OPTION_DEFINITION_DEPRECATION" "Authority to deprecate option definitions"}
   {"DATASET_INFORMATION" "Access to dataset metadata and details"}
   {"PROVIDER_HOLDINGS" "View of provider's data holdings"}
   {"EXTENDED_SERVICE" "Access to extended service features"}
   {"PROVIDER_ORDER" "Ability to manage provider orders"}
   {"PROVIDER_ORDER_RESUBMISSION" "Permission to resubmit provider orders"}
   {"PROVIDER_ORDER_ACCEPTANCE" "Authority to accept provider orders"}
   {"PROVIDER_ORDER_REJECTION" "Authority to reject provider orders"}
   {"PROVIDER_ORDER_CLOSURE" "Ability to close provider orders"}
   {"PROVIDER_ORDER_TRACKING_ID" "Access to provider order tracking IDs"}
   {"PROVIDER_INFORMATION" "View of provider information"}
   {"PROVIDER_CONTEXT" "Access to provider context data"}
   {"AUTHENTICATOR_DEFINITION" "Ability to define authenticators"}
   {"PROVIDER_POLICIES" "Access to provider policy information"}
   {"USER" "User account management permissions"}
   {"GROUP" "Group management permissions"}
   {"DASHBOARD_DAAC_CURATOR" "Access to DAAC curator dashboard"}
   {provider-object-acl-target "Target for provider object ACLs"}
   {provider-catalog-item-acl-target "Target for provider catalog item ACLs"}
   {ingest-management-acl-target "Target for ingest management ACLs"}
   {"DATA_QUALITY_SUMMARY_DEFINITION" "Ability to define data quality summaries"}
   {"DATA_QUALITY_SUMMARY_ASSIGNMENT" "Permission to assign data quality summaries"}
   {"PROVIDER_CALENDAR_EVENT" "Access to provider calendar events"}
   {"NON_NASA_DRAFT_USER" "Permissions for non-NASA draft users"}
   {"NON_NASA_DRAFT_APPROVER" "Authority to approve non-NASA drafts"}
   {"SUBSCRIPTION_MANAGEMENT" "Ability to manage subscriptions"}]
)

(def provider-object-targets
  "A collection of valid provider_object.target values."
  (vec (mapcat keys provider-object-targets-with-definitions)))

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
