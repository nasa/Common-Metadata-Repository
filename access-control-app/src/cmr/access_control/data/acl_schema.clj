(ns cmr.access-control.data.acl-schema
  (:require [cmr.common.validations.json-schema :as js]))

(defn- ref-def
  "Returns a JSON Schema $ref pointing to #/definitions/name. Short for \"reference to definition\"."
  [id]
  (str "#/definitions/"
       (if (keyword? id)
         (name id)
         id)))

(def acl-schema
  (js/parse-json-schema
    {:type :object
     :additionalProperties false
     :properties {:legacy_guid {:$ref "#/definitions/identifierType"}
                  :group_permissions {:$ref "#/definitions/groupPermissionsType"}
                  :system_identity {:$ref "#/definitions/systemIdentityType"}}
     :definitions {:identifierType {:type :string
                                    :minLength 1
                                    :maxLength 100}
                   :groupPermissionsType {:type :object
                                          :properties {:permissions {:type :array
                                                                     :items {:enum ["create"
                                                                                    "read"
                                                                                    "update"
                                                                                    "delete"
                                                                                    "order"]}}
                                                       :group_id {:type :string
                                                                  :minLength 1
                                                                  :maxLength 100}
                                                       :user_type {:enum ["registered"
                                                                          "guest"]}}
                                          :oneOf [{:required [:permissions :group_id]}
                                                  {:required [:permissions :user_type]}]}
                   :systemObjectTargetType {:enum ["SYSTEM_AUDIT_REPORT"
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
                   :providerObjectTargetType {:enum ["AUDIT_REPORT"
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
                   :systemIdentityType {:type :object
                                        :properties {:target (ref-def :systemObjectTargetType)}}
                   :providerIdentityType {:type :object
                                          :properties {:provider_id (ref-def :identifierType)
                                                       :target (ref-def :providerObjectTargetType)}}
                   :singleInstanceIdentityType {:type :object
                                                :properties {:target_id (ref-def :identifierType)
                                                             ;; this seems silly
                                                             :target {:enum ["GROUP_MANAGEMENT"]}}}
                   :catalogItemIdentityType {:type :object
                                             :properties {:name (ref-def :identifierType)
                                                          :provider_id (ref-def :identifierType)
                                                          :collection_applicable {:type :boolean}
                                                          :granule_applicable {:type :boolean}
                                                          :collection_identifier (ref-def :collectionIdentifierType)}}
                   :accessValueType {:type :object
                                     :properties {:min_value {:type :number}
                                                  :max_value {:type :number}
                                                  :include_undefined_value {:type :boolean}}}
                   :temporalIdentifierType {:type :object
                                            :properties {:start_date {:type :string
                                                                      :format :date-time}
                                                         :stop_date {:type :string
                                                                     :format :date-time
                                                                     :mask {:enum ["intersect"
                                                                                   "contains"
                                                                                   "disjoint"]}}}}
                   :collectionIdentifierType {:type :object
                                              :properties {:entry_titles {:type :array
                                                                          :items {:type :string
                                                                                  :minLength 1
                                                                                  :maxLength 100}}
                                                           :access_value (ref-def :accessValueType)
                                                           :temporal (ref-def :temporalIdentifierType)
                                                           :granule_identifier {:type :object
                                                                                :properties {:access_value (ref-def :accessValueType)
                                                                                             :temporal (ref-def :temporalIdentifierType)}}}}}}))
