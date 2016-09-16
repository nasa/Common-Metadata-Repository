(ns cmr.access-control.services.acl-service-util
  "Contains common utility functions used in ACL service"
  (:require
    [clojure.edn :as edn]
    [clojure.set :as set]
    [cmr.access-control.data.acl-schema :as schema]
    [cmr.access-control.services.acl-service-messages :as acl-msg]
    [cmr.access-control.services.group-service :as groups]
    [cmr.common.concepts :as concepts]
    [cmr.common.date-time-parser :as dtp]
    [cmr.common.services.errors :as errors]
    [cmr.access-control.services.messages :as msg]
    [cmr.acl.core :as acl]
    [cmr.common.util :as util]
    [cmr.transmit.echo.tokens :as tokens]
    [cmr.transmit.metadata-db :as mdb1]
    [cmr.transmit.metadata-db2 :as mdb]
    [cmr.umm.acl-matchers :as acl-matchers]))

(defn- context->user-id
  "Returns user id of the token in the context. Throws an error if no token is provided"
  [context]
  (if-let [token (:token context)]
    (tokens/get-user-id context (:token context))
    (errors/throw-service-error :unauthorized msg/token-required)))

(defn get-sids
  "Returns a seq of sids for the given username string or user type keyword
   for use in checking permissions against acls."
  [context username-or-type]
  (cond
    (contains? #{"guest" "registered"} (name username-or-type)) [username-or-type]
    (string? username-or-type) (concat [:registered]
                                       (->> (groups/search-for-groups context {:member username-or-type})
                                            :results
                                            :items
                                            (map :concept_id)))))

(defn fetch-acl-concept
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

(defn echo-style-acl
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
           (when-let [umm-temporal (util/lazy-get granule :temporal)]
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
       (case (:concept-type concept)
         :collection (acl-matchers/coll-applicable-acl? (:provider-id concept) concept acl)
         :granule (acl-matches-granule? acl concept))))

(defn- provider-acl?
  "Returns true if the ECHO-style acl specifically identifies the given provider id."
  [provider-id acl]
  (or
    (-> acl :provider-object-identity :provider-id (= provider-id))
    (-> acl :catalog-item-identity :provider-id (= provider-id))))

(defn- ingest-management-acl?
  "Returns true if the ACL targets a provider INGEST_MANAGEMENT_ACL."
  [acl]
  (-> acl :provider-object-identity :target (= schema/ingest-management-acl-target)))

(defn- concept-permissions-granted-by-acls
  "Returns the set of permission keywords (:read, :order, and :update) granted on concept
   to the seq of group guids by seq of acls."
  [concept sids acls]
  (let [provider-acls (filter #(provider-acl? (:provider-id concept) %) acls)
        ;; When a user has UPDATE on the provider's INGEST_MANAGEMENT_ACL target, then they have UPDATE and
        ;; DELETE permission on all of the provider's catalog items.
        ingest-management-permissions (when (some #(acl/acl-matches-sids-and-permission? sids "update" %)
                                                  (filter ingest-management-acl? provider-acls))
                                        [:update :delete])
        ;; The remaining catalog item ACLs can only grant READ or ORDER permission.
        catalog-item-acls (filter :catalog-item-identity provider-acls)
        catalog-item-permissions (for [permission [:read :order]
                                       :when (some #(grants-concept-permission? % permission concept sids)
                                                   catalog-item-acls)]
                                   permission)]
    (set
      (concat catalog-item-permissions
              ingest-management-permissions))))

(defn- add-acl-enforcement-fields
  "Adds all fields necessary for comparing concept map against ACLs."
  [context concept]
  (let [concept (acl-matchers/add-acl-enforcement-fields-to-concept concept)]
    (if-let [parent-id (:collection-concept-id concept)]
      (assoc concept :parent-collection
                     (acl-matchers/add-acl-enforcement-fields-to-concept
                       (mdb/get-latest-concept context parent-id)))
      concept)))

(defn get-catalog-item-permissions
  "Returns a map of concept ids to seqs of permissions granted on that concept for the given username."
  [context username-or-type concept-ids]
  (let [sids (get-sids context username-or-type)
        acls (get-echo-style-acls context)]
    (into {}
          (for [concept (mdb1/get-latest-concepts context concept-ids)
                :let [concept-with-acl-fields (add-acl-enforcement-fields context concept)]]
            [(:concept-id concept)
             (concept-permissions-granted-by-acls concept-with-acl-fields sids acls)]))))

(defn system-permissions-granted-by-acls
  "Returns a set of permission keywords granted on the system target to the given sids by the given acls."
  [system-object-target sids acls]
  (let [relevant-acls (filter #(-> % :system-object-identity :target (= system-object-target))
                              acls)]
    (set
      (for [permission [:create :read :update :delete]
            :when (some #(acl/acl-matches-sids-and-permission? sids (name permission) %)
                        relevant-acls)]
        permission))))

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
