(ns cmr.mock-echo.data.acl-db
  (:require
   [cmr.transmit.config :as transmit-config]))

(defn initial-db-state
  []
  {:last-id 0
   ;; A map of guids to acls
   ;; Contains an initial guid that grants the mock admin user ingest management permission.
   :acls {"mock-admin-acl-guid"
          {:acl
           {:id "mock-admin-acl-guid",
            :access_control_entries
            [{:sid {:group_sid {:group_guid (transmit-config/administrators-group-legacy-guid)}},
              :permissions ["READ" "UPDATE"]}],
            :system_object_identity {:target "INGEST_MANAGEMENT_ACL"}}}}})

(defn create-db
  []
  (atom (initial-db-state)))

(defn- context->acl-db
  [context]
  (get-in context [:system :acl-db]))

(defn reset
  [context]
  (reset! (context->acl-db context) (initial-db-state)))

(defn- next-guid
  [context]
  (let [next-id (-> context
                    context->acl-db
                    (swap! update-in [:last-id] inc)
                    :last-id)]
    (str "guid" next-id)))

(defn create-acl
  [context acl]
  ;;Allow passing in a guid or auto generating
  (let [guid (or (get-in acl [:acl :id])
                 (next-guid context))
        acl (assoc acl :id guid)]
    (-> context
        context->acl-db
        (swap! update-in [:acls] assoc guid acl))
    acl))

(defn delete-acl
  [context guid]
  (-> context
      context->acl-db
      (swap! update-in [:acls] dissoc guid)))

(defn get-acls
  [context]
  (-> context
      context->acl-db
      deref
      :acls
      vals))
