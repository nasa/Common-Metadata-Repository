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

(defn create-acl
  [context acl]
  (-> context
      context->acl-db
      (swap! update-in [:acls] conj acl)))

(defn get-acls
  [context]
  (-> context
      context->acl-db
      deref
      :acls))