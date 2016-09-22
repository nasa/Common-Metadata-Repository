(ns cmr.mock-echo.data.urs-db
  "This is an in memory database for mocking URS."
  (:require
   [clojure.string :as str]
   [cmr.transmit.config :as transmit-config]))

(defn initial-db-state
  "Initial database state which is a map of usernames to passwords"
  []
  {:users {(str/lower-case (transmit-config/echo-system-username)) "never login as this user"
           (str/lower-case transmit-config/local-system-test-user) transmit-config/local-system-test-password}})

(defn create-db
  []
  (atom (initial-db-state)))

(defn- context->urs-db
  [context]
  (get-in context [:system :urs-db]))

(defn reset
  [context]
  (reset! (context->urs-db context) (initial-db-state)))

(defn create-users
  "Creates the list of users in the user db"
  [context users]
  (let [user-map (into {} (for [{:keys [username password email affiliation] :as user} users]
                            [(str/lower-case username) user]))
        user-db (context->urs-db context)]
    (swap! user-db update-in [:users] merge user-map)))

(defn remove-user
  "Removes a user from the user db"
  [context username]
  (let [user-db (context->urs-db context)]
    (swap! user-db update-in [:users] dissoc username)))

(defn get-user
  "Returns the user map"
  [context username]
  (get-in (deref (context->urs-db context)) [:users (str/lower-case username)]))

(defn user-exists?
  "Returns true if the user exists"
  [context username]
  (some? (get-in (deref (context->urs-db context)) [:users (str/lower-case username)])))

(defn password-matches?
  "Returns true if the user exists and their password matches"
  [context username password]
  (let [user (get-in (deref (context->urs-db context)) [:users (str/lower-case username)])
        correct-password (get user :password)]
    (= password correct-password)))
