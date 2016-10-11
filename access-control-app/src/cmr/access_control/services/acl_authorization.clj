(ns cmr.access-control.services.acl-authorization
  (:require
    [clojure.edn :as edn]
    [cmr.access-control.services.auth-util :as auth-util]
    [cmr.acl.core :as acl]
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.query-execution :as qe]
    [cmr.common-app.services.search.query-model :as qm]
    [cmr.common.services.errors :as errors]
    [cmr.common.util :as util]
    [cmr.transmit.config :as transmit-config]
    [cmr.transmit.echo.tokens :as tokens]
    [cmr.access-control.services.group-service :as group-service]))

(defn- get-acls-by-condition
  "Returns a map containing the context user, the user's sids, and acls found by executing given condition against ACL index"
  [context condition]
  (let [token (:token context)
        user (if token (tokens/get-user-id context token) "guest")
        sids (auth-util/get-sids context user)
        query (qm/query {:concept-type :acl
                         :condition condition
                         :skip-acls? true
                         :page-size :unlimited
                         :result-format :query-specified
                         :result-fields [:acl-gzip-b64]})
        response (qe/execute-query context query)
        response-acls (map #(edn/read-string (util/gzip-base64->string (:acl-gzip-b64 %))) (:items response))]
    {:acls response-acls :sids sids :user user}))

(defn- has-system-access?
  "Returns true if system acl matches sids for user in context for a given action"
  [context action target]
  (let [condition (qm/string-condition :identity-type "System" true false)
        system-acls (get-acls-by-condition context condition)
        any-acl-system-acl (acl/echo-style-acl
                             (first (filter #(= target (:target (:system-identity %))) (:acls system-acls))))]
    (acl/acl-matches-sids-and-permission? (:sids system-acls) (name action) any-acl-system-acl)))

(defn- has-provider-access?
  "Returns true if provider acl matches sids for user in context for a given action"
  [context action target provider-id]
  (let [provider-identity-condition (qm/string-condition :identity-type "Provider" true false)
        provider-id-condition (qm/string-condition :provider provider-id)
        conditions (gc/and-conds [provider-identity-condition provider-id-condition])
        provider-acls (get-acls-by-condition context conditions)
        prov-acl (acl/echo-style-acl
                      (first (filter #(= target (:target (:provider-identity %))) (:acls provider-acls))))]
    (acl/acl-matches-sids-and-permission? (:sids provider-acls) (name action) prov-acl)))

(defn- permission-denied-message
  "Returns permission denied message for given user and action."
  [action]
  (format "Permission to %s ACL is denied" (name action)))

(defn- permitted?
  "Returns true if any ACLs grant the current context user the given permission
  keyword (:create, :update, etc.) on the given acl."
  [context permission acl]
  (cond
    ;; system token or system-level ANY_ACL permission can do anything
    (or (transmit-config/echo-system-token? context)
        (has-system-access? context permission "ANY_ACL"))
    true

    ;; If the user does not have system-level permissions, they may not perform any actions
    ;; on system-level ACLs.
    (:system-identity acl)
    false

    (:provider-identity acl)
    (if (= (:target (:provider-identity acl)) "CATALOG_ITEM_ACL")
      (has-provider-access? context permission "CATALOG_ITEM_ACL"
                            (:provider-id (:provider-identity acl)))
      (has-provider-access? context permission "PROVIDER_OBJECT_ACL"
                            (:provider-id (:provider-identity acl))))

    (:catalog-item-identity acl)
    (has-provider-access? context permission "CATALOG_ITEM_ACL"
                          (:provider-id (:catalog-item-identity acl)))

    (:single-instance-identity acl)
    (let [target-group (group-service/get-group context (get-in acl [:single-instance-identity :target-id]))]
      (and (:provider-id target-group)
           (has-provider-access? context permission "PROVIDER_OBJECT_ACL" (:provider-id target-group))))))

(defn authorize-acl-action
  "Throws service error if user doesn't have permission to intiate a given action for a given acl.
   Actions include create and update."
  [context action acl]
  (when-not (permitted? context action acl)
    (errors/throw-service-error :bad-request (permission-denied-message action))))
