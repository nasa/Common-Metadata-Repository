(ns cmr.system-int-test.transmit.error-handling-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util]
   [cmr.system-int-test.utils.search-util :as search]))

(deftest gateway-timeout-test
  (testing "Gateway Timeouts are handled"
    (is (= {:status 504
            :errors ["A gateway timeout occurred, please try your request again later."]}
           ;; gateway-timeout is a special-token that triggers a 504 in mock-urs
           (search/find-refs :collection {:token "gateway-timeout"})))))
