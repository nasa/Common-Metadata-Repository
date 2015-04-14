(ns cmr.mock-echo.client.echo-util
  "Contains helper functions for working with the echo mock"
  (:require [cmr.mock-echo.client.mock-echo-client :as echo-client]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.transmit.config :as config]
            [clj-http.client :as client]
            [cmr.common.util :as util]))

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

(defn coll-id
  "Creates an ACL collection identifier"
  ([entry-titles]
   (coll-id entry-titles nil))
  ([entry-titles access-value-filter]
   {:entry-titles entry-titles
    :access-value access-value-filter}))

(defn gran-id
  "Creates an ACL granule identifier"
  [access-value-filter]
  {:access-value access-value-filter})

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
  ([context aces catalog-item-identity object-identity-type acl-type]
   (grant context aces catalog-item-identity object-identity-type acl-type nil))
  ([context aces catalog-item-identity object-identity-type acl-type provider-guid]
   (let [acl {:aces aces
              :catalog-item-identity catalog-item-identity
              object-identity-type (util/remove-nil-keys {:provider-guid provider-guid
                                                          :target acl-type})}]
     (echo-client/create-acl context acl))))

(defn ungrant
  "Removes the acl"
  [context acl]
  (echo-client/delete-acl context (:id acl)))

(def guest-ace
  "A CMR style access control entry granting guests read access."
  {:permissions [:read]
   :user-type :guest})

(def registered-user-ace
  "A CMR style access control entry granting registered users read access."
  {:permissions [:read]
   :user-type :registered})

(defn group-ace
  "A CMR style access control entry granting users in a specific group read access."
  [group-guid permissions]
  {:permissions permissions
   :group-guid group-guid})

(defn grant-all
  "Creates an ACL in mock echo granting guests and registered users access to catalog items
  identified by the catalog-item-identity"
  [context catalog-item-identity]
  (grant context [guest-ace registered-user-ace] catalog-item-identity :system-object-identity nil))

(defn grant-all-ingest
  "Creates an ACL in mock echo granting guests and registered users access to ingest for the given
  provider."
  [context provider-guid]
  (grant context
         [{:permissions [:update :delete]
           :user-type :guest}
          {:permissions [:update :delete]
           :user-type :registered}]
         nil
         :provider-object-identity
         ingest-management-acl
         provider-guid))

(defn grant-guest
  "Creates an ACL in mock echo granting guests access to catalog items identified by the
  catalog-item-identity"
  [context catalog-item-identity]
  (grant context [guest-ace] catalog-item-identity :system-object-identity nil))

(defn grant-registered-users
  "Creates an ACL in mock echo granting all registered users access to catalog items identified by
  the catalog-item-identity"
  [context catalog-item-identity]
  (grant context [registered-user-ace] catalog-item-identity :system-object-identity nil))

(defn grant-group
  "Creates an ACL in mock echo granting users in the group access to catalog items identified by
  the catalog-item-identity"
  [context group-guid catalog-item-identity]
  (grant context [(group-ace group-guid [:read])] catalog-item-identity :system-object-identity nil))

(defn grant-group-admin
  "Creates an ACL in mock echo granting users in the group the given permissions for system ingest
  management.  If no permissions are provided the group is given read and update permission."
  [context group-guid & permission-types]
  (grant context [(group-ace group-guid (or (seq permission-types)
                                            [:read :update]))]
         nil
         :system-object-identity
         ingest-management-acl))

(defn grant-group-provider-admin
  "Creates an ACL in mock echo granting users in the group the given permissions to ingest for the
  given provider.  If no permissions are provided the group is given update and delete permissions."
  [context group-guid provider-guid & permission-types]
  (grant context [(group-ace group-guid (or (seq permission-types)
                                            [:update :delete]))]
         nil
         :provider-object-identity
         ingest-management-acl
         provider-guid))


