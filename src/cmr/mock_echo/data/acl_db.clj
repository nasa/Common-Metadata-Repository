(ns cmr.mock-echo.data.acl-db)

(def initial-db-state
  {:last-id 0
   :acls {}})

(defn create-db
  []
  (atom initial-db-state))

(defn- context->acl-db
  [context]
  (get-in context [:system :acl-db]))

(defn reset
  [context]
  (reset! (context->acl-db context) initial-db-state))

(defn- next-guid
  [context]
  (let [next-id (-> context
                    context->acl-db
                    (swap! update-in [:last-id] inc)
                    :last-id)]
    (str "guid" next-id)))

(defn create-acl
  [context acl]
  (let [guid (next-guid context)
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