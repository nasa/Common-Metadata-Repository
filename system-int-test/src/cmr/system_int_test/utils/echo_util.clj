(ns cmr.system-int-test.utils.echo-util
  "Contains helper functions for working with the echo mock"
  (:require [cmr.transmit.echo.mock :as mock]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.transmit.config :as config]))

(def context-atom
  "Cached context"
  (atom nil))

(defn- context
  "Returns a context to pass to the mock transmit api"
  []
  (when-not @context-atom
    (reset! context-atom {:system (config/system-with-connections {} [:echo-rest])}))
  @context-atom)


(defn reset
  "Resets the mock echo."
  []
  (mock/reset (context)))

(defn create-providers
  "Creates the providers in the mock echo."
  [provider-guid-id-map]
  (mock/create-providers (context) provider-guid-id-map))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Token related

(defn login-guest
  "Logs in as a guest and returns the token"
  []
  (tokens/login-guest (context)))

(defn login
  "Logs in as the specified user and returns the token. No password needed because mock echo
  doesn't enforce passwords. Group guids can be optionally specified. The logged in user will
  be in the given groups."
  ([user]
   (login user nil))
  ([user group-guids]
   (if group-guids
     (mock/login-with-group-access (context) user "password" group-guids)
     (tokens/login (context) user "password"))))

(defn logout
  "Logs out the specified token."
  [token]
  (tokens/logout (context) token))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ACL related

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
  [aces catalog-item-identity system-object-identity]
  (let [acl {:aces aces
             :catalog-item-identity catalog-item-identity
             :system-object-identity system-object-identity}]
    (mock/create-acl (context) acl)))

(defn ungrant
  "Removes the acl"
  [acl]
  (mock/delete-acl (context) (:id acl)))

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
  [catalog-item-identity]
  (grant [guest-ace registered-user-ace] catalog-item-identity nil))

(defn grant-guest
  "Creates an ACL in mock echo granting guests access to catalog items identified by the
  catalog-item-identity"
  [catalog-item-identity]
  (grant [guest-ace] catalog-item-identity nil))

(defn grant-registered-users
  "Creates an ACL in mock echo granting all registered users access to catalog items identified by the
  catalog-item-identity"
  [catalog-item-identity]
  (grant [registered-user-ace] catalog-item-identity nil))

(defn grant-group
  "Creates an ACL in mock echo granting users in the group access to catalog items identified by the
  catalog-item-identity"
  [group-guid catalog-item-identity]
  (grant [(group-ace group-guid [:read])] catalog-item-identity nil))


;; Ingest management AKA admin granters
(def ingest-management-system-object-identity
  "A system object identity for the ingest management acl which is used for managing admin access."
  {:target "INGEST_MANAGEMENT_ACL"})

(defn grant-group-admin
  [group-guid & permission-types]
  (grant [(group-ace group-guid (or (seq permission-types)
                                    [:read :update]))]
         nil
         ingest-management-system-object-identity))


