(ns cmr.access-control.test.services.acl-service
  (require [clojure.test :refer :all]
           [cmr.access-control.services.acl-service :as as]))

(deftest valid-permitted-group?
  (testing "Valid permitted groups"
    (are [group]
         (#'as/valid-permitted-group? group)

         "guest"
         "registered"
         "GUEST"
         "Registered"
         "AG1234-CMR"
         "ag1234-CMR"
         "Ag1234-CMR"
         "aG1234-CMR"
         "AG1234-PROV1"))

  (testing "Invalid permitted groups"
    (are [group]
         (not (#'as/valid-permitted-group? group))

         ""
         "gust"
         "A1234-PROV1"
         "G1234-PROV1"
         "AG1234PROV1"
         "AG*-PROV1"
         "AG123?-PROV1")))

