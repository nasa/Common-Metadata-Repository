(ns cmr.ingest.test.services.email-processing
  "CMR subscription processing tests"
  (:require
   [clj-time.core :as t]
   [clojure.test :refer :all]
   [cmr.ingest.services.email-processing :as email-processing]))

(deftest subscription->time-constraint-test
  "Test the subscription->time-constraint function as it is criticle for internal
   use. Test for when there is and is not a last-notified-at date and when there
   is no date, test that the code will look back with a specified number of seconds"
  (let [now (t/now)
        one-hour-back (t/minus now (t/seconds 3600))
        two-hours-back (t/minus now (t/seconds 7200))]
    (testing
     "Usecase one: no last-notified-at date, so start an hour ago and end now"
      (let [data {:extra-fields {}}
            expected (str one-hour-back "," now)
            actual (#'email-processing/subscription->time-constraint data now 3600)]
        (is (= expected actual))))
    (testing
     "Usecase one: no last-notified-at date, it's explicitly set to nil, so
      start an hour ago and end now"
      (let [data {:extra-fields {:last-notified-at nil}}
            expected (str one-hour-back "," now)
            actual (#'email-processing/subscription->time-constraint data now 3600)]
        (is (= expected actual))))
    (testing
     "Usecase one: no last-notified-at date, it's explicitly set to nil, so start
      two hours ago and end now ; test that look back is not hardcoded"
      (let [data {:extra-fields {:last-notified-at nil}}
            expected (str two-hours-back "," now)
            actual (#'email-processing/subscription->time-constraint data now 7200)]
        (is (= expected actual))))

    (testing
     "Use case two: last-notified-at date is specified, start then and end now"
      (let [start "2012-01-10T08:00:00.000Z"
            data {:extra-fields {:last-notified-at start}}
            expected (str start "," now)
            actual (#'email-processing/subscription->time-constraint data now 3600)]
        (is (= expected actual))))
    (testing
     "Use case two: last-notified-at date is specified, start then and end now,
          look back seconds is ignored and can even be crazy"
      (let [start "2012-01-10T08:00:00.000Z"
            data {:extra-fields {:last-notified-at start}}
            expected (str start "," now)
            actual (#'email-processing/subscription->time-constraint data now -1234)]
        (is (= expected actual))))))
