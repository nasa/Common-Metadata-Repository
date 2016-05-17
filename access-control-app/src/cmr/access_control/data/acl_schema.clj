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

(def acl-schema
  (js/parse-json-schema
    {:type :object
     :additionalProperties false
     :properties {:legacy_guid (ref-def :IdentifierType)
                  :group_permissions (ref-def :GroupPermissionsType)
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
                                          :properties {:permissions {:type :array
                                                                     :items {:enum ["create"
                                                                                    "read"
                                                                                    "update"
                                                                                    "delete"
                                                                                    "order"]}
                                                                     :minLength 1}
                                                       :group_id {:type :string
                                                                  :minLength 1
                                                                  :maxLength 100}
                                                       :user_type {:enum ["registered"
                                                                          "guest"]}}
                                          :oneOf [{:required [:permissions :group_id]}
                                                  {:required [:permissions :user_type]}]}
                   :SystemObjectTargetType {:enum ["SYSTEM_AUDIT_REPORT"
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
                                                   "ANY_ACL"
                                                   "EVENT_NOTIFICATION"
                                                   "EXTENDED_SERVICE"
                                                   "SYSTEM_OPTION_DEFINITION"
                                                   "SYSTEM_OPTION_DEFINITION_DEPRECATION"
                                                   "INGEST_MANAGEMENT_ACL"
                                                   "SYSTEM_CALENDAR_EVENT"]}
                   :ProviderObjectTargetType {:enum ["AUDIT_REPORT"
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
                                                     "PROVIDER_OBJECT_ACL"
                                                     "CATALOG_ITEM_ACL"
                                                     "INGEST_MANAGEMENT_ACL"
                                                     "DATA_QUALITY_SUMMARY_DEFINITION"
                                                     "DATA_QUALITY_SUMMARY_ASSIGNMENT"
                                                     "PROVIDER_CALENDAR_EVENT"]}
                   :SystemIdentityType {:type :object
                                        :properties {:target (ref-def :SystemObjectTargetType)}
                                        :required [:target]}
                   :ProviderIdentityType {:type :object
                                          :properties {:provider_id (ref-def :IdentifierType)
                                                       :target (ref-def :ProviderObjectTargetType)}
                                          :required [:provider_id :target]}
                   :SingleInstanceIdentityType {:type :object
                                                :properties {:target_id (ref-def :IdentifierType)
                                                             ;; this seems silly
                                                             :target {:enum ["GROUP_MANAGEMENT"]}}
                                                :required [:target_id :target]}
                   :CatalogItemIdentityType {:type :object
                                             :properties {:name (ref-def :IdentifierType)
                                                          :provider_id (ref-def :IdentifierType)
                                                          :collection_applicable {:type :boolean}
                                                          :granule_applicable {:type :boolean}
                                                          :collection_identifier (ref-def :CollectionIdentifierType)
                                                          :granule_identifier (ref-def :GranuleIdentifierType)}
                                             :required [:name :provider_id :collection_identifier]}
                   :AccessValueType {:type :object
                                     :properties {:min_value {:type :number}
                                                  :max_value {:type :number}
                                                  :include_undefined_value {:type :boolean}}
                                     :oneOf [{:required [:min_value :max_value]}
                                             {:required [:min_value :max_value :include_undefined_value]}
                                             {:required [:include_undefined_value]}]}
                   :TemporalIdentifierType {:type :object
                                            :properties {:start_date {:type :string
                                                                      :format :date-time}
                                                         :stop_date {:type :string
                                                                     :format :date-time}
                                                         :mask {:enum ["intersect"
                                                                       "contains"
                                                                       "disjoint"]}}}
                   :CollectionIdentifierType {:type :object
                                              :properties {:entry_titles {:type :array
                                                                          :items {:type :string
                                                                                  :minLength 1
                                                                                  :maxLength 100}}
                                                           :access_value (ref-def :AccessValueType)
                                                           :temporal (ref-def :TemporalIdentifierType)}}
                   :GranuleIdentifierType {:type :object
                                           :properties {:access_value (ref-def :AccessValueType)
                                                        :temporal (ref-def :TemporalIdentifierType)}}}}))
