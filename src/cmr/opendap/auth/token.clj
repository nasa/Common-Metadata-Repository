(ns cmr.opendap.auth.token
  (:require
   [clojure.data.xml :as xml]
   [cmr.opendap.components.caching :as caching]
   [cmr.opendap.const :as const]
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
   [org.httpkit.client :as httpc]
   [taoensso.timbre :as log]))

(def token-info-resource "/legacy-services/rest/tokens/get_token_info")

(defn token-data-key
  [token]
  (str "token-data:" token))

(defn user-id-key
  [token]
  (str "user-id:" token))

(defn extract
  [request]
  (request/get-header request "echo-token"))

(defn parse-token-data
  [xml-str]
  (log/debug "Got token XML data:" xml-str)
  (first
    (remove nil?
      (mapcat #(when (map? %)
                (when (= :user_name (:tag %)) (:content %)))
              (:content (xml/parse-str xml-str))))))

(defn get-token-info
  [base-url token]
  (let [url (str base-url token-info-resource)
        data (str "id=" token)]
    (httpc/request (-> request/default-options
                       (request/options :method :post :url url :body data)
                       (request/add-token-header token)
                       (request/add-form-ct)
                       (request/add-client-id)
                       ((fn [x] (log/trace "Prepared request:" x) x)))
                   #(response/client-handler % parse-token-data))))

(defn ->user
  [base-url token]
  @(get-token-info base-url token))

(defn ->cached-user
  [system base-url token]
  (caching/lookup
   system
   (user-id-key token)
   #(->user base-url token)))
