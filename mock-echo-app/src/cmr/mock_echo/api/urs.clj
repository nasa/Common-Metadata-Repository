(ns cmr.mock-echo.api.urs
  "Defines routes for mocking URS"
  (:require
   [cheshire.core :as json]
   [clojure.data.codec.base64 :as b64]
   [clojure.data.xml :as x]
   [clojure.string :as str]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.xml :as cx]
   [cmr.mock-echo.data.urs-db :as urs-db]
   [cmr.transmit.config :as transmit-config]
   [compojure.core :refer :all]
   [ring.util.response :as rsp]))

(defn get-user
  "Processes a request to get a user."
  [context name]
  (if (urs-db/user-exists? context name)
    (let [{:keys [username password email affiliation studyArea country phone firstName lastName
                  organization userType address1 address2 address3 city state zip]}
          (urs-db/get-user context name)]
      {:status 200 :body (str
                          "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
                           <user>
                            <accountCreatedDate>2003-08-24T00:00:00Z</accountCreatedDate>
                            <accountStatus>ACTIVE</accountStatus>"
                            (if address1 (str "<address1>" address1 "</address1>"))
                            (if address2 (str "<address2>" address2 "</address2>"))
                            (if address3 (str "<address3>" address3 "</address3>"))
                            (if affiliation (str "<affiliation>" affiliation "</affiliation>"))
                            (if city (str "<city>" city "</city>"))
                            (if country (str "<country>" country "</country>"))
                            "<emailAddress>" email "</emailAddress>"
                            (if firstName (str "<firstName>" firstName "</firstName>"))
                            (if lastName (str "<lastName>" lastName "</lastName>"))
                            (if state (str "<state>" state "</state>"))
                            (if studyArea (str "<studyArea>" studyArea "</studyArea>"))
                            (if organization (str "<organization>" organization "</organization>"))
                            (if phone (str "<phone>" phone "</phone>"))
                            "<userName>" username "</userName>"
                            (if userType (str "<userType>" userType "</userType>"))
                            (if zip (str "<zip>" zip "</zip>"))
                          "</user>")
       :headers {"content-type" mt/xml}})
    {:status 404 :body "Not found.\n"}))

