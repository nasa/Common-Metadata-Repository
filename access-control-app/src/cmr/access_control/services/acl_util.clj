(ns cmr.access-control.services.acl-util
  "Contains functions needed to be called by group-serivce to avoid circular dependencies between
   group-service and acl-service."
  (:require
   [clojure.edn :as edn]
   [clojure.set :as set]
   [clojure.string :as str]
   [cmr.access-control.config :as config]
   [cmr.access-control.data.access-control-index :as index]
   [cmr.access-control.data.acls :as acls]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common.log :refer [info debug warn error]]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :refer [defn-timed] :as util]
   [cmr.transmit.echo.tokens :as tokens]
   [cmr.transmit.metadata-db :as mdb1]
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

(defn-timed get-acls-by-condition
  "Returns the ACLs found by executing given condition against ACL index."
  [context condition]
  (let [query (qm/query {:concept-type :acl
                         :condition condition
                         :skip-acls? true
                         :page-size :unlimited
                         :result-format :query-specified
                         :result-fields [:acl-gzip-b64]})
        response (qe/execute-query context query)]
    (mapv #(edn/read-string (util/gzip-base64->string (:acl-gzip-b64 %))) (:items response))))

(defn get-acl-concepts-by-identity-type-and-target
  "Returns ACLs with given identity-type string and target string.
   Valid identity types are defined in cmr.access-control.data.access-control-index.
   Valid targets can be found in cmr.access-control.data.acl-schema."
  [context identity-type target]
  (let [identity-type-condition (qm/string-condition :identity-type identity-type true false)
        condition (if target
                    (gc/and identity-type-condition
                            (qm/string-condition :target target))
                    identity-type-condition)]
    (get-acls-by-condition context condition)))

(defn get-collections-chunked
  "Searches for collections in chunks."
  [context values field provider-id]
  (let [chunk-size (config/sync-entry-titles-concept-ids-collection-batch-size)
        chunked-values (partition chunk-size chunk-size nil values)]
    (mapcat #(mdb1/find-concepts context
                              {:exclude-metadata true
                               :latest true
                               field %
                               :provider-id provider-id}
                              :collection)
            chunked-values)))

(defn sync-entry-titles-concept-ids
  "If the given ACL is a catalog item acl with a collection identifier that includes concept-ids or
   entry-titles, return the ACL such that both are unioned with each other."
  [context acl]
  (if-let [collection-identifier (get-in acl [:catalog-item-identity :collection-identifier])]
    (let [start-time (System/currentTimeMillis)
          entry-titles (:entry-titles collection-identifier)
          concept-ids (:concept-ids collection-identifier)
          provider-id (get-in acl [:catalog-item-identity :provider-id])
          colls-from-entry-titles (when (seq entry-titles)
                                    (get-collections-chunked context entry-titles :entry-title provider-id))
          colls-from-concept-ids (when (seq concept-ids)
                                   (get-collections-chunked context concept-ids :concept-id provider-id))
          collections (distinct (concat colls-from-entry-titles colls-from-concept-ids))
          synced-concept-ids (map :concept-id collections)
          dropped-concept-ids (set/difference (set concept-ids)
                                              (set synced-concept-ids))
          entry-titles (map #(get-in % [:extra-fields :entry-title]) collections)
          collection-identifier (-> collection-identifier
                                    (assoc :entry-titles entry-titles)
                                    (assoc :concept-ids synced-concept-ids)
                                    util/remove-nil-keys)
          total-took (- (System/currentTimeMillis) start-time)]
      (info (format "ACL entry-titles/concept-ids sync finished, %s entries synced in %d ms."
                    (count entry-titles)
                    total-took))
      (when (seq dropped-concept-ids)
        (warn (format "Dropping non existent collection concept-ids from collection identifier: %s"
                      (vec dropped-concept-ids))))
      (assoc-in acl [:catalog-item-identity :collection-identifier] collection-identifier))
    acl))
