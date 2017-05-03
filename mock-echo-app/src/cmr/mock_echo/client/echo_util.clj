(ns cmr.mock-echo.client.echo-util
  "Contains helper functions for working with the echo mock"
  (:require
   [cmr.common.util :as util]
   [cmr.mock-echo.client.mock-echo-client :as echo-client]
   [cmr.transmit.access-control :as ac]
   [cmr.transmit.config :as config]
   [cmr.transmit.echo.tokens :as tokens]))

(defn reset
  "Resets the mock echo."
  [context]
  (echo-client/reset context))

(defn create-providers
  "Creates the providers in the mock echo."
  [context provider-guid-id-map]
  (echo-client/create-providers context provider-guid-id-map))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Token related

(defn login-guest
  "Logs in as a guest and returns the token"
  [context]
  (tokens/login-guest context))

(defn login
  "Logs in as the specified user and returns the token. No password needed because mock echo
  doesn't enforce passwords. Group guids can be optionally specified. The logged in user will
  be in the given groups."
  ([context user]
   (login context user nil))
  ([context user group-guids]
   (if group-guids
     (echo-client/login-with-group-access context user "password" group-guids)
     (tokens/login context user "password"))))

(defn logout
  "Logs out the specified token."
  [context token]
  (tokens/logout context token))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ACL related

;; Ingest management AKA admin granters
(def ingest-management-acl
  "An ACL for managing access to ingest management functions."
  "INGEST_MANAGEMENT_ACL")

(def tag-acl
  "An ACL for managing access to tag modification functions."
  "TAG_GROUP")

(defn coll-id
  "Creates an ACL collection identifier"
  ([entry-titles]
   (coll-id entry-titles nil))
  ([entry-titles access-value-filter]
   (coll-id entry-titles access-value-filter nil))
  ([entry-titles access-value-filter temporal]
   {:entry-titles entry-titles
    :access-value access-value-filter
    :temporal temporal}))

(defn gran-id
  "Creates an ACL granule identifier"
  ([access-value-filter]
   (gran-id access-value-filter nil))
  ([access-value-filter temporal]
   {:access-value access-value-filter
    :temporal temporal}))

(defn catalog-item-id
  "Creates a catalog item identity"
  ([provider-guid]
   (catalog-item-id provider-guid nil))
  ([provider-guid coll-identifier]
   (catalog-item-id provider-guid coll-identifier nil))
  ([provider-guid coll-identifier gran-identifier]
   {:provider-guid provider-guid
    :collection-identifier coll-identifier
    :granule-identifier gran-identifier}))

(defn coll-catalog-item-id
  "Creates a collection applicable catalog item identity"
  ([provider-guid]
   (coll-catalog-item-id provider-guid nil))
  ([provider-guid coll-identifier]
   (coll-catalog-item-id provider-guid coll-identifier nil))
  ([provider-guid coll-identifier gran-identifier]
   (assoc (catalog-item-id provider-guid coll-identifier gran-identifier)
          :collection-applicable true)))

(defn gran-catalog-item-id
  "Creates a granule applicable catalog item identity"
  ([provider-guid]
   (gran-catalog-item-id provider-guid nil))
  ([provider-guid coll-identifier]
   (gran-catalog-item-id provider-guid coll-identifier nil))
  ([provider-guid coll-identifier gran-identifier]
   (assoc (catalog-item-id provider-guid coll-identifier gran-identifier)
          :granule-applicable true)))

(defn grant
  "Creates an ACL in mock echo with the id, access control entries, identities"
  [context aces object-identity-type object-identity]
  (echo-client/create-acl context {:aces aces
                                   object-identity-type object-identity}))

(defn ungrant
  "Removes the acl"
  [context acl]
  (echo-client/delete-acl context (:id acl)))

(def guest-read-ace
  "A CMR style access control entry granting guests read access."
  {:permissions [:read]
   :user-type :guest})

(def registered-user-read-ace
  "A CMR style access control entry granting registered users read access."
  {:permissions [:read]
   :user-type :registered})

(def guest-read-write-ace
  "A CMR style access control entry granting guests read and write access."
  {:permissions [:read :create :update :delete]
   :user-type :guest})

(def registered-user-read-write-ace
  "A CMR style access control entry granting registered users read and writeaccess."
  {:permissions [:read :create :update :delete]
   :user-type :registered})

(defn group-ace
  "A CMR style access control entry granting users in a specific group read access."
  [group-guid permissions]
  {:permissions permissions
   :group-guid group-guid})

(defn grant-all-ingest
  "Creates an ACL in mock echo granting guests and registered users access to ingest for the given
  provider."
  [context provider-guid]
  (grant context
         [{:permissions [:read :update :delete]
           :user-type :guest}
          {:permissions [:read :update :delete]
           :user-type :registered}]
         :provider-object-identity
         {:target ingest-management-acl
          :provider-guid provider-guid}))

(defn grant-groups-ingest
  "Creates an ACL in mock echo granting the specified groups access to ingest for the given provider."
  [context provider-guid group-guids]
  (grant context
         (vec (for [guid group-guids]
                (group-ace guid [:update :delete])))
         :provider-object-identity
         {:target ingest-management-acl
          :provider-guid provider-guid}))

