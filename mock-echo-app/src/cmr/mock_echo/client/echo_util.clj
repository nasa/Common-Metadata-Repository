(ns cmr.mock-echo.client.echo-util
  "Contains helper functions for working with the echo mock"
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.util :as util]
   [cmr.mock-echo.client.mock-echo-client :as echo-client]
   [cmr.mock-echo.client.mock-urs-client :as urs-client]
   [cmr.transmit.access-control :as ac]
   [cmr.transmit.config :as config]
   [cmr.transmit.echo.conversion :as c]
   [cmr.transmit.echo.tokens :as tokens]))

(defn reset
  "Resets the mock echo."
  [context]
  (echo-client/reset context))

(defn create-providers
  "Creates the providers in the mock echo."
  [context provider-guid-id-map]
  (echo-client/create-providers context provider-guid-id-map))

(defn get-or-create-group
  "Gets a group or creates it if it does not exist"
  ([context group-name]
   (get-or-create-group context group-name (config/echo-system-token)))
  ([context group-name token]
   (let [existing-group (-> (ac/search-for-groups context
                               {:name group-name}
                               {:raw? true :token token})
                            (get-in [:body :items])
                            (first))
         ;; Return the existing group if it exists, otherwise create a new one
         group (or existing-group (:body (ac/create-group context
                                          {:name group-name
                                           :description group-name}
                                          {:raw? true :token token})))]
     (:concept_id group))))

(defn add-user-to-group
  "Adds the user to an access-control group"
  [context group-concept-id user-name token]
  (ac/add-members context group-concept-id [user-name] {:raw? true :token token}))

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
  ([context user group-concept-ids]
   (urs-client/create-users context [{:username user
                                      :password (str user "Pass0")}])

   (if group-concept-ids
     (do
       (doseq [group-concept-id group-concept-ids]
           (add-user-to-group context group-concept-id user (config/echo-system-token)))
       (echo-client/login-with-group-access context user "password" group-concept-ids))
     (tokens/login context user "password"))))

(defn logout
  "Logs out the specified token."
  [context token]
  (tokens/logout context token))

(def LAUNCHPAD_TOKEN_PADDING
  "Padding to make a regular token into launchpad token
   (i.e. make it longer than max length of URS token)."
  (string/join (repeat lt-validation/URS_TOKEN_MAX_LENGTH "Z")))

(defn login-with-launchpad-token
  "Logs in as the specified user and returns the launchpad token.
   This is a wrapper around the login function and just pad the returned URS token to
   the length of a launchpad token."
  ([context user]
   (login-with-launchpad-token context user nil))
  ([context user group-concept-ids]
   (let [token (login context user group-concept-ids)]
     (str token LAUNCHPAD_TOKEN_PADDING))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ACL related

;; Ingest management AKA admin granters
(def ingest-management-acl
  "An ACL for managing access to ingest management functions."
  "INGEST_MANAGEMENT_ACL")

;; subscription management acl
(def subscription-management
  "An ACL for managing access to subscription functions."
  "SUBSCRIPTION_MANAGEMENT")

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
   (util/remove-nil-keys
    {:entry_titles entry-titles
     :access_value access-value-filter
     :temporal temporal})))

(defn gran-id
  "Creates an ACL granule identifier"
  ([access-value-filter]
   (gran-id access-value-filter nil))
  ([access-value-filter temporal]
   (util/remove-nil-keys
     {:access_value access-value-filter
      :temporal temporal})))

(defn catalog-item-id
  "Creates a catalog item identity"
  ([provider-guid]
   (catalog-item-id provider-guid nil))
  ([provider-guid coll-identifier]
   (catalog-item-id provider-guid coll-identifier nil))
  ([provider-guid coll-identifier gran-identifier]
   (util/remove-nil-keys
     {:provider_id provider-guid
      :collection_identifier coll-identifier
      :granule_identifier gran-identifier})))

(defn coll-catalog-item-id
  "Creates a collection applicable catalog item identity"
  ([provider-guid]
   (coll-catalog-item-id provider-guid nil))
  ([provider-guid coll-identifier]
   (coll-catalog-item-id provider-guid coll-identifier nil))
  ([provider-guid coll-identifier gran-identifier]
   (assoc (catalog-item-id provider-guid coll-identifier gran-identifier)
          :collection_applicable true)))

