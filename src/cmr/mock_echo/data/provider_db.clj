(ns cmr.mock-echo.data.provider-db)

(def initial-db-state
  {:providers []})

(defn create-db
  []
  (atom initial-db-state))

(defn- context->provider-db
  [context]
  (get-in context [:system :provider-db]))

(defn reset
  [context]
  (reset! (context->provider-db context) initial-db-state))

(defn create-providers
  [context providers]
  (-> context
      context->provider-db
      (swap! assoc :providers providers)))

(defn get-providers
  [context]
  (-> context
      context->provider-db
      deref
      :providers))