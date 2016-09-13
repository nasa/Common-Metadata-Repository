(ns cmr.acl.core
  "Contains code for retrieving and manipulating ACLs."
  (:require [cmr.common.services.errors :as errors]
            [cmr.acl.acl-fetcher :as acl-fetcher]
            [cmr.transmit.config :as tc]
            [cmr.transmit.echo.acls :as echo-acls]
            [cmr.transmit.echo.tokens :as echo-tokens]
            [cmr.common.cache :as cache]
            [clojure.string :as str]
            [cmr.common.cache.in-memory-cache :as mem-cache]
            [clojure.core.cache :as clj-cache]))

(def BROWSER_CLIENT_ID "browser")
(def CURL_CLIENT_ID "curl")
(def UNKNOWN_CLIENT_ID "unknown")

(defn- get-token
  "Returns the token the user passed in the headers or parameters"
  [params headers]
  (let [non-empty-string #(when-not (str/blank? %) %)]
    (or (non-empty-string (:token params))
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

(defn context->sids
  "Returns the security identifiers (group guids and :guest or :registered) of the user identified
  by the token in the context."
  [context]
  (or (:sids context)
      (let [{:keys [token]} context]
        (if token
          (echo-tokens/get-current-sids context token)
          [:guest]))))

(defn- ace-matches-sid?
  "Returns true if the ACE is applicable to the SID."
  [sid ace]
  (or
    (= sid (:user-type ace))
    (= sid (:group-guid ace))))

(defn acl-matches-sids-and-permission?
  "Returns true if the acl is applicable to any of the sids."
  [sids permission acl]
  (some (fn [sid]
          (some (fn [ace]
                  (and (ace-matches-sid? sid ace)
                       (some #(= % permission) (:permissions ace))))
                (:aces acl)))
        sids))

(def token-imp-cache-key
  "The cache key for the token to ingest management permission cache."
  :token-imp)

(def TOKEN_IMP_CACHE_TIME
  "The number of milliseconds token information will be cached."
  (* 5 60 1000))

(defn create-token-imp-cache
  "Creates a cache for which tokens have ingest management permission."
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
  (let [acl-oit-key (echo-acls/acl-type->acl-key object-identity-type)]
    (->> (acl-fetcher/get-acls context [object-identity-type])
         ;; Find acls on INGEST_MANAGEMENT
         (filter #(= target (get-in % [acl-oit-key :target])))
         ;; Find acls for this user and permission type
         (filter (partial acl-matches-sids-and-permission?
                          (context->sids context)
                          permission-type))
         seq)))
(comment

 (deref (get-in user/system [:apps :mock-echo :acl-db]))

 (->> (acl-fetcher/get-acls context [object-identity-type])
      ;; Find acls on INGEST_MANAGEMENT
      (filter #(= target (get-in % [acl-oit-key :target])))
      ;; Find acls for this user and permission type
      (filter (partial acl-matches-sids-and-permission?
                       (context->sids context)
                       permission-type))))


(defn- has-ingest-management-permission?
  "Returns true if the user identified by the token in the cache has been granted
  INGEST_MANAGEMENT_PERMISSION in ECHO ACLS for the given permission type."
  [context permission-type object-identity-type provider-id]
  (let [acl-oit-key (echo-acls/acl-type->acl-key object-identity-type)]
    (->> (get-permitting-acls context object-identity-type "INGEST_MANAGEMENT_ACL" permission-type)
         ;; Find acls for this provider
         (filter #(or (nil? provider-id)
                      (= provider-id (get-in % [acl-oit-key :provider-id]))))
         seq)))

(defn verify-ingest-management-permission
  "Verifies the current user has been granted INGEST_MANAGEMENT_PERMISSION in ECHO ACLs"
  ([context]
   (verify-ingest-management-permission context :update :system-object nil))
  ([context permission-type]
   (verify-ingest-management-permission context permission-type :system-object nil))
  ([context permission-type object-identity-type provider-id]
   (let [has-permission-fn #(has-ingest-management-permission?
                              context permission-type object-identity-type provider-id)
         has-permission? (if-let [cache (cache/context->cache context token-imp-cache-key)]
                           ;; Read using cache. Cache key is combo of token and permission type
                           (cache/get-value
                             cache [(:token context) permission-type] has-permission-fn)
                           ;; No token cache so directly check permission.
                           (has-permission-fn))]
     (when-not has-permission?
       (errors/throw-service-error
         :unauthorized
         "You do not have permission to perform that action.")))))
