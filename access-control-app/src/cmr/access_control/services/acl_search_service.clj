(ns cmr.access-control.services.acl-search-service
  "Contains ACL search functions, including parameter
   validation and user visibility permission checks"
  (:require
    [camel-snake-kebab.core :as csk]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [cmr.access-control.data.acl-schema :as schema]
    [cmr.access-control.services.acl-authorization :as acl-auth]
    [cmr.access-control.services.acl-service :as acl-service]
    [cmr.access-control.services.auth-util :as auth-util]
    [cmr.access-control.services.group-service :as groups]
    [cmr.access-control.services.permitted-concept-id-search :as pcs]
    [cmr.acl.core :as acl]
    [cmr.common-app.services.search :as cs]
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.parameter-validation :as cpv]
    [cmr.common-app.services.search.parameters.converters.nested-field :as nf]
    [cmr.common-app.services.search.params :as cp]
    [cmr.common-app.services.search.query-execution :as qe]
    [cmr.common-app.services.search.query-model :as common-qm]
    [cmr.common.log :refer [info debug]]
    [cmr.common.util :as util]
    [cmr.transmit.echo.tokens :as tokens]
    [cmr.transmit.metadata-db2 :as mdb2]
    [cmr.umm.collection.product-specific-attribute :as psa]))

(defmethod cpv/params-config :acl
  [_]
  (cpv/merge-params-config
    cpv/basic-params-config
    {:single-value #{:include-full-acl}
     :multiple-value #{:permitted-group :identity-type :provider :permitted-concept-id}}
    {:single-value #{:include-full-acl :legacy-guid}
     :multiple-value #{:permitted-group :identity-type :provider}
     :always-case-sensitive #{}
     :disallow-pattern #{:identity-type :permitted-user :group-permission :legacy-guid}
     :allow-or #{}}))

(defmethod cpv/valid-query-level-params :acl
  [_]
  #{:include-full-acl :legacy-guid})

(defmethod cpv/valid-parameter-options :acl
  [_]
  {:permitted-concept-id #{}
   :permitted-group cpv/string-param-options
   :provider cpv/string-param-options
   :identity-type cpv/string-param-options
   :legacy-guid cpv/string-param-options
   :permitted-user #{}
   :group-permission #{}})

(defn- valid-permission?
  "Returns true if the given permission is valid."
  [permission]
  (contains? (set schema/valid-permissions) (str/lower-case permission)))

(defn valid-permitted-group?
  "Returns true if the given permitted group is valid, i.e. guest, registered or conforms to
  access group id format."
  [group]
  (or (.equalsIgnoreCase "guest" group)
      (.equalsIgnoreCase "registered" group)
      (some? (re-find #"[Aa][Gg]\d+-.+" group))))

(defn- permitted-group-validation
  "Validates permitted group parameters."
  [context params]
  (let [permitted-groups (util/seqify (:permitted-group params))]
    (when-let [invalid-groups (seq (remove valid-permitted-group? permitted-groups))]
      [(format "Parameter permitted_group has invalid values [%s]. Only 'guest', 'registered' or a group concept id can be specified."
               (str/join ", " invalid-groups))])))

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

(defn- group-permission-permitted-group-validation
  "Validates permitted group subfield of group-permission parameters."
  [params]
  (let [permitted-groups (->> (:group-permission params)
                              vals
                              (keep :permitted-group))]
    (when-let [invalid-groups (seq (remove valid-permitted-group? permitted-groups))]
      [(format (str "Sub-parameter permitted_group of parameter group_permissions has invalid values [%s]. "
                    "Only 'guest', 'registered' or a group concept id may be specified.")
               (str/join ", " invalid-groups))])))

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
          (group-permission-permitted-group-validation params)
          (group-permission-permission-validation params)))

(def acl-identity-type->search-value
 "Maps identity type query paremter values to the actual values used in the index."
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

(defmethod cp/always-case-sensitive-fields :acl
  [_]
  #{:concept-id :identity-type})

(defmethod common-qm/default-sort-keys :acl
  [_]
  [{:field :display-name :order :asc}])

(defmethod cp/param-mappings :acl
  [_]
  {:permitted-concept-id :permitted-concept-id
   :permitted-group :string
   :identity-type :acl-identity-type
   :provider :string
   :permitted-user :acl-permitted-user
   :group-permission :acl-group-permission
   :legacy-guid :string})

(defmethod cp/parse-query-level-params :acl
  [concept-type params]
  (let [[params query-attribs] (cp/default-parse-query-level-params :acl params)]
    [(dissoc params :include-full-acl)
     (merge query-attribs
            (when (= (:include-full-acl params) "true")
              {:result-features [:include-full-acl]}))]))

(defmethod cp/parameter->condition :permitted-concept-id
 [context concept-type param value options]
 (let [concept (mdb2/get-latest-concept context value)]
   (pcs/get-permitted-concept-id-conditions context concept)))

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
    (cp/string-parameter->condition concept-type :permitted-group groups options)))

(defmethod cp/parameter->condition :legacy-guid
  [context concept-type param value options]
  (cp/string-parameter->condition concept-type :legacy-guid value options))

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
                                    (partial cpv/validate-map [:collection-concept-id])
                                    (partial cpv/validate-map [:options :permitted-group])
                                    (partial cpv/validate-map [:options :identity-type])
                                    (partial cpv/validate-map [:options :provider])
                                    (partial cpv/validate-map [:options :permitted-user])
                                    (partial cpv/validate-map [:group_permission])])]
   (cpv/validate-parameters
     :acl safe-params
     (concat cpv/common-validations
             [permitted-group-validation
              identity-type-validation
              group-permission-validation
              (partial cpv/validate-boolean-param :include-full-acl)])
     type-errors))
 params)

(defn has-any-read?
  "Returns true if user has permssion to read any ACL."
  [context]
  (acl-auth/has-system-access? context :read "ANY_ACL"))

(defn get-searchable-acls
  "Returns a lazy sequence of concept-ids for ACLs that are searchable for the given sids."
  [context acls-with-concept-id]
  (for [acl acls-with-concept-id
        :when (acl-auth/action-permitted-on-acl? context :read acl (:concept-id acl))]
    (:concept-id acl)))

(defn make-acl-condition
  "Returns elastic condition to filter out ACLs that are not visible to the user."
  [context acls-with-concept-id]
  (let [concept-ids (get-searchable-acls context acls-with-concept-id)]
    (when (seq concept-ids)
      (common-qm/string-conditions :concept-id concept-ids true))))

(defmethod qe/add-acl-conditions-to-query :acl
  [context query]
  (let [acl-concepts (acl-service/get-all-acl-concepts context)
        acls-with-concept-id (map #(assoc (acl-service/get-parsed-acl %) :concept-id (:concept-id %)) acl-concepts)]
    (if (has-any-read? context)
      query
      (if-let [acl-cond (make-acl-condition context acls-with-concept-id)]
        (update-in query [:condition] #(gc/and-conds [acl-cond %]))
        (assoc query :condition common-qm/match-none)))))

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
