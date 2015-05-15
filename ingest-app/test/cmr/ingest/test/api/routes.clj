(ns cmr.ingest.test.api.routes
  (:require [clojure.test :refer :all]
            [cmr.ingest.api.routes :as r]))

(deftest verify-provider-cmr-only-against-client-id-test
  (testing "CMR Only flag is nil"
    (is (thrown-with-msg?
          java.lang.Exception
          #"CMR Only should not be nil, but is for Provider PROV1."
          (#'r/verify-provider-cmr-only-against-client-id "PROV1" nil "client-id"))))

  (testing "CMR Only flag and client id match"
    (are [cmr-only client-id]
         (nil? (#'r/verify-provider-cmr-only-against-client-id "PROV1" cmr-only client-id))

         true "any"
         false "Echo"))

  (testing "CMR Only flag and client id do not match"
    (are [cmr-only client-id msg]
         (thrown-with-msg?
           clojure.lang.ExceptionInfo
           msg
           (#'r/verify-provider-cmr-only-against-client-id "PROV1" cmr-only client-id))

         true "Echo"
         #"Provider PROV1 was configured as CMR Only which only allows ingest directly through the CMR. It appears from the client id that it was sent from ECHO."

         false "any"
         #"Provider PROV1 was configured as false for CMR Only which only allows ingest indirectly through ECHO. It appears from the client id \[any\] that ingest was not sent from ECHO.")))

