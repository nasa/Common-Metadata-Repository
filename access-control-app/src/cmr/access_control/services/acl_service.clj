(ns cmr.access-control.services.acl-service
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [cmr.access-control.data.acls :as acls]
    [cmr.common.log :refer [info debug]]
    [cmr.access-control.services.acl-service-util :as acl-util]
    [cmr.common.mime-types :as mt]
    [cmr.access-control.services.acl-validation :as v]
    [cmr.common.services.errors :as errors]
    [cmr.transmit.echo.tokens :as tokens]
    [cmr.transmit.metadata-db2 :as mdb]))

(def acl-provider-id
  "The provider ID for all ACLs. Since ACLs are not owned by individual
  providers, they fall under the CMR system provider ID."
  "CMR")

(defn acl-identity
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

(defn- acl->base-concept
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
   (let [user (tokens/get-user-id context (:token context))]
     (case action
           :create (format "User: [%s] Created ACL [%s]" user (pr-str new-acl))
           :update (format "User: [%s] Updated ACL,\n before: [%s]\n after: [%s]"
                           user (pr-str existing-acl) (pr-str new-acl))
           :delete (format "User: [%s] Deleted ACL [%s]" user (pr-str existing-acl))))))

(defn create-acl
  "Save a new ACL to Metadata DB. Returns map with concept and revision id of created acl."
  [context acl]
  (v/validate-acl-save! context acl :create)

  (let [resp (mdb/save-concept context (merge (acl->base-concept context acl)
                                            {:revision-id 1
                                             :native-id (str (java.util.UUID/randomUUID))}))]
       (info (acl-log-message context (merge acl {:concept-id (:concept-id resp)}) :create))
       resp))

(defn update-acl
  "Update the ACL with the given concept-id in Metadata DB. Returns map with concept and revision id of updated acl."
  [context concept-id acl]
  (v/validate-acl-save! context acl :update)
  ;; This fetch acl call also validates if the ACL with the concept id does not exist or is deleted
  (let [existing-concept (acl-util/fetch-acl-concept context concept-id)
        existing-legacy-guid (:legacy-guid (edn/read-string (:metadata existing-concept)))
        legacy-guid (:legacy-guid acl)]
    (when-not (= existing-legacy-guid legacy-guid)
      (errors/throw-service-error
        :invalid-data (format "ACL legacy guid cannot be updated, was [%s] and now [%s]"
                              existing-legacy-guid legacy-guid)))
    (let [new-concept (merge (acl->base-concept context acl)
                           {:concept-id concept-id
                            :native-id (:native-id existing-concept)})
          resp (mdb/save-concept context new-concept)]
         (info (acl-log-message context new-concept existing-concept :update))
         resp)))

(defn delete-acl
  "Saves a tombstone for the ACL with the given concept id."
  [context concept-id]
  (let [acl-concept (acl-util/fetch-acl-concept context concept-id)]
    (let [tombstone {:concept-id (:concept-id acl-concept)
                       :revision-id (inc (:revision-id acl-concept))
                       :deleted true}
          resp (mdb/save-concept context tombstone)]
         (info (acl-log-message context tombstone acl-concept :delete))
         resp)))
