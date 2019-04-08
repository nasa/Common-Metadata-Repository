(ns cmr.system-int-test.utils.site-util
  "This contains utilities for testing sites."
  (:require
   [clj-http.client :as client]
   [clojure.test :refer [is testing]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.transmit.config :as transmit-config]))

(defn- add-token-header
  "Create or update HTTP options that include the CMR token header."
  ([token]
   (add-token-header token {}))
  ([token options]
   (assoc-in options [:headers transmit-config/token-header] token)))

(defn get-search-response
  "Given a URL path (and possibly options), execute an HTTP `GET` with that
  path on the CMR Search endpoint."
  ([url-path]
   (get-search-response url-path {}))
  ([url-path options]
   (-> (transmit-config/application-public-root-url :search)
       (str url-path)
       (client/get options))))

(defn assert-search-directory-regen-permissions
  [test-section url-path]
  (testing test-section
    (let [no-perms-error ["You do not have permission to perform that action."]]
      (testing "anonymous"
        (let [response (get-search-response url-path {:throw-exceptions? false})]
          (is (= 401 (:status response)))
          (is (= no-perms-error (search/safe-parse-error-xml (:body response))))))
      (testing "nil token"
        (let [response (get-search-response
                        url-path
                        (add-token-header nil {:throw-exceptions? false}))]
          (is (= 401 (:status response)))
          (is (= no-perms-error (search/safe-parse-error-xml (:body response))))))
      (testing "fake token"
        (let [response (get-search-response
                        url-path
                        (add-token-header "ABC" {:throw-exceptions? false}))]
          (is (= 401 (:status response)))
          (is (= ["Token does not exist"]
                 (search/safe-parse-error-xml (:body response))))))
      (testing "regular user token"
        (let [response (get-search-response
                        url-path
                        (add-token-header (e/login (s/context) "user")
                                          {:throw-exceptions? false}))]
          (is (= 401 (:status response)))
          (is (= no-perms-error (search/safe-parse-error-xml (:body response))))))
      (testing "admin"
        (let [admin-group-id (e/get-or-create-group (s/context) "admin-group")
              admin-user-token (e/login (s/context) "admin-user" [admin-group-id])
              _ (e/grant-group-admin (s/context) admin-group-id :update)
              ;; Need to clear the ACL cache to get the latest ACLs from mock-echo
              _ (search/clear-caches)
              response (get-search-response url-path (add-token-header admin-user-token))]
          (is (= 200 (:status response))))))))
