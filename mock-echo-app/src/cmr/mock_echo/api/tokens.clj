(ns cmr.mock-echo.api.tokens
  "Defines the HTTP URL routes for tokens."
  (:require
   [cheshire.core :as json]
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.services.errors :as svc-errors]
   [cmr.mock-echo.api.api-helpers :as ah]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.mock-echo.data.token-db :as token-db]
   [compojure.core :refer :all]))

(def ^:private error-504-gateway-timeout (ah/status-gateway-timeout "<html><head><title>504 Gateway Time-out</title></head><body><center><h1>504 Gateway Time-out</h1></center></body></html>"))

(def token-keys
  #{;; Fields provided during login
    :username
    :client_id
    :password
    :user_ip_address

    ;; A list of group guids the user belongs to.
    :group_guids})

(defn login
  [context body]
  (let [{info :token} (json/decode body true)
        keys-specified (set (keys info))
        unknown-keys (set/difference keys-specified token-keys)]
    (when (seq unknown-keys)
      (svc-errors/throw-service-error :invalid-data (str "Unkown keys used during login " (pr-str unknown-keys))))
    ;; Future enhancement: validate that min fields are provided
    (token-db/create context info)))

(defn- strip-bearer-if-exists
  "Remove 'Bearer ' from the front of the token if it exists"
  [token]
  (if (and (>= (count token) 7) (= "Bearer " (subs token 0 7)))
    (subs token 7)
    token))

(defn- get-token-or-error
  "Helper for getting the token if it exists or thowing an error"
  [context token-id]
  (let [token-id (strip-bearer-if-exists token-id)]
    (if-let [token (token-db/fetch context token-id)]
      token
      (svc-errors/throw-service-error :unauthorized (str "Token [" token-id "] does not exist")))))

(defn logout
  [context token-id]
  (get-token-or-error context token-id)
  (token-db/delete context token-id))

(defn- get-token-info-mock
  "Returns the mock token info for the token with the given token id"
  [context token-id]
  ;; for launchpad token, remove the padding before searching
  (let [token-id (if lt-validation/is-launchpad-token?
                   (string/replace token-id echo-util/LAUNCHPAD_TOKEN_PADDING "")
                   token-id)
        {:keys [username client_id expires id]} (get-token-or-error context token-id)]
    {:token_info {:user_name username
                  :client_id client_id
                  :guest (= username "guest")
                  :expires expires
                  :token id
                  :user_guid "unsupported-in-mock-echo"}}))

(def guest-sid
  {:sid {:user_authorization_type_sid {:user_authorization_type "GUEST"}}})

(def registered-user-sid
  {:sid {:user_authorization_type_sid {:user_authorization_type "REGISTERED"}}})

(defn- get-current-sids
  "Returns the groups the user who owns the token belongs to"
  [context token-id]
  (let [{:keys [username group_guids]} (get-token-or-error context token-id)]
    (if (= username "guest")
      [guest-sid]
      (cons registered-user-sid
            (map (fn [guid]
                   {:sid {:group_sid {:group_guid guid}}})
                 group_guids)))))

(defn- get-token-info
  "Returns the token info for the given token"
  [context headers token-id]
  (case token-id
    "expired-token" (ah/status-bad-request {:errors ["Token [expired-token] has expired."]})
    "gateway-timeout" error-504-gateway-timeout

    ;; default
    (do
      (ah/require-sys-admin-token headers)
      (ah/status-ok (get-token-info-mock context token-id)))))

(defn build-routes [system]
  (routes
   (context "/tokens" []
      ;; Login
     (POST "/" {params :params context :request-context body :body}
       (let [token (login context (slurp body))
             url (str "http://localhost:3000/tokens/" (:id token))]
         (case token
           "gateway-timeout" error-504-gateway-timeout

           ;; default
           {:status 201
            :content-type :json
            :headers {"Location" url}
            :body {:token token}})))
     (POST "/get_token_info" {context :request-context headers :headers {token-id :id} :params}
       (get-token-info context headers token-id))
     (context "/:token-id" [token-id]
        ;; Logout
       (DELETE "/" {context :request-context}
         (logout context token-id)
         {:status 200})
       (GET "/current_sids" {context :request-context}
         ;; Does not require sys admin token
         (case token-id
           "expired-token" (ah/status-bad-request {:errors ["Token [expired-token] has expired."]})
           "gateway-timeout" error-504-gateway-timeout

           ;; default
           (ah/status-ok (get-current-sids context token-id))))
       (GET "/token_info" {context :request-context headers :headers}
         (get-token-info context headers token-id))))
   (GET "/availability" {context :request-context}
     {:status 200})))
