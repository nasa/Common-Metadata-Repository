(ns cmr.opendap.auth.token
  "The functions in this API are responsible for such things as making queries
  to CMR Access Control to get token-to-user mappings, extracting tokens from
  request headers, and defining caching keys and related tasks."
  (:require
   [clojure.data.xml :as xml]
   [cmr.opendap.components.caching :as caching]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.const :as const]
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
   [taoensso.timbre :as log]))

(def token-info-resource
  "The path segment to the CMR Access Control API resource that is queried
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
  "Extract the value of `Echo-Token` header that was passed in the request."
  [request]
  (request/get-header request "echo-token"))

(defn extract-user-name
  "Given a parsed XML element from a get-token-info payload, extract the user
  name."
  [xml-element]
  (when (= :user_name (:tag xml-element))
   (:content xml-element)))

(defn parse-token-data
  "Parse the XML that is returned when querying the CMR Access Control API for
  token info."
  [xml-str]
  (log/trace "Got token XML data:" xml-str)
  (first
    (remove nil?
      (mapcat #(when (map? %) (extract-user-name %))
              (:content (xml/parse-str xml-str))))))

(defn get-token-info
  "Query the CMR Access Control API for information assocated with the given
  token."
  [base-url token]
  (let [url (str base-url token-info-resource)
        data (str "id=" token)]
    (request/async-post
      url
      (-> {:body data}
          (request/add-token-header token)
          (request/add-form-ct))
      #(response/client-handler % parse-token-data))))

(defn ->user
  "Given a token, return the associated user name."
  [base-url token]
  @(get-token-info base-url token))

(defn ->cached-user
  "Look up the user for a token in the cache; if there is a miss, make the
  actual call for the lookup."
  [system token]
  (caching/lookup
   system
   (user-id-key token)
   #(->user (config/get-echo-rest-url system) token)))
