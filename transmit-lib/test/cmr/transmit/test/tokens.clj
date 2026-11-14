(ns cmr.transmit.test.tokens
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest is]]
   [cmr.transmit.tokens :as tokens]))

(deftest loggable-params-test
  (let [system-token "system-token-0123456789-abcdefghij"
        user-token "user-token-0123456789-abcdefghij"
        logged (pr-str (#'tokens/loggable-params
                        {:connection-manager :pool
                         :socket-timeout 1000
                         :headers {"Authorization" system-token "Accept" "application/json"}
                         :form-params {:id user-token}}))]
    (is (not (string/includes? logged system-token)))
    (is (not (string/includes? logged user-token)))
    (is (not (string/includes? logged ":connection-manager")))
    (is (string/includes? logged "application/json"))))
