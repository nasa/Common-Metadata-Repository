(ns cmr.authz.token
  "The functions in this API are responsible for such things as making queries
  to CMR Access Control to get token-to-user mappings, extracting tokens from
  request headers, and defining caching keys and related tasks."
  (:require
   [buddy.core.keys :as buddy-keys]
   [buddy.sign.jwt :as jwt]
   [cheshire.core :as json]
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [cmr.authz.components.config :as config]
   [cmr.authz.http :as http]
   [cmr.http.kit.request :as request]
   [cmr.http.kit.response :as response]
   [taoensso.timbre :as log]
   [xml-in.core :as xml-in]))


(def token-info-resource
  "EDL Launchpad token verification resource."
  "/api/nams/edl_user")

(def client-token-resource
  "EDL client bearer token resource."
  "/oauth/token?grant_type=client_credentials")

(defn token-data-key
  "Generate a key to be used for caching token data."
  [token]
  (str "token-data:" token))

(defn user-id-key
  "Generate a key to be used for caching user-id data."
  [token]
  (str "user-id:" token))

(defn extract
  "Extract the value of `Authorization` or `Echo-Token` header that was passed in the request."
  [request]
  (when (request/get-header request "echo-token")
    (log/warn (format "Still using Echo-Token for authorization, client id: %s." (request/get-header request "client-id"))))
  (or (request/get-header request "authorization")
      (request/get-header request "echo-token")))

(defn find-xml
  [xml-str in-keys]
  (log/trace "Got token XML data:" xml-str)
  (xml-in/find-first (xml/parse-str xml-str) in-keys))

(defn parse-token-data
  "Parse the XML that is returned when querying the CMR Access Control API for
  token info."
  [xml-str]
  (find-xml xml-str [:token_info]))

(defn parse-token
  "Parse the XML that is returned when querying the CMR Access Control API for
  the token."
  [xml-str]
  (find-xml xml-str [:token_info :token]))

(defn parse-bearer-token
  [body]
  (:access_token (json/parse-string body true)))

(defn parse-username
  "Parse the XML that is returned when querying the CMR Access Control API for
  the username associated with the token."
  [xml-str]
  (-> xml-str
      (find-xml [:token_info :user_name])
      first))

(defn parse-lp-username
  [body]
  (:uid (json/parse-string body true)))

(defn add-basic-auth
  ([user pass]
   (add-basic-auth {} user pass))
  ([req user pass]
   (assoc req :basic-auth [user pass])))

(defn get-client-token
  "Get the CMR client bearer token from from the cache."
  [system]
  (let [base-url (config/get-edl-rest-url system)
        url (str base-url client-token-resource)]
    (request/async-post
     url
     (-> {}
         (request/add-accept "application/json")
         (request/add-basic-auth (config/get-edl-username system) (config/get-edl-password system)))
     {}
     #(response/general-response-handler % response/error-handler parse-bearer-token))))

(defn verify-edl-token-locally
  "Uses the EDL public key to verify jwt tokens locally."
  [system token]
  (try
    (log/trace "edl public key= " (config/get-jwt-public-key system))
    (let [public-key (buddy-keys/jwk->public-key (json/parse-string (config/get-jwt-public-key system) true))
          bearer-stripped-token (string/replace token #"Bearer\W+" "")
          decrypted-token (jwt/unsign bearer-stripped-token public-key {:alg :rs256})]
      {:body (:uid decrypted-token)})
    (catch Exception ex
      (log/error (.getMessage ex))
      (log/debug "JWT local token verification failed, trying launchpad instead."))))

(defn get-token-info
  "Query the CMR Access Control API for information assocated with the given
  token."
  [system token]
  (let [base-url (config/get-edl-rest-url system)
        url (str base-url token-info-resource)]
    (log/trace "Decoding jwt token to get token info")
    (if-let [token-info (verify-edl-token-locally system token)]
      (do
        (log/trace "user info found for jwt token, token-info=" token-info)
        (deliver (promise) token-info))
      (let [bearer-token @(get-client-token system)]
        (if (:errors bearer-token)
          (deliver (promise) bearer-token)
          (do
            (log/trace "retrieved cmr client token= " bearer-token)
            (log/trace "user info not found for jwt token, attempting to authenticate user via launchpad")
            (request/async-post
             url
             (-> {:body (str "token=" token)}
                 (http/add-options)
                 (request/add-token-header (str "Bearer " (:body bearer-token))))
             {}
             #(response/general-response-handler % response/error-handler parse-lp-username))))))))

(defn ->user
  "Given a token, return the associated user name."
  [system token]
  (let [result @(get-token-info system token)
        errors (:errors result)]
    (log/trace "result=" result)
    (if errors
      (throw (ex-info (first errors) result))
      (:body result))))
