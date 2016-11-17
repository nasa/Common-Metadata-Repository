(ns cmr.transmit.test.connection
  (:require
   [clojure.test :refer :all]
   [cmr.transmit.connection :as c]))

(defn failing-fn
  "Creates a function that fails N times with a socket exception and then returns return-value"
  [num-fails return-value]
  (let [fails-left (atom num-fails)]
    (fn []
      (if (> @fails-left 0)
        (do
          (swap! fails-left dec)
          (throw (java.net.SocketException. "Failing for test")))
        return-value))))

(deftest handle-socket-exception-retries-test
  (testing "No failures just works"
    (let [fail-none (constantly 5)]
      (is (= 5 (c/handle-socket-exception-retries (fail-none))))))
  (testing "Handle Failures by retrying"
    (let [fail-2 (failing-fn 2 :success)]
      (is (= :success (c/handle-socket-exception-retries {:num-retries 2 :pause-ms 1} (fail-2))))))
  (testing "Throw exception when failures exceed num retries"
    (let [fail-2 (failing-fn 2 :nothing-should-be-returned)]
      (is (thrown? java.net.SocketException
                   (c/handle-socket-exception-retries
                    {:num-retries 1 :pause-ms 1}
                    (fail-2)))))))