(defn grant-all-tag
  "Creates an ACL in mock echo granting registered users ability to tag anything"
  [context]
  (grant context
         [{:permissions [:create :update :delete]
           :user-type :registered}
          {:permissions [:create :update :delete]
           :user-type :guest}]
         :system-object-identity
         {:target tag-acl}))

(defn grant-create-read-groups
  "Creates an ACL in mock echo granting registered users and guests ability to create and read
  groups. If a provider id is provided this it permits it for the given provider. If not provided
  then it is at the system level."
  ([context]
   (grant context
          [{:permissions [:create :read] :user-type :registered}
           {:permissions [:create :read] :user-type :guest}]
          :system-object-identity
          {:target "GROUP"}))
  ([context provider-guid]
   (grant context
          [{:permissions [:create :read] :user-type :registered}
           {:permissions [:create :read] :user-type :guest}]
          :provider-object-identity
          {:target "GROUP"
           :provider-guid provider-guid})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Grant functions for Catalog Item ACLS

(defn grant-all
  "Creates an ACL in mock echo granting guests and registered users access to catalog items
  identified by the catalog-item-identity"
  [context catalog-item-identity]
  (grant context [guest-read-ace registered-user-read-ace] :catalog-item-identity catalog-item-identity))

(defn grant-guest
  "Creates an ACL in mock echo granting guests access to catalog items identified by the
  catalog-item-identity"
  [context catalog-item-identity]
  (grant context [guest-read-ace] :catalog-item-identity catalog-item-identity))

(defn grant-registered-users
  "Creates an ACL in mock echo granting all registered users access to catalog items identified by
  the catalog-item-identity"
  [context catalog-item-identity]
  (grant context [registered-user-read-ace] :catalog-item-identity catalog-item-identity))

(defn grant-group
  "Creates an ACL in mock echo granting users in the group access to catalog items identified by
  the catalog-item-identity"
  [context group-guid catalog-item-identity]
  (grant context [(group-ace group-guid [:read])] :catalog-item-identity catalog-item-identity))

(defn grant-group-admin
  "Creates an ACL in mock echo granting users in the group the given permissions for system ingest
  management.  If no permissions are provided the group is given read and update permission."
  [context group-guid & permission-types]
  (grant context [(group-ace group-guid (or (seq permission-types)
                                            [:read :update]))]
         :system-object-identity
         {:target ingest-management-acl}))

(defn grant-all-admin
  "Creates an ACL in mock echo granting all users read and update for system ingest management."
  [context]
  (grant context [guest-read-write-ace registered-user-read-write-ace]
         :system-object-identity
         {:target ingest-management-acl}))

(defn grant-group-provider-admin
  "Creates an ACL in mock echo granting users in the group the given permissions to ingest for the
  given provider.  If no permissions are provided the group is given update and delete permissions."
  [context group-guid provider-guid & permission-types]
  (grant context [(group-ace group-guid (or (seq permission-types)
                                            [:update :delete]))]
         :provider-object-identity
         {:target ingest-management-acl
          :provider-guid provider-guid}))

(defn grant-group-tag
  "Creates an ACL in mock echo granting users in the group the given permissions to modify tags.  If
   no permissions are provided the group is given create, update, and delete permissions."
  [context group-guid & permission-types]
  (grant context [(group-ace group-guid (or (seq permission-types)
                                            [:create :update :delete]))]
         :system-object-identity
         {:target tag-acl}))

(defn grant-system-group-permissions-to-group
  "Grants system-level access control group management permissions to given group."
  [context group-guid & permission-types]
  (grant context [(group-ace group-guid (or (seq permission-types)
                                            [:create :update :delete]))]
         :system-object-identity
         {:target "GROUP"}))

(defn grant-system-group-permissions-to-admin-group
  "Grants system-level access-control group management permissions for the admin group"
  [context & permission-types]
  (apply grant-system-group-permissions-to-group
   context (config/administrators-group-legacy-guid) permission-types))

(defn grant-group-instance-permissions-to-group
  [context group-guid target-group-guid & permission-types]
  (grant context [(group-ace group-guid (seq permission-types))]
         :single-instance-object-identity
         {:target "GROUP_MANAGEMENT"
          :target-guid target-group-guid}))

(defn grant-system-group-permissions-to-all
  "Grants all users all permissions for system level access control group management."
  [context]
  (grant context [guest-read-write-ace registered-user-read-write-ace]
         :system-object-identity
         {:target "GROUP"}))

(defn grant-provider-group-permissions-to-group
  "Grants provider-level access-control group management permissions for specified group-guid and provider-guid."
  [context group-guid provider-guid & permission-types]
  (grant context [(group-ace group-guid (or (seq permission-types)
                                            [:create :update :delete]))]
         :provider-object-identity
         {:target "GROUP"
          :provider-guid provider-guid}))

(defn grant-provider-group-permissions-to-admin-group
  "Grants provider-level access-control group management permissions for the admin group"
  [context provider-guid & permission-types]
  (apply grant-provider-group-permissions-to-group
   context (config/administrators-group-legacy-guid) provider-guid permission-types))

(defn grant-provider-group-permissions-to-all
  "Grants provider-level access-control group management to all users for all providers."
  [context provider-guid]
  (grant context [guest-read-write-ace registered-user-read-write-ace]
         :provider-object-identity
         {:target "GROUP"
          :provider-guid provider-guid}))
