(ns cmr.mock-echo.data.urs-db
  "This is an in memory database for mocking URS.")

(def initial-db-state
  "Initial database state which is a map of usernames to passwords"
  {:users {}})

(defn create-db
  []
  (atom initial-db-state))

(defn- context->urs-db
  [context]
  (get-in context [:system :urs-db]))

(defn reset
  [context]
  (reset! (context->urs-db context) initial-db-state))

(defn create-users
  "Creates the list of users in the user db"
  [context users]
  (let [user-map (into {} (for [{:keys [username password]} users]
                            [username password]))
        user-db (context->urs-db context)]
    (swap! user-db update-in [:users] merge user-map)))

(defn user-exists?
  "Returns true if the user exists"
  [context username]
  (some? (get-in (deref (context->urs-db context)) [:users username])))

(defn password-matches?
  "Returns true if the user exists and their password matches"
  [context username password]
  (= password (get-in (deref (context->urs-db context)) [:users username])))


