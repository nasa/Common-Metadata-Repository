(ns cmr.common-app.api.token-denylist
  "Rejects requests whose token, or the user it belongs to, is on the token deny list, and
  defines the routes for managing the list."
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.launchpad-token-validation :as lt-validation]
   [cmr.common-app.api.request-context-user-augmenter :as context-augmenter]
   [cmr.common.cache :as cache]
   [cmr.common.log :refer [info]]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as tk]
   [cmr.common.util :as util]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.token-denylist :as denylist]
   [cmr.transmit.tokens :as tokens]
   [compojure.core :refer [context DELETE GET PUT]]))

(defn- token->user-id
  [ctx token]
  ;; Legacy tokens resolve through the retired ECHO REST call, which logs the token.
  (when-not (util/is-legacy-token? token)
    (or (when (= token (:token ctx))
          (util/get-real-or-lazy ctx :user-id))
        (if-let [user-id-cache (cache/context->cache ctx context-augmenter/token-user-id-cache-name)]
          (cache/get-value user-id-cache token #(tokens/get-user-id ctx token))
          (tokens/get-user-id ctx token)))))

(defn verify-token-allowed
  "Throws unauthorized when the token, by default the context's, or its user is on the deny list."
  ([ctx]
   (verify-token-allowed ctx (:token ctx)))
  ([ctx token]
   (when (and token
              (not (transmit-config/echo-system-token? token))
              (denylist/denied? ctx token #(token->user-id ctx token)))
     (errors/throw-service-error
      :unauthorized
      (format "Token [%s] has been revoked. Note the token value has been partially redacted."
              (util/scrub-token token))))))

(defn deny-listed-token-handler
  "Ring handler that rejects deny-listed tokens. It must run after the token is added to the
  request context."
  [handler]
  (fn [request]
    (verify-token-allowed (:request-context request))
    (handler request)))

(def ^:private path->entry-type
  {"tokens" :token
   "users" :user})

(defn- validate-id
  [entry-type id]
  (let [id (if (= :token entry-type) (string/lower-case id) id)]
    (when-not (if (= :token entry-type)
                (re-matches #"[0-9a-f]{64}" id)
                (re-matches #"\S+" id))
      (errors/throw-service-error
       :bad-request
       (if (= :token entry-type)
         "Token entries are identified by the token's SHA-256 hash in hex."
         "User id must be non-blank and contain no whitespace.")))
    id))

(defn- parse-reason
  [body]
  (when-not (string/blank? body)
    (let [parsed (try
                   (json/parse-string body true)
                   (catch Exception _
                     (errors/throw-service-error :bad-request "Request body must be JSON.")))]
      (when-not (map? parsed)
        (errors/throw-service-error :bad-request "Request body must be a JSON object."))
      (:reason parsed))))

(defn- json-response
  [status body]
  {:status status
   :headers {"Content-Type" (mt/with-utf-8 mt/json)}
   :body (json/generate-string body)})

(defn- entries-response
  [entries]
  {:tokens (for [[token-hash metadata] (:token entries)]
             (assoc metadata :hash token-hash))
   :users (for [[user-id metadata] (:user entries)]
            (assoc metadata :user_id user-id))})

(defn- requesting-user
  [ctx]
  (some->> (:token ctx) (tokens/get-user-id ctx)))

(defn- add-entry
  [ctx entry-type id body]
  (let [id (validate-id entry-type id)
        metadata {:reason (parse-reason body)
                  :added_by (requesting-user ctx)
                  :added_at (str (tk/now))}]
    (denylist/add-entry! entry-type id metadata)
    (denylist/reload-snapshot ctx)
    (info (format "User [%s] added %s [%s] to the token deny list. Reason: [%s]"
                  (:added_by metadata) (name entry-type) id (:reason metadata)))
    (json-response 200 metadata)))

(defn- remove-entry
  [ctx entry-type id]
  (let [id (validate-id entry-type id)]
    (if (denylist/remove-entry! entry-type id)
      (do
        (denylist/reload-snapshot ctx)
        (info (format "User [%s] removed %s [%s] from the token deny list."
                      (requesting-user ctx) (name entry-type) id))
        {:status 204})
      (json-response 404 {:errors [(format "No %s deny list entry for [%s]." (name entry-type) id)]}))))

(defn- verify-write
  [ctx]
  (lt-validation/validate-write-token ctx)
  (acl/verify-ingest-management-permission ctx :update))

(def token-denylist-api-routes
  "Routes for listing, adding and removing deny list entries. Callers need system ingest
  management permission."
  (context "/token-denylist" []
    (GET "/" {ctx :request-context}
      (acl/verify-ingest-management-permission ctx :read)
      (json-response 200 (entries-response (denylist/get-entries))))
    (context ["/:entry-path/:id" :entry-path #"tokens|users"] [entry-path id]
      (PUT "/" {ctx :request-context body :body headers :headers}
        (verify-write ctx)
        (when-let [content-type (get headers "content-type")]
          (when-not (string/starts-with? (string/lower-case content-type) mt/json)
            (errors/throw-service-error :bad-request "Content-Type must be application/json.")))
        (add-entry ctx (path->entry-type entry-path) id (some-> body slurp)))
      (DELETE "/" {ctx :request-context}
        (verify-write ctx)
        (remove-entry ctx (path->entry-type entry-path) id)))))
