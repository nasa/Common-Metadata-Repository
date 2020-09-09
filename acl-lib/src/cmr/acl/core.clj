(ns cmr.acl.core
  "Contains code for retrieving and manipulating ACLs."
  (:require
   [cheshire.core :as json]
   [clojure.core.cache :as clj-cache]
   [clojure.set :as set]
   [clojure.string :as str]
   [cmr.acl.acl-fetcher :as acl-fetcher]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.date-time-parser :as dtp]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.transmit.access-control :as access-control]
   [cmr.transmit.config :as tc]
   [cmr.transmit.config :as transmit-config]))

(def BROWSER_CLIENT_ID "browser")
(def CURL_CLIENT_ID "curl")
(def UNKNOWN_CLIENT_ID "unknown")

(defn get-token
  "Returns the token the user passed in the headers or parameters"
  [params headers]
  (let [non-empty-string #(when-not (str/blank? %) %)]
    (or (non-empty-string (get headers tc/authorization-header))
        (non-empty-string (:token params))
        (non-empty-string (get headers tc/token-header)))))

(defn- get-client-id
  "Gets the client id passed by the client or tries to determine it from other headers"
  [headers]
  (or (get headers "client-id")
      (when-let [user-agent (get headers "user-agent")]
        (cond
          (or (re-find #"^Mozilla.*" user-agent) (re-find #"^Opera.*" user-agent))
          BROWSER_CLIENT_ID
          (re-find #"^curl.*" user-agent)
          CURL_CLIENT_ID))
      UNKNOWN_CLIENT_ID))

(defn- add-authentication-to-context
  "Adds information to the context including the current token and the client id"
  [context params headers]
  (-> context
      (assoc :token (get-token params headers))
      (assoc :client-id (get-client-id headers))))

(defn add-authentication-handler
  "This is a ring handler that adds the authentication token and client id to the request context.
  It expects the request context is already associated with the request."
  [f]
  (fn [request]
    (let [{:keys [request-context params headers]} request]
      (f (update-in request [:request-context] add-authentication-to-context params headers)))))

(defn- request-sids
  "Gets the current sids from access control and parses the returned json into a seq."
  [context]
  (let [{:keys [token]} context]
    (if token
      (->
        (access-control/get-current-sids context (:token context))
        json/parse-string)
      [:guest])))

(defn context->sids
  "Returns the security identifiers (group guids and :guest or :registered) of the user identified
  by the token in the context. Search app adds the sids to the context so before making the call
  to get sids, check if they are stored on the context."
  [context]
  (or (util/get-real-or-lazy context :sids)
      (request-sids context)))

(defn echo-style-temporal-identifier
  "Returns an ECHO-style ACL temporal identifier from a CMR-style ACL temporal identifier"
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

(defn- ace-matches-sid?
  "Returns true if the ACE is applicable to the SID."
  [sid group-permission]
  (or
    (= (keyword sid) (keyword (:user-type group-permission)))
    (= sid (:group-id group-permission))))

(defn acl-matches-sids-and-permission?
  "Returns true if the acl is applicable to any of the sids."
  [sids permission acl]
  (some (fn [sid]
          (some (fn [group-permission]
                  (and (ace-matches-sid? sid group-permission)
                       (some #(= % (name permission)) (:permissions group-permission))))
                (:group-permissions acl)))
        sids))

(def token-imp-cache-key
  "The cache key for the token to ingest management permission cache."
  :token-imp)

(def token-smp-cache-key
  "The cache key for the token to subscription management permission cache."
  :token-smp)

(def TOKEN_IMP_CACHE_TIME
  "The number of milliseconds token information will be cached."
  (* 5 60 1000))

(defn create-token-imp-cache
  "Creates a cache for which tokens have ingest management permission."
  []
  (mem-cache/create-in-memory-cache :ttl {} {:ttl TOKEN_IMP_CACHE_TIME}))

(defn create-token-smp-cache
  "Creates a cache for which tokens have subscription management permission."
  []
  (mem-cache/create-in-memory-cache :ttl {} {:ttl TOKEN_IMP_CACHE_TIME}))

(defn get-permitting-acls
  "Gets ACLs for the current user of the given object identity type and target that grant the given
  permission.
  Example arguments:
  * object-identity-type = :system-object
  * target = \"INGEST_MANAGEMENT_ACL\"
  * permission-type = :read"
  [context object-identity-type target permission-type]
  (try
    (let [acl-oit-key (access-control/acl-type->acl-key object-identity-type)]
      (->> (acl-fetcher/get-acls context [object-identity-type])
           ;; Find acls on target
           (filter #(= target (get-in % [acl-oit-key :target])))
           ;; Find acls for this user and permission type
           (filter (partial acl-matches-sids-and-permission?
                            (context->sids context)
                            permission-type))
           seq))
    (catch Exception e
      (info "Caught exception getting permitting ACLs: " (.getMessage e))
      (if (re-matches #".*status 401.*" (.getMessage e))
        (let [error-message (peek (re-find #"\[\"(.+?)\"" (.getMessage e)))]
          (errors/throw-service-error
           :unauthorized
           error-message))
        nil))))

(defn- has-management-permission?
  "Returns true if the user identified by the token in the cache has been granted
  target permission in ECHO ACLS for the given permission type."
  [context permission-type object-identity-type provider-id target]
  ;; Performance optimization here of returning true if it's the system user.
  (or (transmit-config/echo-system-token? context)
      (let [acl-oit-key (access-control/acl-type->acl-key object-identity-type)]
        (->> (get-permitting-acls context
                                  object-identity-type
                                  target
                                  permission-type)
             ;; Find acls for this provider
             (filter #(or (nil? provider-id)
                          (= provider-id (get-in % [acl-oit-key :provider-id]))))
             seq))))

(defn has-subscription-management-permission?
  "Returns true if the user identified by the token in the cache has been granted
  SUBSCRIPTION_MANAGEMENT permission in ECHO ACLS for the given permission type."
  [context permission-type object-identity-type provider-id]
  (has-management-permission?
    context permission-type object-identity-type provider-id "SUBSCRIPTION_MANAGEMENT"))

(defn has-ingest-management-permission?
  "Returns true if the user identified by the token in the cache has been granted
  INGEST_MANAGEMENT permission in ECHO ACLS for the given permission type."
  [context permission-type object-identity-type provider-id]
  (has-management-permission?
    context permission-type object-identity-type provider-id "INGEST_MANAGEMENT_ACL"))

(defn- verify-management-permission
  "Verifies the current user has been granted the permission in permission-fn in ECHO ACLs"
  [context permission-type object-identity-type provider-id cache-key permission-fn]
  (let [has-permission-fn (fn []
                            (permission-fn
                              context permission-type object-identity-type provider-id))
        has-permission? (if-let [cache (cache/context->cache context cache-key)]
                          ;; Read using cache. Cache key is combo of token and permission type
                          (if (= permission-fn has-subscription-management-permission?)
                            ;; add provider-id to the lookup key for subscription acl cache.
                            (cache/get-value
                              cache [(:token context) permission-type provider-id] has-permission-fn)
                            (cache/get-value
                              cache [(:token context) permission-type] has-permission-fn))
                          ;; No token cache so directly check permission.
                          (has-permission-fn))]
    (when-not has-permission?
      (errors/throw-service-error
        :unauthorized
        "You do not have permission to perform that action."))))

(defn verify-subscription-management-permission
  "Verifies the current user has been granted SUBSCRIPTION_MANAGEMENT
  permission in ECHO ACLs"
  [context permission-type object-identity-type provider-id]
  (verify-management-permission
    context
    permission-type
    object-identity-type
    provider-id
    token-smp-cache-key
    has-subscription-management-permission?))

(defn verify-ingest-management-permission
  "Verifies the current user has been granted INGEST_MANAGEMENT_ACLS
  permission in ECHO ACLs"
  ([context]
   (verify-ingest-management-permission context :update :system-object nil))
  ([context permission-type]
   (verify-ingest-management-permission context permission-type :system-object nil))
  ([context permission-type object-identity-type provider-id]
   (verify-management-permission
     context
     permission-type
     object-identity-type
     provider-id
     token-imp-cache-key
     has-ingest-management-permission?)))