(defn get-user-info
  "Returns mock URS info for a user"
  [user-id]
  (if-not (= "null" user-id)
    {:status 200 :body {:first_name "mock"
                        :last_name "name"
                        :uid user-id
                        :email_address (str user-id"@example.com")}}
    {:status 500 :body "<!DOCTYPE html>
                          <body>
                            <p> There has been an error processing your request. </p>
                          </body>
                        </html>"}))

(defn get-groups
  "Returns mock URS groups for a user"
  [context user-id]
  (if-not (= "null" user-id)
    (case user-id
      "edl-group-user1"
      {:status 200 :body [{:description "cmr test group"
                           :name "cmr_test_group"
                           :shared_user_group false
                           :app_uid "mock_test_application"
                           :client_id "cmr"
                           :tag "PROV1"
                           :created_by "mock_test_application"}
                          {:description "cmr test group"
                           :name "cmr_test_group2"
                           :shared_user_group false
                           :app_uid "mock_test_application"
                           :client_id "cmr"
                           :tag nil
                           :created_by "mock_test_application"}]}

      "edl-group-user3"
      {:status 200 :body [{:description "cmr test group"
                           :name "cmr_test_group3"
                           :shared_user_group false
                           :app_uid "mock_test_application"
                           :client_id "cmr"
                           :tag "PROV1"
                           :created_by "mock_test_application"}]}

      {:status 200 :body []})
    {:status 500 :body "<!DOCTYPE html>
                          <body>
                            <p> There has been an error processing your request. </p>
                          </body>
                        </html>"}))

(defn get-groups-for-user
  "Returns mock URS groups for a user"
  [context user-id]
  (if-not (= "null" user-id)
    (case user-id
      "edl-group-user1"
      {:status 200 :body {:user_groups
                          [{:group_id "group-id-1"
                            :description "cmr test group"
                            :name "cmr_test_group"
                            :shared_user_group false
                            :app_uid "mock_test_application"
                            :client_id "cmr"
                            :tag "PROV1"
                            :created_by "mock_test_application"}
                           {:group_id "group-id-2"
                            :description "cmr test group"
                            :name "cmr_test_group2"
                            :shared_user_group false
                            :app_uid "mock_test_application"
                            :client_id "cmr"
                            :tag nil
                            :created_by "mock_test_application"}]}}
      "edl-group-user3"
      {:status 200 :body {:user_groups
                          [{:group_id "group-id-3"
                            :description "cmr test group"
                            :name "cmr_test_group3"
                            :shared_user_group false
                            :app_uid "mock_test_application"
                            :client_id "cmr"
                            :tag "PROV1"
                            :created_by "mock_test_application"}]}}
      {:status 200 :body []})
    {:status 500 :body "<!DOCTYPE html>
                          <body>
                            <p> There has been an error processing your request. </p>
                          </body>
                        </html>"}))

(defn parse-create-user-xml
  "Parses a create user request into a map of user fields"
  [body]
  (let [parsed (x/parse-str body)]
    {:username (cx/string-at-path parsed [:userName])
     :password (cx/string-at-path parsed [:password])
     :email    (cx/string-at-path parsed [:emailAddress])
     :affiliation (cx/string-at-path parsed [:affiliation])
     :studyArea (cx/string-at-path parsed [:studyArea])
     :country (cx/string-at-path parsed [:country])
     :phone (cx/string-at-path parsed [:phone])
     :firstName (cx/string-at-path parsed [:firstName])
     :lastName (cx/string-at-path parsed [:lastName])
     :organization (cx/string-at-path parsed [:organization])
     :userType (cx/string-at-path parsed [:userType])
     :address1 (cx/string-at-path parsed [:address1])
     :address2 (cx/string-at-path parsed [:address2])
     :address3 (cx/string-at-path parsed [:address3])
     :city (cx/string-at-path parsed [:city])
     :state (cx/string-at-path parsed [:state])
     :zip (cx/string-at-path parsed [:zip])}))

(defn create-users-json
  "Processes a JSON request to create one or more users."
  [context body]
  (let [users (json/decode body true)]
    (urs-db/create-users context users)
    {:status 201}))

(defn create-users-xml
  "Processes an XML request to create one or more users."
  [context body]
  (let [{:keys [username password] :as user} (parse-create-user-xml body)]
    (urs-db/create-users context [user])
    (assoc (get-user context username) :status 201)))

(defn decode-base64
  [^String string]
  (-> string .getBytes b64/decode String.))

(defn decode-basic-auth
  "Decodes basic auth to a map of username and password. Based on
  https://github.com/remvee/ring-basic-authentication"
  [request]
  (let [auth (get-in request [:headers "authorization"])
        cred (and auth (decode-base64 (last (re-find #"^Basic (.*)$" auth))))
        [user pass] (and cred (str/split (str cred) #":" 2))]
    {:username user :password pass}))

(defn assert-urs-basic-auth-info
  "URS expects that a basic auth client username and password is sent. This verifies that requests
  sent include this information."
  [request]
  (let [{:keys [username password]} (decode-basic-auth request)]
    (when-not (and (= username (transmit-config/urs-username)) (= password (transmit-config/urs-password)))
      (errors/throw-service-error :unauthorized "Bad URS authentication info"))))

(def mock-urs-token
  "A token for mock urs."
  "mock-urs-token")

(defn assert-bearer-token
  "Assert bearer token."
  [request]
  (when-not (= (format "Bearer %s" mock-urs-token)
               (get-in request [:headers "authorization"]))
    (errors/throw-service-error :unauthorized "Bad URS bearer token")))

(defmulti create-urs-token
  (fn [request]
    (keyword (get-in request [:params :grant_type]))))

(defmethod create-urs-token :client_credentials
  [request]
  (assert-urs-basic-auth-info request)
  {:status 200
   :headers {"content-type" mt/json}
   :body (format "{\"access_token\": \"%s\"}"
                 mock-urs-token)})

(defmethod create-urs-token :default
  [_]
  (errors/throw-service-error :not-found "Mock URS grant type not yet supported."))

(defn build-routes [system]
  (routes
    (context "/urs" []
      ;;availability endpoint
      (GET "/" [] {:status 200})

      (context "/oauth" []
        (POST "/token" request
          (create-urs-token request)))

      (context "/api/users" []
        (GET "/verify_uid" {:keys [request-context params] :as request}
          (assert-bearer-token request)
          (get-user request-context (:uid params)))
        ;; While a real URS connection would return more than just the email address,
        ;; we only need the email address to fill in for the subscription concept.
        (GET "/:user-id" {{:keys [user-id]} :params}
          (get-user-info user-id)))

      (context "/api/user_groups" []
        (GET "/search" {:keys [request-context params] :as request}
          (assert-bearer-token request)
          (get-groups request-context (:user_ids params)))

        (context ["/groups_for_user/:user-id" :user-id #".*$"] [user-id]
          (GET "/" {:keys [request-context params] :as request}
            (assert-bearer-token request)
            (get-groups-for-user request-context user-id))))

      (context "/users" []
        ;; Create a bunch of users all at once
        ;; This is used for adding test data. Body should be a list of
        ;; maps with username, password, and other URS user fields
        (POST "/" {context :request-context body :body content-type :content-type}
          (if (= mt/xml content-type)
            (create-users-xml context (slurp body))
            (create-users-json context (slurp body))))))))
