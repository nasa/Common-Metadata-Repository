(ns cmr.access-control.services.acl-util
  "Contains functions needed to be called by group-serivce to avoid circular dependencies between
   group-service and acl-service."
  (:require
   [clojure.string :as str]
   [cmr.access-control.data.access-control-index :as index]
   [cmr.access-control.data.acls :as acls]
   [cmr.common.log :refer [info debug]]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.transmit.echo.tokens :as tokens]
   [cmr.transmit.metadata-db2 :as mdb]))

(def acl-provider-id
  "The provider ID for all ACLs. Since ACLs are not owned by individual
  providers, they fall under the CMR system provider ID."
  "CMR")

(defn- acl-identity
  "Returns a string value representing the ACL's identity field."
  [acl]
  (str/lower-case
    (let [{:keys [system-identity provider-identity single-instance-identity catalog-item-identity]} acl]
      (cond
        system-identity          (str "system:" (:target system-identity))
        single-instance-identity (format "single-instance:%s:%s"
                                         (:target-id single-instance-identity)
                                         (:target single-instance-identity))
        provider-identity        (format "provider:%s:%s"
                                         (:provider-id provider-identity)
                                         (:target provider-identity))
        catalog-item-identity    (format "catalog-item:%s:%s"
                                         (:provider-id catalog-item-identity)
                                         (:name catalog-item-identity))
        :else                    (errors/throw-service-error
                                   :bad-request "malformed ACL")))))

(defn acl->base-concept
  "Returns a basic concept map for the given request context and ACL map."
  [context acl]
  {:concept-type :acl
   :metadata (pr-str acl)
   :format mt/edn
   :provider-id acl-provider-id
   :user-id (when-let [token (:token context)]
              (tokens/get-user-id context token))
   ;; ACL-specific fields
   :extra-fields {:acl-identity (acl-identity acl)
                  :target-provider-id (acls/acl->provider-id acl)}})

(defn acl-log-message
  "Creates appropriate message for given action. Actions include :create, :update and :delete."
  ([context acl action]
   (acl-log-message context acl nil action))
  ([context new-acl existing-acl action]
   (let [user (if (:token context) (tokens/get-user-id context (:token context)) "guest")]
     (case action
           :create (format "User: [%s] Created ACL [%s]" user (pr-str new-acl))
           :update (format "User: [%s] Updated ACL,\n before: [%s]\n after: [%s]"
                           user (pr-str existing-acl) (pr-str new-acl))
           :delete (format "User: [%s] Deleted ACL [%s]" user (pr-str existing-acl))))))

(defn create-acl
  "Save a new ACL to Metadata DB without any validations. Returns map with concept and revision id of created acl."
  [context acl]
  (let [acl-concept (merge (acl->base-concept context acl)
                           {:revision-id 1
                            :native-id (str (java.util.UUID/randomUUID))})
        resp (mdb/save-concept context acl-concept)]
    ;; index the saved ACL here to make ingest synchronous
    (index/index-acl context
                     (merge acl-concept (select-keys resp [:concept-id :revision-id]))
                     {:synchronous? true})
    (info (acl-log-message context (merge acl {:concept-id (:concept-id resp)}) :create))
    resp))
