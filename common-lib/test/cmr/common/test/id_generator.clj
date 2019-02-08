(ns cmr.common.test.id-generator
  (:require [clojure.test :refer :all]
            [cmr.common.id-generator :as id-generator]
            [cmr.common.util :as util]))

(deftest test-id-from-state
  (testing "positioning"
    (are [parts id]
         (let [[time sequence worker] parts
               m {:worker worker
                  :sequence sequence
                  :time time}
               actual-id (id-generator/id-from-state m)]
           (is (= m (id-generator/state-from-id id)))
           (is (= id actual-id)))
         ; 6 byte time
         ; 1 byte sequence
         ; 1 byte worker
         [0 0 0]              0x0000000000000000
         [1 0 0]              0x0000000000010000
         [0 1 0]              0x0000000000000100
         [0 0 1]              0x0000000000000001
         [1 1 1]              0x0000000000010101
         [0x7fffffffffff 0 0] 0x7fffffffffff0000
         [0 0xff 0]           0x000000000000ff00
         [0 0 0xff]           0x00000000000000ff)))

(defmacro with-curr-time
  [val & args]
  "A macro that will override the current time in milliseconds call in id-generator to always return
  the given value."
  `(with-bindings {#'id-generator/current-time-millis (constantly ~val)}
     ~@args))

(defmacro with-curr-times
  [vals & args]
  "A macro that will override the current time in milliseconds call in id-generator so that repeated
  calls return each of the values in the list."
  `(with-bindings {#'id-generator/current-time-millis (util/sequence->fn ~vals)}
     ~@args))

(deftest test-new-id-state
  (testing "success"
    (with-curr-time
      5
      (is (= {:worker 1
              :sequence 0
              :time 5}
             (id-generator/new-id-state 1)))))
  (testing "Worker too large"
    (is (thrown-with-msg?
          IllegalArgumentException
          #"Worker value 256 is greater than max 255"
          (id-generator/new-id-state 256)))))

(deftest test-next-id-state
  (testing "time has increased"
    (let [id-state {:worker 1
                    :sequence 0
                    :time 5}
          next-time 6
          expected {:worker 1
                    :sequence 0
                    :time next-time}]
      (with-curr-time
        next-time
        (is (= expected (id-generator/next-id-state id-state))))))
  (testing "increase sequence"
    (let [id-state {:worker 1
                    :sequence 0
                    :time 5}
          expected {:worker 1
                    :sequence 1
                    :time 5}]
      (with-curr-time
        5
        (is (= expected (id-generator/next-id-state id-state))))))
  (testing "sequence roll over"
    (let [id-state {:worker 1
                    :sequence 254
                    :time 5}
          expected {:worker 1 :sequence 0 :time 5}]
      (with-curr-times
        [5 6]
        (is (= {:worker 1 :sequence 255 :time 5}
               (id-generator/next-id-state id-state)))
        (is (= {:worker 1 :sequence 0 :time 6}
               (id-generator/next-id-state id-state)))))))
