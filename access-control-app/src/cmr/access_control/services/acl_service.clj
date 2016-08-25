(ns cmr.access-control.services.acl-service
  (:require [clojure.string :as str]
            [camel-snake-kebab.core :as csk]
            [cmr.access-control.services.acl-service-messages :as acl-msg]
            [cmr.access-control.services.messages :as msg]
            [cmr.access-control.services.acl-validation :as v]
            [cmr.common.log :refer [info debug]]
            [cmr.common.util :as u]
            [cmr.common.mime-types :as mt]
            [cmr.common.services.errors :as errors]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.transmit.metadata-db :as mdb1]
            [cmr.transmit.metadata-db2 :as mdb]
            [cmr.common-app.services.search :as cs]
            [cmr.common-app.services.search.params :as cp]
            [cmr.common-app.services.search.parameter-validation :as cpv]
            [cmr.common-app.services.search.query-model :as common-qm]
            [cmr.common-app.services.search.parameters.converters.nested-field :as nf]
            [cmr.common-app.services.search.group-query-conditions :as gc]
            [clojure.edn :as edn]
            [clojure.set :as set]
            [cmr.common.concepts :as concepts]
            [cmr.access-control.services.group-service :as groups]
            [cmr.transmit.metadata-db :as mdb1]
            [cmr.acl.core :as acl]
            [cmr.umm.acl-matchers :as acl-matchers]
            [cmr.umm.collection.product-specific-attribute :as psa]
            [cmr.common.util :as util]
            [cmr.common.date-time-parser :as dtp]
            [cmr.access-control.data.acls :as acls]
            [cmr.access-control.data.acl-schema :as schema]))

;;; Misc constants and accessor functions

(def acl-provider-id
  "The provider ID for all ACLs. Since ACLs are not owned by individual
  providers, they fall under the CMR system provider ID."
  "CMR")

(defn acl-identity
  "Returns a string value representing the ACL's identity field."
  [acl]
  (str/lower-case
    (let [{:keys [system-identity provider-identity single-instance-identity catalog-item-identity]} acl]
      (cond
        system-identity          (str "system:" (:target system-identity))
        single-instance-identity (format "single-instance:%s:%s"
                                         (:target-id single-instance-identity)
                                         (:target single-instance-identity))
        provider-identity        (format "provider:%s:%s"
                                         (:provider-id provider-identity)
                                         (:target provider-identity))
        catalog-item-identity    (format "catalog-item:%s:%s"
                                         (:provider-id catalog-item-identity)
                                         (:name catalog-item-identity))
        :else                    (errors/throw-service-error
                                   :bad-request "malformed ACL")))))

(defn- fetch-acl-concept
  "Fetches the latest version of ACL concept by concept id. Handles unknown concept ids by
  throwing a service error."
  [context concept-id]
  (let [{:keys [concept-type provider-id]} (concepts/parse-concept-id concept-id)]
    (when (not= :acl concept-type)
      (errors/throw-service-error :bad-request (acl-msg/bad-acl-concept-id concept-id))))

  (if-let [concept (mdb/get-latest-concept context concept-id false)]
    (if (:deleted concept)
      (errors/throw-service-error :not-found (acl-msg/acl-deleted concept-id))
      concept)
    (errors/throw-service-error :not-found (acl-msg/acl-does-not-exist concept-id))))

