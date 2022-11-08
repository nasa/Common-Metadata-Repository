(ns cmr.authz.token
  "The functions in this API are responsible for such things as making queries
  to CMR Access Control to get token-to-user mappings, extracting tokens from
  request headers, and defining caching keys and related tasks."
  (:require
   [clojure.data.xml :as xml]
   [cmr.authz.http :as http]
   [cmr.http.kit.request :as request]
   [cmr.http.kit.response :as response]
   [taoensso.timbre :as log]
   [xml-in.core :as xml-in]))

(def token-info-resource
  "The path segment to the ECHO REST API resource that is queried
  in order to get user/token mappings."
  "/tokens/get_token_info")

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

(defn parse-username
  "Parse the XML that is returned when querying the CMR Access Control API for
  the username associated with the token."
  [xml-str]
  (-> xml-str
      (find-xml [:token_info :user_name])
      first))

(defn get-token-info
  "Query the CMR Access Control API for information assocated with the given
  token."
  [base-url token]
  (let [url (str base-url token-info-resource)
        data (str "id=" token)]
    (log/trace "Making token-info query to ECHO REST:" url)
    (request/async-post
      url
      (-> {:body data}
          (http/add-options)
          (request/add-token-header token)
          (request/add-form-ct))
      {}
      #(response/body-only-response-handler % response/error-handler parse-username))))

(defn ->user
  "Given a token, return the associated user name."
  [base-url token]
  (let [result @(get-token-info base-url token)
        errors (:errors result)]
    (if errors
      (throw (ex-info (first errors) result))
      result)))
