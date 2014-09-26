(ns cmr.acl.core
  "Contains code for retrieving and manipulating ACLs."
  (:require [cmr.common.services.errors :as errors]
            [cmr.acl.acl-cache :as acl-cache]
            [cmr.transmit.echo.acls :as echo-acls]
            [cmr.transmit.echo.tokens :as echo-tokens]
            [cmr.acl.collection-matchers :as cm]))

(def BROWSER_CLIENT_ID "browser")
(def CURL_CLIENT_ID "curl")
(def UNKNOWN_CLIENT_ID "unknown")

(def token-header
  "echo-token")

(defn- get-token
  "Returns the token the user passed in the headers or parameters"
  [params headers]
  (or (:token params) (get headers token-header)))

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

(defn add-authentication-to-context
  "Adds information to the context including the current token and the client id"
  [context params headers]
  (-> context
      (assoc :token (get-token params headers))
      (assoc :client-id (get-client-id headers))))

(defn context->sids
  "Returns the security identifiers (group guids and :guest or :registered) of the user identified
  by the token in the context."
  [context]
  (let [{:keys [token]} context]
    (if token
      (echo-tokens/get-current-sids context token)
      [:guest])))

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

(defn get-coll-permitted-group-ids
  "Returns the groups ids (group guids, 'guest', 'registered') that have permission to read
  this collection"
  [context provider-id coll]

  (->> (acl-cache/get-acls context)
       ;; Find only acls that are applicable to this collection
       (filter (partial cm/coll-applicable-acl? provider-id coll))
       ;; Get the permissions they grant
       (mapcat :aces)
       ;; Find permissions that grant read
       (filter #(some (partial = :read) (:permissions %)))
       ;; Get the group guids or user type of those permissions
       (map #(or (:group-guid %) (some-> % :user-type name)))
       distinct))

(defn verify-ingest-management-permission
  "Verifies the current user has been granted INGEST_MANAGEMENT_PERMISSION in ECHO ACLs"
  ([context]
   (verify-ingest-management-permission context :update))
  ([context permission-type]
   (when-not  (->> (echo-acls/get-acls-by-type context "SYSTEM_OBJECT")
                   ;; Find acls on INGEST_MANAGEMENT
                   (filter (comp (partial = "INGEST_MANAGEMENT_ACL") :target :system-object-identity))
                   ;; Find acls for this user and permission type
                   (filter (partial acl-matches-sids-and-permission?
                                    (context->sids context)
                                    permission-type))
                   seq)
     (errors/throw-service-error
       :unauthorized
       "You do not have permission to perform that action."))))


