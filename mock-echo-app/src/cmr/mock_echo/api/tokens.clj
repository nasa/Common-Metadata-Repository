(ns cmr.mock-echo.api.tokens
  "Defines the HTTP URL routes for tokens."
  (:require [compojure.core :refer :all]
            [cheshire.core :as json]
            [clojure.set :as set]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.services.errors :as svc-errors]
            [cmr.mock-echo.data.token-db :as token-db]
            [cmr.mock-echo.api.api-helpers :as ah]))


(def token-keys
  #{
    ;; Fields provided during login
    :username
    :client_id
    :password
    :user_ip_address

    ;; A list of group guids the user belongs to.
    :group_guids
    })

(defn login
  [context body]
  (let [{info :token} (json/decode body true)
        keys-specified (set (keys info))
        unknown-keys (set/difference keys-specified token-keys)]
    (when (seq unknown-keys)
      (svc-errors/throw-service-error :invalid-data (str "Unkown keys used during login " (pr-str unknown-keys))))
    ;; Future enhancement: validate that min fields are provided
    (token-db/create context info)))

(defn- get-token-or-error
  "Helper for getting the token if it exists or thowing an error"
  [context token-id]
  (if-let [token (token-db/fetch context token-id)]
    token
    (svc-errors/throw-service-error :unauthorized (str "Token [" token-id "] does not exist"))))

(defn logout
  [context token-id]
  (get-token-or-error context token-id)
  (token-db/delete context token-id))

(defn get-token-info
  [context token-id]
  (let [{:keys [username client_id id]} (get-token-or-error context token-id)]
    {:token_info {:user_name username
                  :client_id client_id
                  :guest (= username "guest")
                  :token id
                  :user_guid "unsupported-in-mock-echo"}}))

(def guest-sid
  {:sid {:user_authorization_type_sid {:user_authorization_type "GUEST"}}})

(def registered-user-sid
  {:sid {:user_authorization_type_sid {:user_authorization_type "REGISTERED"}}})

(defn get-current-sids
  "Returns the groups the user who owns the token belongs to"
  [context token-id]
  (let [{:keys [username group_guids]} (get-token-or-error context token-id)]
    (if (= username "guest")
      [guest-sid]
      (cons registered-user-sid
            (map (fn [guid]
                   {:sid {:group_sid {:group_guid guid}}})
                 group_guids)))))

(defn build-routes [system]
  (routes
    (context "/tokens" []
      ;; Login
      (POST "/" {params :params context :request-context body :body}
        (let [token (login context (slurp body))
              url (str "http://localhost:3000/tokens/" (:id token))]
          {:status 201
           :content-type :json
           :headers {"Location" url}
           :body {:token token}}))
      (context "/:token-id" [token-id]
        ;; Logout
        (DELETE "/" {context :request-context}
          (logout context token-id)
          {:status 200})
        (GET "/current_sids" {context :request-context}
          ;; Does not require sys admin token
          (if (= "expired-token" token-id)
            ;; echo-rest returns status code 400 for request with expired token
            (ah/status-bad-request {:errors ["Token [expired-token] has expired."]})
            (ah/status-ok (get-current-sids context token-id))))
        (GET "/token_info" {context :request-context headers :headers }
          (if (= "expired-token" token-id)
            ;; echo-rest returns status code 400 for request with expired token
            (ah/status-bad-request {:errors ["Token [expired-token] has expired."]})
            (do
              (ah/require-sys-admin-token headers)
              (ah/status-ok (get-token-info context token-id)))))))
    (GET "/availability" {context :request-context}
      {:status 200})))
