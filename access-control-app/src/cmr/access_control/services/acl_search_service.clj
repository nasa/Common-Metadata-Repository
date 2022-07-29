(ns cmr.access-control.services.acl-search-service
  "Contains ACL search functions, including parameter
   validation and user visibility permission checks"
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [cmr.access-control.data.acl-schema :as schema]
   [cmr.access-control.services.auth-util :as auth-util]
   [cmr.access-control.services.group-service :as groups]
   [cmr.access-control.services.permitted-concept-id-search :as pcs]
   [cmr.common-app.services.search :as cs]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.parameter-validation :as cpv]
   [cmr.common-app.services.search.parameters.converters.nested-field :as nf]
   [cmr.common-app.services.search.params :as cp]
   [cmr.common-app.services.search.query-model :as common-qm]
   [cmr.common.log :refer [info debug]]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.umm.collection.product-specific-attribute :as psa])
  ;; Must be required to be available at runtime
  (:require
   cmr.access-control.data.acl-json-results-handler))

(defmethod cpv/params-config :acl
  [_]
  (cpv/merge-params-config
    cpv/basic-params-config
    {:single-value #{:include-full-acl :legacy-guid :include-legacy-group-guid}
     :multiple-value #{:permitted-group :identity-type :provider :id :target :target-id}
     :always-case-sensitive #{}
     :disallow-pattern #{:identity-type :permitted-user :group-permission :legacy-guid :target}
     :allow-or #{}}))

(defmethod cpv/valid-query-level-params :acl
  [_]
  #{:include-full-acl :legacy-guid :include-legacy-group-guid :id :target})

(defmethod cpv/valid-parameter-options :acl
  [_]
  {:permitted-concept-id #{}
   :permitted-group cpv/string-param-options
   :provider cpv/string-param-options
   :identity-type cpv/string-param-options
   :target #{}
   :target-id #{}
   :legacy-guid #{}
   :id #{}
   :permitted-user #{}
   :group-permission #{}})

(defn- valid-permission?
  "Returns true if the given permission is valid."
  [permission]
  (contains? (set schema/valid-permissions) (str/lower-case permission)))

(defn- permitted-concept-id-validation
  "Validates permitted concept id parameter"
  [context params]
  (when-let [permitted-concept-id (:permitted-concept-id params)]
    (when-not (re-matches #"(C|G)\d+-[A-Za-z0-9_]+" permitted-concept-id)
      [(format "Must be collection or granule concept id.")])))

(defn- group-permission-parameter-index-validation
  "Validates that the indices used in group permission parameters are valid numerical indices."
  [params]
  (keep (fn [index-key]
            (let [index-str (name index-key)
                  index (psa/safe-parse-value :int index-str)]
             (when (or (nil? index) (< index 0))
               (format (str "Parameter group_permission has invalid index value [%s]. "
                            "Only integers greater than or equal to zero may be specified.")
                       index-str))))
        (keys (:group-permission params))))

(defn- group-permission-parameter-subfield-validation
  "Validates that the subfields for a group-permission query only include 'permitted-group' and 'permision'."
  [params]
  (keep (fn [subfield]
          (when-not (contains? #{:permitted-group :permission} subfield)
            (format (str "Parameter group_permission has invalid subfield [%s]. "
                         "Only 'permitted_group' and 'permission' are allowed.")
                    (csk/->snake_case (name subfield)))))
        (mapcat keys (vals (:group-permission params)))))

(defn- group-permission-permission-validation
  "Validates that the permission subfield of group-permission parameters is one of the permitted values."
  [params]
  (let [permissions (->> (:group-permission params)
                         vals
                         (keep :permission))]
    (when-let [invalid-permissions (seq (remove valid-permission? permissions))]
      [(format (str "Sub-parameter permission of parameter group_permissions has invalid values [%s]. "
                    "Only 'read', 'update', 'create', 'delete', or 'order' may be specified.")
               (str/join ", " invalid-permissions))])))

(defn- group-permission-validation
  "Validates group_permission parameters.
  The group-permission validations operation on the :group-permission field of the parameters
  which (if present) should take the following form

  {:group-permission {:0 {:permitted-group \"guest\" :permission \"read\"}
                      :1 {:permission \"order\"}}}

  which corresponds to acls that grant read permission to guests for order permission (to anyone)."
  [context params]
  (concat (group-permission-parameter-index-validation params)
          (group-permission-parameter-subfield-validation params)
          (group-permission-permission-validation params)))

(def acl-identity-type->search-value
 "Maps identity type query parameter values to the actual values used in the index."
 {"system" "System"
  "single_instance" "Group"
  "provider" "Provider"
  "catalog_item" "Catalog Item"})

(defn- valid-identity-type?
  "Returns true if the given identity-type is valid, i.e., one of 'system', 'single_instance', 'provider', or 'catalog_item'."
  [identity-type]
  (contains? (set (keys acl-identity-type->search-value)) (str/lower-case identity-type)))

(defn- identity-type-validation
  "Validates identity-type parameters."
  [context params]
  (let [identity-types (util/seqify (:identity-type params))]
    (when-let [invalid-types (seq (remove valid-identity-type? identity-types))]
      [(format (str "Parameter identity_type has invalid values [%s]. "
                    "Only 'provider', 'system', 'single_instance', or 'catalog_item' can be specified.")
               (str/join ", " invalid-types))])))

(defn- include-legacy-group-guid-validation
  "Validates include-legacy-group-guid parameters."
  [_ params]
  (when (and (= "true" (:include-legacy-group-guid params))
             (not= "true" (:include-full-acl params)))
    ["Parameter include_legacy_group_guid can only be used when include_full_acl is true"]))

(defn- target-id-validation
  "Validates that when target-id parameter is specified,
  identity-type=single_instance is also specified"
  [context params]
  (let [target-ids (util/seqify (:target-id params))
        identity-types (util/seqify (:identity-type params))]
    (when (and target-ids
               (not (some #(= "single_instance" %) identity-types)))
      ["Parameter identity_type=single_instance is required to search by target-id"])))

(defmethod cp/always-case-sensitive-fields :acl
  [_]
  #{:concept-id :identity-type :target-id})

(defmethod common-qm/default-sort-keys :acl
  [_]
  [{:field :display-name-lowercase :order :asc}])

(defmethod cp/param-mappings :acl
  [_]
  {:permitted-concept-id :permitted-concept-id
   :permitted-group :string
   :identity-type :acl-identity-type
   :target :string
   :target-id :string
   :provider :string
   :permitted-user :acl-permitted-user
   :group-permission :acl-group-permission
   :legacy-guid :string
   :id ::id})

(defmethod cp/parse-query-level-params :acl
  [concept-type params]
  (let [[params query-attribs] (cp/default-parse-query-level-params :acl params)]
    [(dissoc params :include-full-acl :include-legacy-group-guid)
     (merge query-attribs
            (when (= "true" (:include-full-acl params))
              {:result-features [:include-full-acl]})
            (when (= "true" (:include-legacy-group-guid params))
              {:result-features [:include-full-acl :include-legacy-group-guid]}))]))

(defmethod cp/parameter->condition :permitted-concept-id
 [context concept-type param value options]
 (if-let [concept (mdb2/get-latest-concept context value)]
   (pcs/get-permitted-concept-id-conditions context concept)
   (errors/throw-service-error :bad-request (format "permitted_concept_id does not exist."))))

(defmethod cp/parameter->condition :acl-identity-type
 [context concept-type param value options]
 (if (sequential? value)
   (gc/group-conds (cp/group-operation param options)
                   (map #(cp/parameter->condition context concept-type param % options) value))
   (let [value (get acl-identity-type->search-value (str/lower-case value))]
     (cp/string-parameter->condition concept-type param value options))))

(defmethod cp/parameter->condition :acl-permitted-user
  [context concept-type param value options]
  ;; reject non-existent users
  (groups/validate-members-exist context [value])
  (let [groups (->> (auth-util/get-sids context value)
                    (map name))]
    (gc/and
     (gc/or
      (nf/parse-nested-condition :group-permission {:permission "create"} false false)
      (nf/parse-nested-condition :group-permission {:permission "read"} false false)
      (nf/parse-nested-condition :group-permission {:permission "update"} false false)
      (nf/parse-nested-condition :group-permission {:permission "delete"} false false)
      (nf/parse-nested-condition :group-permission {:permission "order"} false false))
     (cp/string-parameter->condition concept-type :permitted-group groups options))))

(defmethod cp/parameter->condition :legacy-guid
  [context concept-type param value options]
  (cp/string-parameter->condition concept-type :legacy-guid value options))

(defmethod cp/parameter->condition ::id
  [context concept-type param value options]
  (gc/or
    (cp/string-parameter->condition concept-type :concept-id value options)
    (cp/string-parameter->condition concept-type :legacy-guid value options)))

(defmethod cp/parameter->condition :acl-group-permission
  [context concept-type param value options]
  (let [case-sensitive? false
        pattern? false
        target-field (keyword (name param))]
    (if (map? (first (vals value)))
      ;; If multiple group permissions are passed in like the following
      ;;  -> group_permission[0][permitted_group]=guest&group_permission[1][permitted_group]=registered
      ;; then this recurses back into this same function to handle each separately.
      (gc/group-conds
        :or
        (map #(cp/parameter->condition context concept-type param % options)(vals value)))
      ;; Creates the group-permission condition.
      (nf/parse-nested-condition target-field value case-sensitive? pattern?))))

(defn validate-acl-search-params
  "Validates the parameters for an ACL search. Returns the parameters or throws an error if invalid."
  [context params]
  (let [[safe-params type-errors] (cpv/apply-type-validations
                                   params
                                   [(partial cpv/validate-map [:options])
                                    (partial cpv/validate-map [:options :permitted-group])
                                    (partial cpv/validate-map [:options :identity-type])
                                    (partial cpv/validate-map [:options :provider])
                                    (partial cpv/validate-map [:options :permitted-user])
                                    (partial cpv/validate-map [:group_permission])])]
    (cpv/validate-parameters
     :acl safe-params
     (concat cpv/common-validations
             [permitted-concept-id-validation
              identity-type-validation
              group-permission-validation
              target-id-validation
              (partial cpv/validate-boolean-param :include-full-acl)
              (partial cpv/validate-boolean-param :include-legacy-group-guid)
              include-legacy-group-guid-validation])
     type-errors))
  params)

(defn search-for-acls
  "Searches for ACLs using given parameters. Returns result map from find-concepts
   including total time taken."
  [context params]
  (let [[query-creation-time query] (util/time-execution
                                     (->> params
                                          cp/sanitize-params
                                          (validate-acl-search-params :acl)
                                          (cp/parse-parameter-query context :acl)))
        [find-concepts-time results] (util/time-execution
                                      (cs/find-concepts context :acl query))
        total-took (+ query-creation-time find-concepts-time)]
    (info (format "Found %d acls in %d ms in format %s with params %s."
                  (:hits results) total-took (common-qm/base-result-format query) (pr-str params)))
    (assoc results :took total-took)))
