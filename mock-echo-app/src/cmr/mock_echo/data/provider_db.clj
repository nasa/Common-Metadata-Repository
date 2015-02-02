(ns cmr.mock-echo.data.provider-db)

(def initial-db-state
  {:providers {}})

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
  (let [provider-map (into {} (for [provider providers]
                                [(get-in provider [:provider :id]) provider]))
        provider-db (context->provider-db context)]
    (swap! provider-db update-in [:providers] merge provider-map)))

(defn get-providers
  "Returns a list of providers in the format [{:provider {:id prov1guid :provider_id prov1}]"
  [context]
  (-> context
      context->provider-db
      deref
      :providers
      vals))

(defn provider-id->provider-guid
  "Return the provider guid for the given provider id."
  [context provider-id]
  (->> (get-providers context)
       (map :provider)
       (filter #(= (:provider_id %) provider-id))
       first
       :id))
