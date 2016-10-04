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
    [cmr.transmit.echo.tokens :as tokens]))

(defn- get-acls-by-condition
  "Returns user in context, sids of user, and search items returned using condition"
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
    {:items response-acls :sids sids :user user}))

(defn- has-system-access?
  "Returns true if system acl matches sids for user in context for a given action"
  [context action target]
  (let [condition (qm/string-condition :identity-type "System" true false)
        response (get-acls-by-condition context condition)
        any-acl-system-acl (acl/echo-style-acl
                             (first (filter #(= target (:target (:system-identity %))) (:items response))))]
    (acl/acl-matches-sids-and-permission? (:sids response) (name action) any-acl-system-acl)))

(defn- has-provider-access?
  "Returns true if provider acl matches sids for user in context for a given action"
  [context action target provider-id]
  (let [provider-identity-condition (qm/string-condition :identity-type "Provider" true false)
        provider-id-condition (qm/string-condition :provider provider-id)
        conditions (gc/and-conds [provider-identity-condition provider-id-condition])
        response (get-acls-by-condition context conditions)
        prov-acl (acl/echo-style-acl
                      (first (filter #(= target (:target (:provider-identity %))) (:items response))))]
    (acl/acl-matches-sids-and-permission? (:sids response) (name action) prov-acl)))

(defn can?
  "For a given concept, check if user from context has permissions requested"
  [context action acl]
  (when-not (or (transmit-config/echo-system-token? context) (has-system-access? context action "ANY_ACL"))
    (cond
      (:provider-identity acl) (if (= (:target (:provider-identity acl)) "CATALOG_ITEM_ACL")
                                 (when-not (has-provider-access? context action "CATALOG_ITEM_ACL"
                                                                 (:provider-id (:provider-identity acl)))
                                   (errors/throw-service-error :bad-request "Permission Denied"))
                                 (when-not (has-provider-access? context action "PROVIDER_OBJECT_ACL"
                                                                 (:provider-id (:provider-identity acl)))
                                   (errors/throw-service-error :bad-request "Permission Denied")))
      (:catalog-item-identity acl) (when-not (has-provider-access? context action "CATALOG_ITEM_ACL"
                                                                   (:provider-id (:catalog-item-identity acl)))
                                     (errors/throw-service-error :bad-request "Permission Denied"))
      (:system-identity acl) (errors/throw-service-error :bad-request "Permission Denied"))))
