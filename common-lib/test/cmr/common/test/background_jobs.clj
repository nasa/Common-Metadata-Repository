(ns cmr.common.test.background-jobs
  "Contains tests for background jobs"
  (:require
   [clojure.test :refer :all]
   [cmr.common.background-jobs :as background-jobs]
   [cmr.common.lifecycle :as lifecycle]))

(deftest background-jobs-test
  (let [counter1 (atom 0)
        counter2 (atom 0)
        counter3 (atom 0)
        job-10-times-a-second (background-jobs/create-background-job #(swap! counter1 inc) 0.1)
        job-5-times-a-second (background-jobs/create-background-job #(swap! counter2 inc) 0.2)
        job-1-time-a-second (background-jobs/create-background-job #(swap! counter3 inc) 1)]
    (testing "None of the counters are updated prior to start being called"
      (Thread/sleep 200)
      (is (= 0 @counter1 @counter2 @counter3)))

    (testing "Start all the jobs and verify each job is called the correct number of times"
      (let [job-10-times-a-second (lifecycle/start job-10-times-a-second nil)
            job-5-times-a-second (lifecycle/start job-5-times-a-second nil)
            job-1-time-a-second (lifecycle/start job-1-time-a-second nil)]
        (Thread/sleep 1000)
        ;; Allow for small discrepancy based on CPU times
        (is (<= 10 @counter1 11))
        (is (<= 5 @counter2 6))
        (is (<= 1 @counter3 2))

        (testing "When jobs are stopped the counter stops incrementing"
          (let [job-10-times-a-second (lifecycle/stop job-10-times-a-second nil)
                job-5-times-a-second (lifecycle/stop job-5-times-a-second nil)
                job-1-time-a-second (lifecycle/stop job-1-time-a-second nil)
                counter1-value @counter1
                counter2-value @counter2
                counter3-value @counter3]
            (Thread/sleep 1000)
            (is (= counter1-value @counter1))
            (is (= counter2-value @counter2))
            (is (= counter3-value @counter3))

            (testing "Calling stop on a stopped job is ok"
              (let [job-10-times-a-second (lifecycle/stop job-10-times-a-second nil)]
                (Thread/sleep 200)
                (is (= counter1-value @counter1))

                (testing "Previously stopped jobs can be started"
                  (let [job-10-times-a-second (lifecycle/start job-10-times-a-second nil)
                        job-5-times-a-second (lifecycle/start job-5-times-a-second nil)]
                    (Thread/sleep 550)
                    (is (<= (+ 5 counter1-value) @counter1 (+ 6 counter1-value)))
                    (is (<= (+ 2 counter2-value) @counter2 (+ 3 counter2-value)))
                    (is (= counter3-value @counter3))

                    (testing (str "Calling start on an already running job restarts the job and "
                                  "only 1 thread is running")
                      (let [job-10-times-a-second (lifecycle/start job-10-times-a-second nil)]
                        (Thread/sleep 550)
                        (is (<= (+ 10 counter1-value) @counter1 (+ 12 counter1-value)))
                        ;; Stop all the jobs
                        (lifecycle/stop job-10-times-a-second nil)
                        (lifecycle/stop job-5-times-a-second nil)
                        (lifecycle/stop job-1-time-a-second nil)))))))))))))
