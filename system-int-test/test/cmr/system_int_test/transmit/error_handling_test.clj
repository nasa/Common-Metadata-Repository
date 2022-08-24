(ns cmr.system-int-test.transmit.error-handling-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest gateway-timeout-test
  (testing "Gateway Timeouts are handled"
    (testing "during search"
      (is (= {:status 504
              :errors ["A gateway timeout occurred, please try your request again later."]}
             ;; gateway-timeout is a special-token that triggers a 504 in mock-urs
             (search/find-refs :collection {:token "gateway-timeout"}))))

    (testing "during ingest"
      (let [{:keys [status body]} (ingest/ingest-concept
                                   (data-umm-c/collection-concept
                                    {:provider-id "PROV1"
                                     :StandardProduct true}
                                    :umm-json)
                                   {:token "gateway-timeout"})]
        (is (= 504 status))
        (is (= {:errors ["A gateway timeout occurred, please try your request again later."]}
               (json/parse-string body true)))))))
