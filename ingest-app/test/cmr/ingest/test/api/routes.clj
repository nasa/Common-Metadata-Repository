(ns cmr.ingest.test.api.routes
  (:require [clojure.test :refer :all]
            [cmr.common.util :as util]
            [cmr.ingest.api.routes :as r]))

(deftest verify-provider-cmr-only-against-client-id-test
  (testing "CMR Only flag is nil"
    (is (thrown-with-msg?
          java.lang.Exception
          #"CMR Only should not be nil, but is for Provider PROV1."
          (#'r/verify-provider-cmr-only-against-client-id "PROV1" nil "client-id"))))

  (testing "CMR Only flag and client id match"
    (util/are2 [cmr-only client-id]
               (nil? (#'r/verify-provider-cmr-only-against-client-id "PROV1" cmr-only client-id))

               "CMR Only, client id is not Echo is OK"
               true "any"

               "CMR Only false, client id is Echo is OK"
               false "Echo"))

  (testing "CMR Only flag and client id do not match"
    (util/are2 [cmr-only client-id msg]
               (thrown-with-msg?
                 clojure.lang.ExceptionInfo
                 msg
                 (#'r/verify-provider-cmr-only-against-client-id "PROV1" cmr-only client-id))

               "CMR Only, client id is Echo is not OK"
               true "Echo"
               #"Provider PROV1 was configured as CMR Only which only allows ingest directly through the CMR. It appears from the client id that it was sent from ECHO."

               "CMR Only false, client id is not Echo is not OK"
               false "any"
               #"Provider PROV1 was configured as false for CMR Only which only allows ingest indirectly through ECHO. It appears from the client id \[any\] that ingest was not sent from ECHO.")))

