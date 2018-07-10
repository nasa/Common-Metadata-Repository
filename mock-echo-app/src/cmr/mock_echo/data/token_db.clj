(ns cmr.mock-echo.data.token-db
  (:require
   [clj-time.core :as t]
   [cmr.common-app.services.search.datetime-helper :as datetime-helper]
   [cmr.common.time-keeper :as tk]
   [cmr.transmit.config :as transmit-config]))

(def token-expiration-days
  "Defines the number of days that a token expires after it is created."
  30)

(defn initial-db-state
  []
  {:last-id 0
   ;; a map of token ids to maps containing username and group_guids
   :token-map {(transmit-config/echo-system-token)
               {:username (transmit-config/echo-system-username)
                :group_guids [(transmit-config/administrators-group-legacy-guid)]}}})

(defn create-db
  "Creates a new empty token database"
  []
  (atom (initial-db-state)))

(defn- context->token-db
  [context]
  (get-in context [:system :token-db]))

(defn- new-token-id
  [token-db]
  (let [new-state (swap! token-db update-in [:last-id] inc)]
    ;; Tokens are returned in this format so they will be easily distinguishable from real tokens.
    ;; This is still within the format that a GUID would look like from ECHO.
    (str "ABC-" (:last-id new-state))))

(defn- save-token
  [token-db token]
  (swap! token-db update-in [:token-map] assoc (:id token) token))

(defn create
  "Creates a token in the database with the given token information. Returns the token info with the
  :id of the token that was created"
  [context token-info]
  (let [token-db (context->token-db context)
        token (assoc token-info :id (new-token-id token-db))
        token (if (:expires token)
                token
                (assoc token :expires (datetime-helper/datetime->string
                                       (t/plus (tk/now) (t/days token-expiration-days)))))]
    (save-token token-db token)
    token))

(defn fetch
  [context id]
  (let [token-db (context->token-db context)]
    (-> token-db deref :token-map (get id))))

(defn delete
  [context id]
  (let [token-db (context->token-db context)]
    (swap! token-db update-in [:token-map] dissoc id)))

(defn reset
  [context]
  (reset! (context->token-db context) (initial-db-state)))

(comment

  (create {:system user/system} {:username "foo"})

  (-> user/system :token-db deref)

  (fetch {:system user/system} "ABC-1")

  (delete {:system user/system} "ABC-1"))
