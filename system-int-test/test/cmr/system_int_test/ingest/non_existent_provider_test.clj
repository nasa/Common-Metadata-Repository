(ns cmr.system-int-test.ingest.non-existent-provider-test
  "Non-existent provider error condition tests on various concept ingest endpoints."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.system-int-test.utils.ingest-util :as ingest]))

(deftest non-existent-provider-test
  (testing "ingest concept on a non-existent provider"
    (are [concept-type]
         (let [params {:method :put
                       :url (url/ingest-url "NOT_PROV" concept-type "native-id")
                       :body  "dummy body"
                       :content-type "application/echo10+xml"}
               {:keys [status errors]} (ingest/exec-ingest-http-request params)]
           (= [422 ["Provider with provider-id [NOT_PROV] does not exist."]]
              [status errors]))
         :collection
         :granule))

  (testing "validate concept on a non-existent provider"
    (are [concept-type]
         (let [params {:method :post
                       :url (url/validate-url "NOT_PROV" concept-type "native-id")
                       :body  "dummy body"
                       :content-type "application/echo10+xml"}
               {:keys [status errors]} (ingest/exec-ingest-http-request params)]
           (= [422 ["Provider with provider-id [NOT_PROV] does not exist."]]
              [status errors]))
         :collection
         :granule))

  (testing "delete concept on a non-existent provider"
    (are [concept-type]
         (let [params {:method :delete
                       :url (url/ingest-url "NOT_PROV" concept-type "native-id")}
               {:keys [status errors]} (ingest/exec-ingest-http-request params)]
           (= [422 ["Provider with provider-id [NOT_PROV] does not exist."]]
              [status errors]))
         :collection
         :granule)))