(defn- get-sids
  "Returns a seq of sids for the given username string or user type keyword
   for use in checking permissions against acls."
  [context username-or-type]
  (cond
    (contains? #{:guest :registered} username-or-type) [username-or-type]
    (string? username-or-type) (concat [:registered]
                                       (->> (groups/search-for-groups context {:member username-or-type})
                                            :results
                                            :items
                                            (map :concept_id)))))

(defn- acl->base-concept
  "Returns a basic concept map for the given request context and ACL map."
  [context acl]
  {:concept-type :acl
   :metadata (pr-str acl)
   :format mt/edn
   :provider-id acl-provider-id
   :user-id (when-let [token (:token context)]
              (tokens/get-user-id context token))
   ;; ACL-specific fields
   :extra-fields {:acl-identity (acl-identity acl)
                  :target-provider-id (acls/acl->provider-id acl)}})

(defn- context->user-id
  "Returns user id of the token in the context. Throws an error if no token is provided"
  [context]
  (if-let [token (:token context)]
    (tokens/get-user-id context (:token context))
    (errors/throw-service-error :unauthorized msg/token-required)))

(defn create-acl
  "Save a new ACL to Metadata DB. Returns map with concept and revision id of created acl."
  [context acl]
  (v/validate-acl-save! context acl)
  (mdb/save-concept context (merge (acl->base-concept context acl)
                                 {:revision-id 1
                                  :native-id (str (java.util.UUID/randomUUID))})))

(defn update-acl
  "Update the ACL with the given concept-id in Metadata DB. Returns map with concept and revision id of updated acl."
  [context concept-id acl]
  (v/validate-acl-save! context acl)
  ;; This fetch acl call also validates if the ACL with the concept id does not exist or is deleted
  (let [existing-concept (fetch-acl-concept context concept-id)
        existing-legacy-guid (:legacy-guid (edn/read-string (:metadata existing-concept)))
        legacy-guid (:legacy-guid acl)]
    (when-not (= existing-legacy-guid legacy-guid)
      (errors/throw-service-error
        :invalid-data (format "ACL legacy guid cannot be updated, was [%s] and now [%s]"
                              existing-legacy-guid legacy-guid)))
    (mdb/save-concept context (merge (acl->base-concept context acl)
                                     {:concept-id concept-id
                                      :native-id (:native-id existing-concept)}))))

(defn delete-acl
  "Saves a tombstone for the ACL with the given concept id."
  [context concept-id]
  (let [acl-concept (fetch-acl-concept context concept-id)]
    (mdb/save-concept context {:concept-id (:concept-id acl-concept)
                               :revision-id (inc (:revision-id acl-concept))
                               :deleted true})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Search functions

(defmethod cpv/params-config :acl
  [_]
  (cpv/merge-params-config
    cpv/basic-params-config
    {:single-value #{:include-full-acl}
     :multiple-value #{:permitted-group :identity-type :provider}
     :always-case-sensitive #{}
     :disallow-pattern #{:identity-type :permitted-user :group-permission}
     :allow-or #{}}))

(defmethod cpv/valid-query-level-params :acl
  [_]
  #{:include-full-acl})

(defmethod cpv/valid-parameter-options :acl
  [_]
  {:permitted-group cpv/string-param-options
   :provider cpv/string-param-options
   :identity-type cpv/string-param-options
   :permitted-user #{}
   :group-permission #{}})

(defn- valid-permitted-group?
  "Returns true if the given permitted group is valid, i.e. guest, registered or conforms to
  access group id format."
  [group]
  (or (.equalsIgnoreCase "guest" group)
      (.equalsIgnoreCase "registered" group)
      (some? (re-find #"[Aa][Gg]\d+-.+" group))))

(defn- valid-permission?
  "Returns true if the given permission is valid."
  [permission]
  (contains? (set schema/valid-permissions) (str/lower-case permission)))

(defn- permitted-group-validation
  "Validates permitted group parameters."
  [context params]
  (let [permitted-groups (u/seqify (:permitted-group params))]
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
  (let [identity-types (u/seqify (:identity-type params))]
    (when-let [invalid-types (seq (remove valid-identity-type? identity-types))]
      [(format (str "Parameter identity_type has invalid values [%s]. "
                    "Only 'provider', 'system', 'single_instance', or 'catalog_item' can be specified.")
               (str/join ", " invalid-types))])))

(defn boolean-value-validation
  "Validates that all of the boolean parameters have values of true or false."
  [concept-type params]
  (let [bool-params (select-keys params [:include-full-acl])]
    (mapcat
      (fn [[param value]]
        (when-not (contains? #{"true" "false"} (when value (str/lower-case value)))
          [(format "Parameter %s must take value of true or false but was [%s]"
                   (csk/->snake_case_string param) value)]))
      bool-params)))

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
              [permitted-group-validation
               identity-type-validation
               group-permission-validation
               boolean-value-validation])
      type-errors))
  params)

(defmethod cp/always-case-sensitive-fields :acl
  [_]
  #{:concept-id :identity-type})

(defmethod common-qm/default-sort-keys :acl
  [_]
  [{:field :display-name :order :asc}])

(defmethod cp/param-mappings :acl
  [_]
  {:permitted-group :string
   :identity-type :acl-identity-type
   :provider :string
   :permitted-user :acl-permitted-user
   :group-permission :acl-group-permission})

(defmethod cp/parse-query-level-params :acl
  [concept-type params]
  (let [[params query-attribs] (cp/default-parse-query-level-params :acl params)
        result-features (when (= (:include-full-acl params) "true"
                                 [:include-full-acl]))]
    [(dissoc params :include-full-acl)
     (merge query-attribs
            (when (= (:include-full-acl params) "true")
              {:result-features [:include-full-acl]}))]))

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

  (let [groups (->> (get-sids context value)
                    (map name))]
    (cp/string-parameter->condition concept-type :permitted-group groups options)))

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

(defn search-for-acls
  [context params]
  (let [[query-creation-time query] (u/time-execution
                                      (->> params
                                           cp/sanitize-params
                                           (validate-acl-search-params :acl)
                                           (cp/parse-parameter-query context :acl)))
        [find-concepts-time results] (u/time-execution
                                       (cs/find-concepts context :acl query))
        total-took (+ query-creation-time find-concepts-time)]
    (info (format "Found %d acls in %d ms in format %s with params %s."
                  (:hits results) total-took (common-qm/base-result-format query) (pr-str params)))
    (assoc results :took total-took)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Member functions

(defn get-acl
  "Returns the parsed metadata of the latest revision of the ACL concept by id."
  [context concept-id]
  (edn/read-string (:metadata (fetch-acl-concept context concept-id))))

(defn echo-style-temporal-identifier
  [t]
  (when t
    (-> t
        (assoc :temporal-field :acquisition)
        (update-in [:mask] keyword)
        (update-in [:start-date] dtp/try-parse-datetime)
        (update-in [:stop-date] dtp/try-parse-datetime)
        (set/rename-keys {:stop-date :end-date}))))

(defn- echo-style-acl
  "Returns acl with the older ECHO-style keywords for consumption in utility functions from other parts of the CMR."
  [acl]
  (-> acl
      (set/rename-keys {:system-identity :system-object-identity
                        :provider-identity :provider-object-identity
                        :group-permissions :aces})
      (util/update-in-each [:aces] update-in [:user-type] keyword)
      (util/update-in-each [:aces] set/rename-keys {:group-id :group-guid})
      (update-in [:catalog-item-identity :collection-identifier :temporal] echo-style-temporal-identifier)
      (update-in [:catalog-item-identity :granule-identifier :temporal] echo-style-temporal-identifier)
      (update-in [:catalog-item-identity :collection-identifier :access-value]
                 #(set/rename-keys % {:include-undefined-value :include-undefined}))
      (update-in [:catalog-item-identity :granule-identifier :access-value]
                 #(set/rename-keys % {:include-undefined-value :include-undefined}))
      util/remove-empty-maps))

(defn get-all-acl-concepts
  "Returns all ACLs in metadata db."
  [context]
  (for [batch (mdb1/find-in-batches context :acl 1000 {:latest true})
        acl-concept batch]
    acl-concept))

(defn get-parsed-acl
  "Returns the ACL concept's metadata parased from EDN."
  [acl-concept]
  (edn/read-string (:metadata acl-concept)))

(defn- get-echo-style-acls
  "Returns all ACLs in metadata db, converted to \"ECHO-style\" keys for use with existing ACL functions."
  [context]
  (map echo-style-acl (map get-parsed-acl (get-all-acl-concepts context))))

(def all-permissions
  "The set of all permissions checked and returned by the functions below."
  #{:read :order :update :delete})

(def provider-level-permissions
  "The set of permissions that are checked at the provider level."
  #{:update :delete})

(defn- collect-permissions
  "Returns seq of any permissions where (grants-permission? acl permission) returns true for any acl in acls."
  [grants-permission? acls]
  (reduce (fn [granted-permissions acl]
            (if (= all-permissions granted-permissions)
              ;; terminate the reduce early, because all permissions have already been granted
              (reduced granted-permissions)
              ;; determine which permissions are granted by this specific acl
              (reduce (fn [acl-permissions permission]
                        (if (grants-permission? acl permission)
                          (conj acl-permissions permission)
                          acl-permissions))
                      ;; start with the set of permissions granted so far
                      granted-permissions
                      ;; and only reduce over permissions that have not yet been granted
                      (set/difference all-permissions granted-permissions))))
          #{}
          acls))

(defn granule-identifier-matches-granule?
  "Returns true if granule identifier portion of ACL matches granule concept."
  [gran-identifier granule]
  (let [{:keys [access-value temporal]} gran-identifier]
    (and (if access-value
           (acl-matchers/matches-access-value-filter? granule access-value)
           true)
         (if temporal
           (when-let [umm-temporal (u/lazy-get granule :temporal)]
             (acl-matchers/matches-temporal-filter? :granule umm-temporal temporal))
           true))))

(defn collection-identifier-matches-granule?
  "Returns true if the collection identifier (a field in catalog item identities in ACLs) is nil or
  it matches the granule concept."
  [collection-identifier granule]
  (if collection-identifier
    (acl-matchers/coll-matches-collection-identifier? (:parent-collection granule) collection-identifier)
    true))

(defn acl-matches-granule?
  "Returns true if the acl matches the concept indicating the concept is permitted."
  [acl granule]
  (let [{{:keys [provider-id granule-identifier collection-identifier granule-applicable]} :catalog-item-identity} acl]
    (and granule-applicable
         (= provider-id (:provider-id granule))
         (granule-identifier-matches-granule? granule-identifier granule)
         (collection-identifier-matches-granule? collection-identifier granule))))

(defn- grants-concept-permission?
  "Returns true if permission keyword is granted on concept to any sids by given acl."
  [acl permission concept sids]
  (and (acl/acl-matches-sids-and-permission? sids (name permission) acl)
       (if (contains? provider-level-permissions permission)
         (when-let [acl-provider-id (-> acl :provider-object-identity :provider-id)]
           (= acl-provider-id (:provider-id concept)))
         ;; else check that the concept matches
         (case (:concept-type concept)
           :collection (acl-matchers/coll-applicable-acl? (:provider-id concept) concept acl)
           :granule (acl-matches-granule? acl concept)))))

(defn- concept-permissions-granted-by-acls
  "Returns the set of permission keywords (:read, :update, :order, or :delete) granted on concept
   to the seq of group guids by seq of acls."
  [concept sids acls]
  (collect-permissions #(grants-concept-permission? %1 %2 concept sids)
                       acls))

(defn- add-acl-enforcement-fields
  "Adds all fields necessary for comparing concept map against ACLs."
  [context concept]
  (let [concept (acl-matchers/add-acl-enforcement-fields-to-concept concept)]
    (if-let [parent-id (:collection-concept-id concept)]
      (assoc concept :parent-collection
                     (acl-matchers/add-acl-enforcement-fields-to-concept
                       (mdb/get-latest-concept context parent-id)))
      concept)))

(defn get-concept-permissions
  "Returns a map of concept ids to seqs of permissions granted on that concept for the given username."
  [context username-or-type concept-ids]
  (let [sids (get-sids context username-or-type)
        acls (get-echo-style-acls context)]
    (into {}
          (for [concept (mdb1/get-latest-concepts context concept-ids)
                :let [concept-with-acl-fields (add-acl-enforcement-fields context concept)]]
            [(:concept-id concept)
             (concept-permissions-granted-by-acls concept-with-acl-fields sids acls)]))))

(defn- system-permissions-granted-by-acls
  "Returns a set of permission keywords granted on the system target to the given sids by the given acls."
  [system-object-target sids acls]
  (collect-permissions (fn [acl permission]
                         (and (= system-object-target (:target (:system-object-identity acl)))
                              (acl/acl-matches-sids-and-permission? sids (name permission) acl)))
                       acls))

(defn get-system-permissions
  "Returns a map of the system object type to the set of permissions granted to the given username or user type."
  [context username-or-type system-object-target]
  (let [sids (get-sids context username-or-type)
        acls (get-echo-style-acls context)]
    (hash-map system-object-target (system-permissions-granted-by-acls system-object-target sids acls))))

(defn provider-permissions-granted-by-acls
  "Returns all permissions granted to provider target for given sids and acls."
  [provider-id target sids acls]
  (collect-permissions (fn [acl permission]
                         (and (= target (:target (:provider-object-identity acl)))
                              (acl/acl-matches-sids-and-permission? sids (name permission) acl)))
                       acls))

(defn get-provider-permissions
  "Returns a map of target object ids to permissions granted to the specified user for the specified provider id."
  [context username-or-type provider-id target]
  (let [sids (get-sids context username-or-type)
        acls (get-echo-style-acls context)]
    (hash-map target (provider-permissions-granted-by-acls provider-id target sids acls))))
