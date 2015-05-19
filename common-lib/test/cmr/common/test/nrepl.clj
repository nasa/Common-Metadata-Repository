(ns cmr.common.test.nrepl
  "Tests for the nREPL component."
  (:require [clojure.test :refer :all]
            [clojure.tools.nrepl :as nrepl]
            [cmr.common.lifecycle :as lifecycle]
            [cmr.common.nrepl :as nrepl-component]))

(deftest test-nrepl-component
  (let [component (lifecycle/start (nrepl-component/create-nrepl 0) nil)
        port (:port component)
        response (with-open [conn (nrepl/connect :port port)]
                   (-> (nrepl/client conn 1000)
                       (nrepl/message {:op :eval :code "(+ 21 21)"})
                       nrepl/response-values
                       first))]
    (is (= 42 response))
    (lifecycle/stop component nil)))