(defn gran-catalog-item-id
  "Creates a granule applicable catalog item identity"
  ([provider-guid]
   (gran-catalog-item-id provider-guid nil))
  ([provider-guid coll-identifier]
   (gran-catalog-item-id provider-guid coll-identifier nil))
  ([provider-guid coll-identifier gran-identifier]
   (assoc (catalog-item-id provider-guid coll-identifier gran-identifier)
          :granule_applicable true)))

(defn- cmr-catalog-identity->echo
  "Converts a cmr catalog-item-identity into the format used by mock-echo"
  [identity]
  (let [{:keys [name provider_id collection_applicable granule_applicable]} identity
        coll-access-value (-> (get-in identity [:collection_identifier :access_value])
                              (clojure.set/rename-keys {:include_undefined_value :include-undefined}))
        gran-access-value (-> (get-in identity [:granule_identifier :access_value])
                              (clojure.set/rename-keys {:include_undefined_value :include-undefined}))
        coll-temporal (-> (get-in identity [:collection_identifier :temporal])
                          (clojure.set/rename-keys {:stop-date :end-date}))
        gran-temporal (-> (get-in identity [:granule_identifier :temporal])
                          (clojure.set/rename-keys {:stop-date :end-date}))
        entry-titles (get-in identity [:collection_identifier :entry_titles])]
    (-> {:name name
         :provider-guid provider_id
         :collection-applicable collection_applicable
         :granule-applicable granule_applicable
         :collection-identifier (util/remove-nil-keys
                                  {:entry-titles entry-titles
                                   :access-value coll-access-value
                                   :temporal (if coll-temporal (assoc coll-temporal :temporal-field :acquisition))})
         :granule-identifier (util/remove-nil-keys
                              {:access-value gran-access-value
                               :temporal (if gran-temporal (assoc gran-temporal :temporal-field :acquisition))})}
        util/remove-nil-keys)))


