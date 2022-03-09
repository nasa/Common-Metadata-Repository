(ns cmr.system-int-test.transmit.urs-test
  "Test namespace for URS requests."
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.mock-urs-client :as mock-urs-client]
   [cmr.system-int-test.system :as system]
   [cmr.transmit.urs :as urs]))

(deftest urs-user-exists-test
  (mock-urs-client/create-users (system/context)
                                [{:username "urs-user" :password "pass"}])

  (testing "User does not exist without raw"
    (is (false? (urs/user-exists? (system/context)
                                  "fake-user"))))

  (testing "User does not exist with raw"
    (is (= 404 (:status (urs/user-exists? (system/context)
                                          "fake-user-2"
                                          true)))))

  (testing "User info can get user-email"
    (is (= "fake-user@example.com" (urs/get-user-email (system/context) "fake-user"))))

  (testing "User exists without raw"
    (is (urs/user-exists? (system/context)
                          "urs-user")))

  (testing "User exists with raw"
    (is (= 200 (:status (urs/user-exists? (system/context)
                                          "urs-user"
                                          true))))))
