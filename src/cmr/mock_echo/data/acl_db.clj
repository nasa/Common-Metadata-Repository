(ns cmr.mock-echo.data.acl-db)

(def initial-db-state
  {:acls []})

(defn create-db
  []
  (atom initial-db-state))

(defn- context->acl-db
  [context]
  (get-in context [:system :acl-db]))

(defn reset
  [context]
  (reset! (context->acl-db context) initial-db-state))

(defn create-acls
  [context acls]
  (-> context
      context->acl-db
      (swap! assoc :acls acls)))

(defn get-acls
  [context]
  (-> context
      context->acl-db
      deref
      :acls))