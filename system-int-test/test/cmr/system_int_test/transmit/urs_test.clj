(ns cmr.system-int-test.transmit.urs-test
  "Test namespace for URS requests."
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.mock-urs-client :as mock-urs-client]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.urs :as urs]))

(def urs-context-atom
  "An atom containing the cached connection context map."
  (atom nil))

(defn urs-context
  "URS context with config needed for testing purposes."
  []
  (when-not @urs-context-atom
    (reset! urs-context-atom {:system (transmit-config/system-with-connections
                                       {}
                                       [:urs])}))
  @urs-context-atom)

(defn- urs-relative-root-url-fixture
  "Need to ensure the relative root is set to /urs for mock-urs to be able to received
  URS requests. This is set to \"\" by default when running from outside of dev-system."
  [f]
  (let [saved-relative-root-url (transmit-config/urs-relative-root-url)]
    (transmit-config/set-urs-relative-root-url! "/urs")
    (f)
    (transmit-config/set-urs-relative-root-url! saved-relative-root-url)))

(use-fixtures :once urs-relative-root-url-fixture)

(deftest urs-user-exists-test
  (mock-urs-client/create-users (urs-context)
                                [{:username "urs-user" :password "pass"}])

  (testing "User does not exist without raw"
    (is (false? (urs/user-exists? (urs-context)
                                  "fake-user"))))

  (testing "User does not exist with raw"
    (is (= 404 (:status (urs/user-exists? (urs-context)
                                          "fake-user-2"
                                          true)))))

  (testing "User info can get user-email"
    (is (= "fake-user@example.com" (urs/get-user-email (urs-context) "fake-user"))))

  (testing "User exists without raw"
    (is (urs/user-exists? (urs-context)
                          "urs-user")))

  (testing "User exists with raw"
    (is (= 200 (:status (urs/user-exists? (urs-context)
                                          "urs-user"
                                          true))))))
