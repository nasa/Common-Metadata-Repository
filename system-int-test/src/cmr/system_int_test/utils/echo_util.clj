(ns cmr.system-int-test.utils.echo-util
  "Contains helper functions for working with the echo mock"
  (:require [cmr.transmit.echo.mock :as mock]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.transmit.config :as config]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.utils.url-helper :as url]
            [clj-http.client :as client]))

(defn- context
  "Returns a context to pass to the mock transmit api"
  []
  {:system (s/system)})

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
  ([aces catalog-item-identity object-identity-type acl-type]
   (grant aces catalog-item-identity object-identity-type acl-type nil))
  ([aces catalog-item-identity object-identity-type acl-type provider-guid]
   (let [acl {:aces aces
              :catalog-item-identity catalog-item-identity
              object-identity-type (if (or acl-type provider-guid)
                                     (merge {}
                                            (when provider-guid {:provider-guid provider-guid})
                                            (when acl-type {:target acl-type}))
                                     nil)}]
     (mock/create-acl (context) acl))))

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
  (grant [guest-ace registered-user-ace] catalog-item-identity :system-object-identity nil))

(defn grant-all-ingest
  "Creates an ACL in mock echo granting guests and registered users access to ingest for the given
  provider."
  [provider-guid]
  (grant [{:permissions [:update :delete]
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
  [catalog-item-identity]
  (grant [guest-ace] catalog-item-identity :system-object-identity nil))

(defn grant-registered-users
  "Creates an ACL in mock echo granting all registered users access to catalog items identified by
  the catalog-item-identity"
  [catalog-item-identity]
  (grant [registered-user-ace] catalog-item-identity :system-object-identity nil))

(defn grant-group
  "Creates an ACL in mock echo granting users in the group access to catalog items identified by
  the catalog-item-identity"
  [group-guid catalog-item-identity]
  (grant [(group-ace group-guid [:read])] catalog-item-identity :system-object-identity nil))

(defn grant-group-admin
  "Creates an ACL in mock echo granting users in the group the given permissions for system ingest
  management.  If no permissions are provided the group is given read and update permission."
  [group-guid & permission-types]
  (grant [(group-ace group-guid (or (seq permission-types)
                                    [:read :update]))]
         nil
         :system-object-identity
         ingest-management-acl))

(defn grant-group-provider-admin
  "Creates an ACL in mock echo granting users in the group the given permissions to ingest for the
  given provider.  If no permissions are provided the group is given update and delete permissions."
  [group-guid provider-guid & permission-types]
  (grant [(group-ace group-guid (or (seq permission-types)
                                    [:update :delete]))]
         nil
         :provider-object-identity
         ingest-management-acl
         provider-guid))

(defn has-action-permission?
  "Attempts to perform the given action using the url and method with the token. Returns true
  if the action was successful."
  ([url method token]
   (has-action-permission? url method token nil nil))
  ([url method token headers]
   (has-action-permission? url method token headers nil))
  ([url method token headers body]
   (let [response (client/request {:url url
                                   :method method
                                   :query-params {:token token}
                                   :headers headers
                                   :body body
                                   :connection-manager (url/conn-mgr)
                                   :throw-exceptions false})
         status (:status response)]

     ;; Make sure the status returned is success or 401
     (when (or (and (>= status 300)
                    (not= status 401))
               (< status 200))
       (throw (Exception. (str "Unexpected status " status " response:" (:body response)))))
     (not= status 401))))


