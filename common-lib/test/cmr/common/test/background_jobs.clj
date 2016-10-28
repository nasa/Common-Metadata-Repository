(ns cmr.common.test.background-jobs
  "Contains tests for background jobs"
  (:require [clojure.test :refer :all]
            [cmr.common.background-jobs :as background-jobs]
            [cmr.common.lifecycle :as lifecycle]
            [clj-time.core :as t]))

;; Multiple lifecycle/stop and lifecycle/start calls to make sure that won't throw an error
(deftest increment-counter-test
  (let [counter (atom 1)]

   (def job (background-jobs/create-background-job #(swap! counter inc) 0.1))
   (def job (lifecycle/start job nil))
   (def job (lifecycle/start job nil))
   (def job (lifecycle/start job nil))
   (Thread/sleep 1000)
   (def job (lifecycle/stop job nil))
   (def job (lifecycle/stop job nil))
   (def job (lifecycle/stop job nil))
   (def job (lifecycle/stop job nil))

   (testing "Has counter been incremented"
     (is (> @counter 1)))))