(defn grant
  "Creates an ACL in mock echo with the id, access control entries, identities"
  [context group-permissions object-identity-type object-identity]
  (let [cmr-acl (if (= object-identity-type :catalog_item_identity)
                  (util/map-keys->snake_case {:group_permissions group-permissions
                                              object-identity-type (util/remove-nil-keys (merge {:name (str (java.util.UUID/randomUUID))}
                                                                                                object-identity))})
                  {:group_permissions group-permissions
                   object-identity-type object-identity})
        echo-identity (if (= object-identity-type :catalog_item_identity) (cmr-catalog-identity->echo object-identity) object-identity)
        ;;attempt to create ACL.  If it already exists, then get the existing ACL and update.
        cmr-response (ac/create-acl context cmr-acl {:raw? true :token (config/echo-system-token)})
        cmr-response (if (= 409 (:status cmr-response))
                       (let [existing-concept-id (->> (get-in cmr-response [:body :errors])
                                                      first
                                                      (re-find #"\[([\w\d-]+)\]")
                                                      second)
                             existing-concept (:body
                                               (ac/get-acl context
                                                           existing-concept-id
                                                           {:raw? true
                                                            :token (config/echo-system-token)}))
                             updated-concept (assoc existing-concept
                                                    :group_permissions
                                                    (into (:group_permissions existing-concept)
                                                          group-permissions))]

                         (assoc
                          (ac/update-acl context
                                         existing-concept-id
                                         updated-concept
                                         {:raw? true
                                          :token (config/echo-system-token)})
                          :acl updated-concept))
                       cmr-response)
        group-permissions (or (get-in cmr-response [:acl :group_permissions]) group-permissions)
        echo-acl (-> {:aces (map #(clojure.set/rename-keys % {:user_type :user-type :group_id :group-guid}) group-permissions)
                      object-identity-type (clojure.set/rename-keys echo-identity {:provider_id :provider-guid  :collection_identifier :collection-identifier})
                      :id (get-in cmr-response [:body :concept_id])}
                     (set/rename-keys {:system_identity :system-object-identity :provider_identity :provider-object-identity :catalog_item_identity :catalog-item-identity}))
        ;; Dont save to ECHO if CMR create fails
        echo-acl-response (if (< (:status cmr-response) 300)
                            (echo-client/create-acl context echo-acl)
                            (info "Failed to ingest ACL to access-control: " cmr-response))]
    (get-in cmr-response [:body :concept_id])))

(defn ungrant
  "Removes the acl"
  [context acl]
  (ac/delete-acl context acl {:raw? true :token (config/echo-system-token)})
  (echo-client/delete-acl context acl))

(defn get-acls-by-type-and-target
  "Get the GROUP ACLs set up for providers in fixtures.  Return in format used for test assertions"
  ([context type target]
   (get-acls-by-type-and-target context type target {}))
  ([context type target options]
   (->> (ac/search-for-acls (assoc context :token (config/echo-system-token)) (merge {:identity_type type :target target :include_full_acl true} options))
        :items
        (map #(assoc (:acl %) :revision_id (:revision_id %) :concept_id (:concept_id %))))))

(defn get-provider-group-acls
  "Get the GROUP ACLs set up for providers in fixtures.  Return in format used for test assertions"
  ([context]
   (get-provider-group-acls context {}))
  ([context options]
   (get-acls-by-type-and-target context "PROVIDER" "GROUP" options)))

(defn get-system-group-acls
  "Get the GROUP ACLs set up for system in fixtures.  Return in format used for test assertions"
  ([context]
   (get-system-group-acls context {}))
  ([context options]
   (get-acls-by-type-and-target context "SYSTEM" "GROUP" options)))

(defn get-catalog-item-acls
  "Get the CATALOG_ITEM ACLs set up for system in fixtures.  Return in format used for test assertions"
  ([context]
   (get-catalog-item-acls context {}))
  ([context options]
   (->> (ac/search-for-acls (assoc context :token (config/echo-system-token)) (merge {:identity_type "catalog_item" :include_full_acl true} options))
        :items
        (map #(assoc (:acl %) :revision_id (:revision_id %) :concept_id (:concept_id %))))))

(defn get-admin-group
  "Gets the system administrator group"
  [context]
  (->> (ac/search-for-groups context {:legacy_guid (config/administrators-group-legacy-guid) :token (config/echo-system-token)})
       :items
       (map #(assoc (:acl %) :revision_id (:revision_id %) :concept_id (:concept_id %)))
       (first)))

(def guest-read-ace
  "A CMR style access control entry granting guests read access."
  {:permissions [:read]
   :user_type :guest})

(def registered-user-read-ace
  "A CMR style access control entry granting registered users read access."
  {:permissions [:read]
   :user_type :registered})

(def guest-read-write-ace
  "A CMR style access control entry granting guests create and read access."
  {:permissions [:read :create]
   :user_type :guest})

(def guest-read-update-ace
  "A CMR style access control entry granting guests create and read access."
  {:permissions [:read :update]
   :user_type :guest})

(def registered-user-read-write-ace
  "A CMR style access control entry granting registered users create and read access."
  {:permissions [:read :create]
   :user_type :registered})

(def registered-user-read-update-ace
  "A CMR style access control entry granting registered users create and read access."
  {:permissions [:read :update]
   :user_type :registered})

(defn group-ace
  "A CMR style access control entry granting users in a specific group read access."
  [group-guid permissions]
  {:permissions permissions
   :group_id group-guid})

(defn grant-all-ingest
  "Creates an ACL in mock echo granting guests and registered users access to ingest for the given
  provider."
  [context provider-guid]
  (grant context
         [{:permissions [:update :read]
           :user_type :guest}
          {:permissions [:update :read]
           :user_type :registered}]
         :provider_identity
         {:target ingest-management-acl
          :provider_id provider-guid}))

(defn grant-registered-ingest
  "Creates an ACL in mock echo granting registered users access to ingest for the given provider."
  [context provider-guid]
  (grant context
         [{:permissions [:update :read]
           :user_type :registered}]
         :provider_identity
         {:target ingest-management-acl
          :provider_id provider-guid}))

(defn grant-groups-ingest
  "Creates an ACL in mock echo granting the specified groups access to ingest for the given provider."
  [context provider-guid group-guids]
  (grant context
         (vec (for [guid group-guids]
                (group-ace guid [:update :read])))
         :provider_identity
         {:target ingest-management-acl
          :provider_id provider-guid}))

(defn grant-all-tag
  "Creates an ACL in mock echo granting registered users ability to tag anything"
  [context]
  (grant context
         [{:permissions [:create :update :delete]
           :user_type :registered}
          {:permissions [:create :update :delete]
           :user_type :guest}]
         :system_identity
         {:target tag-acl}))

(defn grant-all-variable
  "Creates an ACL in mock echo granting registered users ability to do all
  variable related operations"
  [context]
  (grant context
         [{:permissions [:read :update]
           :user_type :registered}
          {:permissions [:read :update]
           :user_type :guest}]
         :system_identity
         {:target ingest-management-acl}))

(def grant-all-service
  "Creates an ACL in mock echo granting registered users ability to do all
  service related operations"
  grant-all-variable)

(def grant-all-tool
  "Creates an ACL in mock echo granting registered users ability to do all
  tool related operations"
  grant-all-variable)

(def grant-all-subscription-ima
  "Creates an ingest-management-acl in mock echo granting guest and registered users ability to do all
  subscription related operations"
  grant-all-variable)

(defn grant-all-subscription-sm
  "Creates a SUBSCRIPTION_MANAGEMENT acl in mock echo granting guest and registered users ability to do all
  subscription related operations"
  [context provider-guid guest-permissions registered-permissions]
  (grant context
         [{:permissions guest-permissions
           :user_type :guest}
          {:permissions registered-permissions
           :user_type :registered}]
         :provider_identity
         {:target subscription-management
          :provider_id provider-guid}))

(defn grant-all-subscription-group-sm
  "Creates a SUBSCRIPTION_MANAGEMENT acl in mock echo granting group ability to do all
  subscription related operations"
  [context provider-guid group-id group-permissions]
  (grant context
         [{:permissions group-permissions
           :group_id group-id}]
         :provider_identity
         {:target subscription-management
          :provider_id provider-guid}))

(defn grant-create-read-groups
  "Creates an ACL in mock echo granting registered users and guests ability to create and read
  groups. If a provider id is provided this it permits it for the given provider. If not provided
  then it is at the system level."
  ([context]
   (grant context
          [{:permissions [:create :read] :user_type :registered}
           {:permissions [:create :read] :user_type :guest}]
          :system_identity
          {:target "GROUP"}))
  ([context provider-guid]
   (grant context
          [{:permissions [:create :read] :user_type :registered}
           {:permissions [:create :read] :user_type :guest}]
          :provider_identity
          {:target "GROUP"
           :provider_id provider-guid})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Grant functions for Catalog Item ACLS

(defn grant-all
  "Creates an ACL in mock echo granting guests and registered users access to catalog items
  identified by the catalog-item-identity"
  [context catalog-item-identity]
  (grant context [guest-read-ace registered-user-read-ace] :catalog_item_identity catalog-item-identity))

(defn grant-guest
  "Creates an ACL in mock echo granting guests access to catalog items identified by the
  catalog-item-identity"
  [context catalog-item-identity]
  (grant context [guest-read-ace] :catalog_item_identity catalog-item-identity))

(defn grant-registered-users
  "Creates an ACL in mock echo granting all registered users access to catalog items identified by
  the catalog-item-identity"
  [context catalog-item-identity]
  (grant context [registered-user-read-ace] :catalog_item_identity catalog-item-identity))

(defn grant-group
  "Creates an ACL in mock echo granting users in the group access to catalog items identified by
  the catalog-item-identity"
  [context group-guid catalog-item-identity]
  (grant context [(group-ace group-guid [:read])] :catalog_item_identity catalog-item-identity))

(defn grant-group-admin
  "Creates an ACL in mock echo granting users in the group the given
  permissions for system ingest management. If no permissions are provided the
  group is given read and update permission.

  Note that not all services have all permissions. In some cases, a service
  allows for concepts to be created, but doesn't actually have a :create
  permission enabled. In such circumstances, the group getting admin
  permissions will need to be granted :update."
  [context group-guid & permission-types]
  (grant context [(group-ace group-guid (or (seq (remove #{:delete} permission-types))
                                            [:read :update]))]
         :system_identity
         {:target ingest-management-acl}))

(defn grant-all-admin
  "Creates an ACL in mock echo granting all users read and update for system ingest management."
  [context]
  (grant context [guest-read-update-ace registered-user-read-update-ace]
         :system_identity
         {:target ingest-management-acl}))

(defn grant-group-provider-admin
  "Creates an ACL in mock echo granting users in the group the given permissions to ingest for the
  given provider.  If no permissions are provided the group is given update and delete permissions."
  [context group-guid provider-guid & permission-types]
  (grant context [(group-ace group-guid (or (seq (remove #{:delete} permission-types))
                                            [:update :read]))]
         :provider_identity
         {:target ingest-management-acl
          :provider_id provider-guid}))

(defn grant-group-tag
  "Creates an ACL in mock echo granting users in the group the given permissions to modify tags.  If
   no permissions are provided the group is given create, update, and delete permissions."
  [context group-guid & permission-types]
  (grant context [(group-ace group-guid (or (seq permission-types)
                                            [:create :update :delete]))]
         :system_identity
         {:target tag-acl}))

(defn grant-system-group-permissions-to-group
  "Grants system-level access control group management permissions to given group."
  [context group-guid & permission-types]
  (grant context [(group-ace group-guid (or (seq (remove #{:update} permission-types))
                                            [:create :read]))]
         :system_identity
         {:target "GROUP"}))

(defn grant-system-group-permissions-to-admin-group
  "Grants system-level access-control group management permissions for the admin group"
  [context & permission-types]
  (apply grant-system-group-permissions-to-group
   context (:concept_id (get-admin-group context)) permission-types))

(defn grant-group-instance-permissions-to-group
  [context group-guid target-group-guid & permission-types]
  (grant context [(group-ace group-guid (seq permission-types))]
         :single_instance_object_identity
         {:target "GROUP_MANAGEMENT"
          :target_guid target-group-guid}))

(defn grant-system-group-permissions-to-all
  "Grants all users all permissions for system level access control group management."
  [context]
  (grant context [guest-read-write-ace registered-user-read-write-ace]
         :system_identity
         {:target "GROUP"}))

(defn grant-provider-group-permissions-to-group
  "Grants provider-level access-control group management permissions for specified group-guid and provider-guid."
  [context group-guid provider-guid & permission-types]
  (grant context [(group-ace group-guid (or (seq (remove #{:update} permission-types))
                                            [:create :read]))]
         :provider_identity
         {:target "GROUP"
          :provider_id provider-guid}))

(defn grant-provider-group-permissions-to-admin-group
  "Grants provider-level access-control group management permissions for the admin group"
  [context provider-guid & permission-types]
  (apply grant-provider-group-permissions-to-group
   context (:concept_id (get-admin-group context)) provider-guid permission-types))

(defn grant-provider-group-permissions-to-all
  "Grants provider-level access-control group management to all users for all providers."
  [context provider-guid]
  (grant context [guest-read-write-ace registered-user-read-write-ace]
         :provider_identity
         {:target "GROUP"
          :provider_id provider-guid}))

(defn grant-permitted?
  "Check if a given grant id is in the list of provided ACLs."
  [grant-id acls]
  (contains?
   (into
    #{}
    (map :guid acls))
   grant-id))

(defn group-permitted?
  "Check if a given group id is in the list of provided ACLs."
  [group-id acls]
  (contains?
   (reduce
    #(into %1 (map :group-guid %2))
    #{}
    (map :aces acls))
   group-id))

(defn permitted?
  "Check if a the ACLs for the given token include the given grant and group IDs."
  [token grant-id group-id acls]
  (and (grant-permitted? grant-id acls)
       (group-permitted? group-id acls)))

(defn not-permitted?
  [& args]
  (not (apply permitted? args)))
