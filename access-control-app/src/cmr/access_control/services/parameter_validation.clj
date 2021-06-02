(ns cmr.access-control.services.parameter-validation
  (:require
   [clojure.string :as str]
   [cmr.access-control.data.acl-schema :as acl-schema]
   [cmr.common.concepts :as cc]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]))

(defn- validate-params
  "Throws a service error when any keys exist in params other than those in allowed-param-names."
  [params & allowed-param-names]
  (when-let [invalid-params (seq (remove (set allowed-param-names) (keys params)))]
    (errors/throw-service-errors :bad-request (for [param invalid-params]
                                                (format "Parameter [%s] was not recognized."
                                                        (name param))))))

(defn validate-standard-params
  "Throws a service error if any parameters other than :token or :pretty are present."
  [params]
  (validate-params params :pretty :token))

(defn validate-group-route-params
  "Same as validate-standard-params plus :group-id."
  [params]
  (validate-params params :pretty :token :group-id))

(defn validate-create-group-route-params
  "Validate params for create group route. Throws exception for un-supported params."
  [params]
  (validate-params params :pretty :token :group-id :managing-group-id :managing_group_id))

(defn- system_object-concept_id-provider-target-validation
  "Validates presence and combinations of system_object, concept_id, provider, and target parameters."
  [{:keys [system_object concept_id provider target target_group_id]}]
  (let [present? #(if (string? %)
                    (not (str/blank? %))
                    (seq %))]
    (when-not (util/xor (present? system_object)
                        (present? concept_id)
                        (present? target_group_id)
                        (and (present? provider)
                             (present? target)))
      ["One of [concept_id], [system_object], [target_group_id], or [provider] and [target] are required."])))

(defn- system_object-validation
  "Validates that system_object parameter has a valid value, if present."
  [{:keys [system_object]}]
  (when system_object
    (when-not (some #{system_object} acl-schema/system-object-targets)
      [(str "Parameter [system_object] must be one of: " (pr-str acl-schema/system-object-targets))])))

(defn- target-group-id-validation
  "Validates the given target group id is a valid group concept id
   and returns errors if it's invalid. Returns nil if valid."
  [group-id]
  (when-not (re-matches #"(AG|ag|Ag|aG)\d+-[A-Za-z0-9_]+" group-id)
    [(format "Target group id [%s] is not valid." group-id)]))

(defn- target-group-ids-validation
  "Validates that all values in the multi-valued target_group_id param are valid group concept ids"
  [{:keys [target_group_id]}]
  (mapcat target-group-id-validation target_group_id))

(defn- concept_ids-validation
  "Validates that all values in the multi-valued concept_id param are valid concept IDs"
  [{:keys [concept_id]}]
  (mapcat cc/concept-id-validation concept_id))

(defn- user_id-user_type-validation
  "Validates that only one of user_id or user_type are specified."
  [{:keys [user_id user_type]}]
  (if-not (= 1 (count (remove str/blank? [user_id user_type])))
    ["One of parameters [user_type] or [user_id] are required."]))

(defn- provider-target-validation
  "Validates that when provider param is specified, target param is a valid enum value."
  [{:keys [provider target]}]
  (when (and provider (not (some #{target} acl-schema/provider-object-targets)))
    [(str "Parameter [target] must be one of: " (pr-str acl-schema/provider-object-targets))]))

(defn- single-target-validation
  "Validates that only one target is specified."
  [{:keys [target]}]
  (when (and
         (vector? target)
         (> (count target) 1))
    ["Only one target can be specified."]))

(def ^:private get-permissions-validations
  "Defines validations for get permissions parameters and values"
  [single-target-validation
   system_object-concept_id-provider-target-validation
   provider-target-validation
   user_id-user_type-validation
   system_object-validation
   target-group-ids-validation
   concept_ids-validation])

(defn validate-get-permission-params
  "Throws service errors if any invalid params or values are found."
  [params]
  (validate-params
   params :system_object :concept_id :user_id :user_type :provider :target :target_group_id)
  (when-let [errors (seq (mapcat #(% params) get-permissions-validations))]
    (errors/throw-service-errors :bad-request errors)))

(defn validate-current-sids-params
  "Throws service errors if any invalid params or values are found."
  [params]
  (validate-params params :user-token))

(defn validate-s3-buckets-params
  "Throws service errors if any invalid params or values are found."
  [params]
  (validate-params params :user_id :provider)
  (when (str/blank? (:user_id params))
    (errors/throw-service-errors :bad-request ["Parameter [user_id] is required."])))
