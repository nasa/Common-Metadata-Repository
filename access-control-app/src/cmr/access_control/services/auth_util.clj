(ns cmr.access-control.services.auth-util
  (:require
   [cmr.access-control.config :as access-control-config]
   [cmr.acl.core :as acl]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.echo.tokens :as echo-tokens]
   [cmr.transmit.urs :as urs]))

(defn get-sids
  "Returns a seq of sids for the given username string or user type keyword
   for use in checking permissions against acls."
  ([context]
   (let [token (:token context)
         user (if token (or (util/get-real-or-lazy context :user-id)
                            (echo-tokens/get-user-id context token))
                        "guest")]
     (get-sids context user)))
  ([context username-or-type]
   (if (contains? #{"guest" "registered"} (name username-or-type))
     [(keyword username-or-type)]
     (concat
      [:registered]
      (when (access-control-config/enable-edl-groups)
        (urs/get-edl-groups-by-username context username-or-type))
      (when (access-control-config/enable-cmr-group-sids)
        (let [query (qm/query {:concept-type :access-group
                               :condition (qm/string-condition :member username-or-type)
                               :skip-acls? true
                               :page-size :unlimited
                               :result-format :query-specified
                               :result-fields [:concept-id :legacy-guid]})
              groups (:items (qe/execute-query context query))]
          (distinct
           (concat []
                   ;; ACLs may be from ECHO or CMR, and may reference ECHO GUIDs as well as CMR concept IDs,
                   ;; depending on the context, so we will return both types of IDs here.
                   (keep :legacy-guid groups)
                   (keep :concept-id groups)))))))))

(defn- put-sids-in-context
  "Gets the current SIDs of the user in the context from the Access control application."
  [context]
  (if-let [token (:token context)]
    ;; TODO we need to cache token->sids cache like in search
    (let [user-id (echo-tokens/get-user-id context token)
          sids (get-sids context user-id)]
      (assoc context :sids sids))
    (assoc context :sids [:guest])))

(defn- get-system-level-group-acls
  "Returns ACLs which grant the given permission to the context user for system-level groups."
  [context permission]
  (seq (acl/get-permitting-acls context :system-object "GROUP" permission)))

(defn- get-provider-level-group-acls
  "Returns all ACLs that grant given permission to context user for any provider-level groups."
  [context permission]
  (acl/get-permitting-acls context :provider-object "GROUP" permission))

(defn- get-provider-acls
  "Returns any ACLs that grant the given permission to the context user for the specified group object."
  [context permission group]
  (when-let [provider-id (:provider-id group)]
    (when-not (= "CMR" provider-id)
      (seq
        (filter #(= provider-id (-> % :provider-identity :provider-id))
                (get-provider-level-group-acls context permission))))))

(defn- get-instance-acls
 "Returns any ACLs that grant the given permission to the context user on a specific group by its concept-id or legacy-guid."
 [context action-description group]
 (let [concept-id (:concept-id group)
       legacy-guid (:legacy-guid group)
       permissions (case action-description
                         "update" [:update]
                         ;; Update and delete permissions grant read as well, there is no explicit read permissions
                         ;; for single instance acls
                         "read" [:update :delete]
                         "delete" [:delete]
                         "create" [:create])]
   (when (or legacy-guid concept-id)
     (let [acls (mapcat #(acl/get-permitting-acls context :single-instance-object "GROUP_MANAGEMENT" %) permissions)]
       (seq (filter
             #(or (= concept-id (get-in % [:single-instance-identity :target-id]))
                  (= legacy-guid (get-in % [:single-instance-identity :target-id])))
             acls))))))

(defn- describe-group
  [group]
  (let [{:keys [provider-id]} group]
    (if (and provider-id (not= "CMR" provider-id))
      (format "access control group [%s] in provider [%s]" (:name group) provider-id)
      (format "system-level access control group [%s]" (:name group)))))

(defn- throw-group-permission-error
  [permission group]
  (errors/throw-service-error
    :unauthorized
    (format "You do not have permission to %s %s."
            (name permission)
            (describe-group group))))

(defn- verify-group-permission
  "Throws a permission service error if no ACLs exist that grant the desired permission to the
  context user on group."
  [context action-description permission group]
  (when-not (transmit-config/echo-system-token? context)
    (let [context (put-sids-in-context context)]
      (when-not (or (get-instance-acls context action-description group)
                    (get-provider-acls context permission group)
                    (get-system-level-group-acls context permission))
        (throw-group-permission-error action-description group)))))

(defn verify-can-create-group
  "Throws a service error if the context user cannot create a group under provider-id."
  [context group]
  (verify-group-permission context "create" :create group))

(defn verify-can-read-group
  "Throws a service error if the context user cannot read the access control group represented by
   the group map."
  [context group]
  (verify-group-permission context "read" :read group))

(defn verify-can-delete-group
  "Throws a service error of context user cannot delete access control group represented by given
   group map."
  [context group]
  ;; Note: temporarily use :create permission for CMR-2585
  (verify-group-permission context "delete" :create group))

(defn verify-can-update-group
  "Throws service error if context user does not have permission to delete group map."
  [context group]
  ;; Note: temporarily use :create permission for CMR-2585
  (verify-group-permission context "update" :create group))

;;; For Search/Indexing

;; The following multimethod is automatically called by the query execution service when executing a query.

(defmethod qe/add-acl-conditions-to-query :access-group
  [context query]
  ;; We want to avoid a circular dependency here. Any call to the kernel will result in a search
  ;; for groups. We assume that the system read token has full permission here. The kernel will use
  ;; that to call the access control group.
  (let [context (put-sids-in-context context)]
    ;; When the user is the system, or a user with system group read permission, they can see all groups.
    (if (or (transmit-config/echo-system-token? context)
            (seq (get-system-level-group-acls context :read)))
      query
      ;; Otherwise, we need to filter the results to only the providers visible to the current user.
      (let [provider-ids (map #(-> % :provider-identity :provider-id)
                              (get-provider-level-group-acls context :read))]
        (if (seq provider-ids)
          (update-in query [:condition] (fn [condition]
                                          (gc/and-conds [(qm/string-conditions :provider-id provider-ids)
                                                         condition])))
          (assoc query :condition qm/match-none))))))
