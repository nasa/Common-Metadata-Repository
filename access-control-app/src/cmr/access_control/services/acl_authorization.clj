(ns cmr.access-control.services.acl-authorization
  (:require
    [clojure.edn :as edn]
    [cmr.access-control.services.acl-util :as acl-util]
    [cmr.access-control.services.auth-util :as auth-util]
    [cmr.access-control.services.group-service :as group-service]
    [cmr.access-control.data.access-control-index :as index]
    [cmr.access-control.data.acl-schema :as schema]
    [cmr.acl.core :as acl]
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.query-execution :as qe]
    [cmr.common-app.services.search.query-model :as qm]
    [cmr.common.services.errors :as errors]
    [cmr.common.util :as util :refer [defn-timed]]
    [cmr.transmit.config :as transmit-config]))

(defn-timed acls-granting-acl-read
  "Returns a sequences of acls granting ACL read to the current user"
  [context]
  (let [sids (auth-util/get-sids context)
        system-condition (gc/and (qm/string-condition :identity-type index/system-identity-type-name true false)
                                 (qm/string-condition :target schema/system-any-acl-target))
        prov-condition (gc/and (qm/string-condition :identity-type index/provider-identity-type-name true false)
                               (gc/or (qm/string-condition :target schema/provider-catalog-item-acl-target)
                                      (qm/string-condition :target schema/provider-object-acl-target)))
        condition (gc/or system-condition prov-condition)
        acls (acl-util/get-acls-by-condition context condition)]
    (filterv #(acl/acl-matches-sids-and-permission? sids "read" %) acls)))

(defn- provider-read-acl->condition
  "Returns an elastic query condition that matches ACLs which the user has permission to read."
  [acl]
  (let [target (get-in acl [:provider-identity :target])
        target-cond (condp = target
                      schema/provider-catalog-item-acl-target
                      (qm/string-condition :identity-type index/catalog-item-identity-type-name true false)

                      schema/provider-object-acl-target
                      (gc/or
                        (qm/string-condition :identity-type index/provider-identity-type-name true false)
                        (qm/string-condition :identity-type index/single-instance-identity-type-name true false))

                      ;; else
                      (throw (Exception. (format "Unexpected target for acl [%s] of [%s]"
                                                 (pr-str acl) target))))
        provider-id (get-in acl [:provider-identity :provider-id])
        provider-cond (qm/string-condition :target-provider-id provider-id)]
    (gc/and provider-cond target-cond)))

(defmethod qe/add-acl-conditions-to-query :acl
  [context query]
  (if (transmit-config/echo-system-token? context)
    query
    (let [acls (acls-granting-acl-read context)]
      (if (some #(= schema/system-any-acl-target (get-in % [:system-identity :target])) acls)
        query
        (if (seq acls)
          (let [combined-condition (gc/or-conds (mapv provider-read-acl->condition acls))]
            (update query :condition #(gc/and combined-condition %)))
          (assoc query :condition qm/match-none))))))

(defn has-system-access?
  "Returns true if system ACL matches sids for user in context for a given action"
  [context action target]
  (let [condition (qm/string-condition :identity-type index/system-identity-type-name true false)
        system-acls (acl-util/get-acls-by-condition context condition)
        any-acl-system-acl (first (filter #(= target (:target (:system-identity %))) system-acls))
        sids (auth-util/get-sids context)]
    (acl/acl-matches-sids-and-permission? sids (name action) any-acl-system-acl)))

(defn- has-provider-access?
  "Returns true if provider ACL matches sids for user in context for a given action"
  [context action target provider-id]
  (let [provider-identity-condition (qm/string-condition :identity-type index/provider-identity-type-name true false)
        provider-id-condition (qm/string-condition :provider provider-id)
        conditions (gc/and-conds [provider-identity-condition provider-id-condition])
        provider-acls (acl-util/get-acls-by-condition context conditions)
        prov-acl (first (filter #(= target (:target (:provider-identity %))) provider-acls))
        sids (auth-util/get-sids context)]
    (acl/acl-matches-sids-and-permission? sids (name action) prov-acl)))

(defn- has-self-permission?
  "Returns true if ACL itself matches sids for user in context for a given action"
  [context action concept-id]
  (let [condition (qm/string-condition :concept-id concept-id true false)
        returned-acl (first (acl-util/get-acls-by-condition context condition))
        sids (auth-util/get-sids context)]
    ;; read is special, if the user has any permission for the acl
    ;; then the user has permission to read
    (if (= action :read)
      (some #(acl/acl-matches-sids-and-permission? sids % returned-acl)
            ["create" "read" "update" "delete"])
      (acl/acl-matches-sids-and-permission? sids (name action) returned-acl))))

(defn- permission-denied-message
  "Returns permission denied message for given user and action."
  [action]
  (format "Permission to %s ACL is denied" (name action)))

(defn action-permitted-on-acl?
  "Returns true if any ACLs grant the current context user the given permission
  keyword (:create, :update, etc.) on the given acl."
  ([context permission acl]
   (action-permitted-on-acl? context permission acl nil))
  ([context permission acl concept-id]
   (cond
     ;; system token or system-level ANY_ACL permission can do anything
     (or (transmit-config/echo-system-token? context)
         (has-system-access? context permission schema/system-any-acl-target)
         (when concept-id
           (has-self-permission? context permission concept-id)))
     true

     ;; If the user does not have system-level permissions, they may not perform any actions
     ;; on system-level ACLs.
     (:system-identity acl)
     false

     (:provider-identity acl)
     (if (= (:target (:provider-identity acl)) schema/provider-catalog-item-acl-target)
       (has-provider-access? context permission schema/provider-catalog-item-acl-target
                             (:provider-id (:provider-identity acl)))
       (has-provider-access? context permission schema/provider-object-acl-target
                             (:provider-id (:provider-identity acl))))

     (:catalog-item-identity acl)
     (has-provider-access? context permission schema/provider-catalog-item-acl-target
                           (:provider-id (:catalog-item-identity acl)))

     (:single-instance-identity acl)
     (let [target-group (group-service/get-group (transmit-config/with-echo-system-token context)
                                                 (get-in acl [:single-instance-identity :target-id]))]
       (and (:provider-id target-group)
            (has-provider-access? context permission schema/provider-object-acl-target (:provider-id target-group)))))))

(defn authorize-acl-action
  "Throws service error if user doesn't have permission to intiate a given action for a given acl.
   Actions include create and update."
  [context action acl]
  (when-not (action-permitted-on-acl? context action acl)
    (errors/throw-service-error :unauthorized (permission-denied-message action))))
