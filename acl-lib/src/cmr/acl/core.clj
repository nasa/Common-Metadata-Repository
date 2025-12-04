(ns cmr.acl.core
  "Contains code for retrieving and manipulating ACLs."
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.acl.acl-fetcher :as acl-fetcher]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.log :refer (info)]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.transmit.access-control :as access-control]
   [cmr.transmit.config :as transmit-config]))

(def BROWSER_CLIENT_ID "browser")
(def CURL_CLIENT_ID "curl")
(def UNKNOWN_CLIENT_ID "unknown")

(declare allow-echo-token)
(defconfig allow-echo-token
  "Flag that indicates if we accept the 'Echo-Token' header."
  {:default true :type Boolean})

(def collection-field-constraints-cache-key
  "The cache key for a URS cache."
  :collection-field-constraints)

(defn non-empty-string
  [s]
  (when-not (string/blank? s) s))

(defn get-token
  "Returns the token the user passed in the headers or parameters"
  [params headers]
  (let [header-token (non-empty-string (get headers transmit-config/token-header))
        param-token (non-empty-string (:token params))]
    (if (and header-token param-token (not= header-token param-token))
      (errors/throw-service-error
       :bad-request
       "Multiple authorization tokens found. Tokens may be set as an Authorization header or token query parameter value but not both.")
      (or
       header-token
       param-token
       (when (allow-echo-token)
         (non-empty-string (get headers transmit-config/echo-token-header)))))))

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
    (let [{:keys [params headers]} request]
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

(def token-pc-cache-key
  "The cache key for the token to provider context permission cache."
  :token-pc)

(def CONCEPT_MAP_CACHE_TIME
  "The number of milliseconds token information will be cached."
  (* 5 60 1000))

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

(defn create-token-pc-cache
  "Creates a cache for which tokens have provider context  permission."
  []
  (mem-cache/create-in-memory-cache :ttl {} {:ttl TOKEN_IMP_CACHE_TIME}))

(defn create-access-constraints-cache
  "Create a cache for access constraint mapping."
  []
  (mem-cache/create-in-memory-cache :ttl {} {:ttl CONCEPT_MAP_CACHE_TIME}))

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

(defn has-provider-context-permission?
  "Returns true if the user identified by the token in the cache has been granted
  PROVIDER_CONTEXT permission in ECHO ACLS for the given permission type."
  [context permission-type object-identity-type provider-id]
  (has-management-permission?
    context permission-type object-identity-type provider-id "PROVIDER_CONTEXT"))

(defn has-non-nasa-draft-permission?
  "Returns true if the user has been granted NON_NASA_DRAFT_USER permission.
  Required for EDL+MFA (assurance level 4) JWT tokens."
  [context permission-type object-identity-type provider-id]
  (has-management-permission?
    context permission-type object-identity-type provider-id "NON_NASA_DRAFT_USER"))

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

(defn- verify-management-permission-for-provider
  "Verifies the current user has been granted the permission in permission-fn in ECHO ACLs for a provider."
  [context permission-type object-identity-type provider-id cache-key permission-fn]
  (let [has-permission-fn (fn []
                            (permission-fn
                              context permission-type object-identity-type provider-id))
        has-permission? (if-let [cache (cache/context->cache context cache-key)]
                          ;; Read using cache. Cache key is combo of token, permission type and provider id.
                          (cache/get-value
                            cache [(:token context) permission-type provider-id] has-permission-fn)
                          ;; No token cache so directly check permission.
                          (has-permission-fn))]
    (when-not has-permission?
      (if (= cache-key token-pc-cache-key)
        (errors/throw-service-error
          :unauthorized
          "You do not have PROVIDER_CONTEXT permission to perform that action.")
        (errors/throw-service-error
          :unauthorized
          "You do not have permission to perform that action.")))))

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

(defn verify-ingest-management-permission-for-provider
  "Verifies the current user has been granted INGEST_MANAGEMENT_ACLS
  permission in ECHO ACLs for a provider."
  [context permission-type object-identity-type provider-id]
  (verify-management-permission-for-provider
    context
    permission-type
    object-identity-type
    provider-id
    token-imp-cache-key
    has-ingest-management-permission?))

(defn verify-provider-context-permission
  "Verifies the current user has been granted PROVIDER_CONTEXT acl.
  permission in ECHO ACLs"
  ([context]
   (verify-provider-context-permission context :read :system-object nil))
  ([context permission-type]
   (verify-provider-context-permission context permission-type :system-object nil))
  ([context permission-type object-identity-type provider-id]
   (verify-management-permission-for-provider
     context
     permission-type
     object-identity-type
     provider-id
     token-pc-cache-key
     has-provider-context-permission?)))

(defn verify-non-nasa-draft-permission
  "Verifies the user has NON_NASA_DRAFT_USER permission for a provider.
  Required for EDL+MFA (assurance level 4) JWT tokens."
  [context permission-type object-identity-type provider-id]
  (when-not (has-non-nasa-draft-permission? context permission-type object-identity-type provider-id)
    (errors/throw-service-error :unauthorized "You do not have permission to perform that action.")))
