(ns cmr.access-control.services.acl-authorization
  (:require
    [clojure.edn :as edn]
    [cmr.access-control.services.auth-util :as auth-util]
    [cmr.acl.core :as acl]
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.query-execution :as qe]
    [cmr.common-app.services.search.query-model :as qm]
    [cmr.common.services.errors :as errors]
    [cmr.common.util :as util :refer [defn-timed]]
    [cmr.transmit.config :as transmit-config]
    [cmr.access-control.services.group-service :as group-service]
    [cmr.transmit.config :as tc]))

;; TODO add timing everywhere

;; TODO many of the functions here are likely no longer needed. See what can be removed.
;; TODO replace System and Provider with constants

(defn-timed get-acls-by-condition
  "Returns the acls found by executing given condition against ACL index"
  [context condition]
  (let [query (qm/query {:concept-type :acl
                         :condition condition
                         :skip-acls? true
                         :page-size :unlimited
                         :result-format :query-specified
                         :result-fields [:acl-gzip-b64]})
        response (qe/execute-query context query)]
    (mapv #(edn/read-string (util/gzip-base64->string (:acl-gzip-b64 %))) (:items response))))

(defn- acls-granting-acl-read
  "Returns a sequences of acls granting ACL read to the current user"
  [context]
  (let [sids (auth-util/get-sids context)
        system-condition (gc/and-conds (qm/string-condition :identity-type "System" true false)
                                       (qm/string-condition :target "ANY_ACL"))
        prov-condition (gc/and-conds
                        (qm/string-condition :identity-type "Provider" true false)
                        (gc/or-conds
                         (qm/string-condition :target "CATALOG_ITEM_ACL")
                         (qm/string-condition :target "PROVIDER_OBJECT_ACL")))
        condition (gc/or-conds system-condition prov-condition)
        acls (get-acls-by-condition context condition)]
    (filterv #(acl/acl-matches-sids-and-permission? sids "read" %) acls)))

;; TODO this won't quite work yet. Single instance identity ACLs don't reference a provider id.
;; I added an idea of how to make that work. See TODO in cmr.access-control.data.acls
(defn- provider-read-acl->condition
  "TODO"
  [acl]
  (let [target (get-in acl [:provider_identity :target])
        ;; TODO constants for all these strings
        target-cond (case target
                      "CATALOG_ITEM_ACL"
                      (qm/string-condition :identity-type "Catalog Item" true false)
                      "PROVIDER_OBJECT_ACL"
                      (gc/or-conds
                       [(qm/string-condition :identity-type "Provider" true false)
                        (qm/string-condition :identity-type "Single Instance Identity" true false)])
                      ;; else
                      (throw (Exception. (format "Unexpected target for acl [%s] of [%s]"
                                                 (pr-str acl) target))))
        provider-id (get-in acl [:provider_identity :provider_id])
        provider-cond (qm/string-condition :target-provider-id provider-id)]
    (gc/and-conds [provider-cond target-cond])))

(defmethod qe/add-acl-conditions-to-query :acl
  [context query]
  (let [acls (acls-granting-acl-read context)]
    ;; TODO ANY_ACL constant
    (if (some #(= "ANY_ACL" (get-in % [:system_identity :target])) acls)
      query
      (if (seq acls)
        (let [combined-condition (gc/group-conds :or (mapv provider-read-acl->condition acls))]
          (update query :condition #(gc/and-conds combined-condition %)))
        (assoc query :condition qm/match-none)))))

;; TODO do we need the functions below this?

(defn has-system-access?
  "Returns true if system ACL matches sids for user in context for a given action"
  [context action target]
  (let [condition (qm/string-condition :identity-type "System" true false)
        system-acls (get-acls-by-condition context condition)
        any-acl-system-acl (acl/echo-style-acl
                             (first (filter #(= target (:target (:system-identity %))) (:acls system-acls))))
        sids (auth-util/get-sids context)]
    (acl/acl-matches-sids-and-permission? sids (name action) any-acl-system-acl)))

(defn- has-provider-access?
  "Returns true if provider ACL matches sids for user in context for a given action"
  [context action target provider-id]
  (let [provider-identity-condition (qm/string-condition :identity-type "Provider" true false)
        provider-id-condition (qm/string-condition :provider provider-id)
        conditions (gc/and-conds [provider-identity-condition provider-id-condition])
        provider-acls (get-acls-by-condition context conditions)
        prov-acl (acl/echo-style-acl
                   (first (filter #(= target (:target (:provider-identity %))) (:acls provider-acls))))
        sids (auth-util/get-sids context)]
    (acl/acl-matches-sids-and-permission? sids (name action) prov-acl)))

(defn- has-self-permission?
  "Returns true if ACL itself matches sids for user in context for a given action"
  [context action concept-id]
  (let [condition (qm/string-condition :concept-id concept-id true false)
        returned-acl (first (get-acls-by-condition context condition))
        echo-acl (acl/echo-style-acl returned-acl)
        sids (auth-util/get-sids context)]
    ;; read is special, if the user has any permission for the acl
    ;; then the user has permission to read
    (if (= action :read)
      (some #(acl/acl-matches-sids-and-permission? sids % echo-acl)
            ["create" "read" "update" "delete"])
      (acl/acl-matches-sids-and-permission? sids (name action) echo-acl))))

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
         (has-system-access? context permission "ANY_ACL")
         (when concept-id
           (has-self-permission? context permission concept-id)))
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
     (let [target-group (group-service/get-group (tc/with-echo-system-token context)
                                                 (get-in acl [:single-instance-identity :target-id]))]
       (and (:provider-id target-group)
            (has-provider-access? context permission "PROVIDER_OBJECT_ACL" (:provider-id target-group)))))))

(defn authorize-acl-action
  "Throws service error if user doesn't have permission to intiate a given action for a given acl.
   Actions include create and update."
  [context action acl]
  (when-not (action-permitted-on-acl? context action acl)
    (errors/throw-service-error :bad-request (permission-denied-message action))))
