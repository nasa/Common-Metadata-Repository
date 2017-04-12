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
   [compojure.core :refer :all]
   [ring.util.response :as rsp]))

(defconfig cmr-urs-username
  "A default username for the mock-echo dev environment"
  {:default "mock_urs_username"})

(defconfig cmr-urs-password
  "A default username for the mock-echo dev environment"
  {:default "mock_urs_password"})

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

(def successful-login-response
  {:status 200
   :body "<LoginResponse><reason></reason><success>true</success><user>...</user></LoginResponse>"
   :headers {"content-type" mt/xml}})

(def unsuccessful-login-response
  {:status 200
   :body "<LoginResponse><reason>Invalid username or password</reason><success>false</success></LoginResponse>"
   :headers {"content-type" mt/xml}})

(defn parse-login-request
  "Parses a login request into a map of username and password"
  [body]
  (let [parsed (x/parse-str body)]
    {:username (cx/string-at-path parsed [:username])
     :password (cx/string-at-path parsed [:password])}))

(defn login-xml
  "Processes a login request"
  [context body]
  (let [{:keys [username password]} (parse-login-request body)]
    (if (and (urs-db/user-exists? context username)
             (urs-db/password-matches? context username password))
      successful-login-response
      unsuccessful-login-response)))

(defn decode-base64
  [string]
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
    (when-not (and (= username cmr-urs-username) (= password cmr-urs-password))
      (errors/throw-service-error :unauthorized "Bad URS authentication info"))))

(defn build-routes [system]
  (routes
    (context "/urs" []
      ;;availability endpoint
      (GET "/" [] {:status 200})

      (POST "/login" {:keys [request-context body] :as request}
        (assert-urs-basic-auth-info request)
        (login-xml request-context (slurp body)))

      ;; echo kernel adds a / for some reason to the request it sends...
      (POST "/login/" {:keys [request-context body content-type] :as request}
        (assert-urs-basic-auth-info request)
        (login-xml request-context (slurp body)))

      (context "/groups/:group" [group]
        (context "/:username" [username]
          (DELETE "/" {context :request-context :as request}
            (urs-db/remove-user context username))))

      (context "/users" []

        ;; Create a bunch of users all at once
        ;; This is used for adding test data. Body should be a list of
        ;; maps with username, password, and other URS user fields
        (POST "/" {context :request-context body :body content-type :content-type}
          (if (= mt/xml content-type)
            (create-users-xml context (slurp body))
            (create-users-json context (slurp body))))

        (PUT "/:username" {context :request-context body :body content-type :content-type}
          (if (= mt/xml content-type)
            (create-users-xml context (slurp body))
            (create-users-json context (slurp body))))

        (context "/:username" [username]
          (GET "/" {:keys [request-context] :as request}
            (assert-urs-basic-auth-info request)
            (get-user request-context username)))))))
