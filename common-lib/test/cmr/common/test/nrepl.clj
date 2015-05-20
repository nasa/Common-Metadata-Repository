(ns cmr.common.test.nrepl
  "Tests for the nREPL component."
  (:require [clojure.test :refer :all]
            [clojure.tools.nrepl :as nrepl]
            [cmr.common.lifecycle :as lifecycle]
            [cmr.common.nrepl :as nrepl-component]))

(deftest test-nrepl-component
  (let [repl (lifecycle/start (nrepl-component/create-nrepl 0) nil)
        response (with-open [conn (nrepl/connect :port (:port repl))]
                   (-> (nrepl/client conn 1000)
                       (nrepl/message {:op :eval :code "(+ 21 21)"})
                       nrepl/response-values
                       first))]
    (is (= 42 response))
    (lifecycle/stop repl nil)))

(deftest test-nrepl-port-in-use
  (let [repl (lifecycle/start (nrepl-component/create-nrepl 0) nil)]
    (is (thrown? java.net.BindException (lifecycle/start (nrepl-component/create-nrepl (:port repl)) nil)))
    (lifecycle/stop repl nil)))
