(ns cmr.ingest.test.services.jobs
  "This tests some of the more complicated functions of cmr.ingest.services.jobs"
  (:require
   [clj-time.core :as t]
   [clojure.test :refer :all]
   [cmr.common.util :as u :refer [are3]]
   [cmr.ingest.services.jobs :as jobs]))

(deftest create-query-params
  (is (= {"polygon" "-78,-18,-77,-22,-73,-16,-74,-13,-78,-18"
          "concept-id" "G123-PROV1"}
         (#'jobs/create-query-params "polygon=-78,-18,-77,-22,-73,-16,-74,-13,-78,-18&concept-id=G123-PROV1"))))

(deftest email-granule-url-list-test
  "This tests the utility function that unpacks a list of urls and turns it into markdown"
  (let [actual (jobs/email-granule-url-list '("https://cmr.link/g1"
                                              "https://cmr.link/g2"
                                              "https://cmr.link/g3"))
        expected (str "* [https://cmr.link/g1](https://cmr.link/g1)\n"
                      "* [https://cmr.link/g2](https://cmr.link/g2)\n"
                      "* [https://cmr.link/g3](https://cmr.link/g3)")]
    (is (= expected actual))))

(deftest create-email-test
  "This tests the HTML output of the email generation"
  (let [actual (jobs/create-email-content
                "cmr-support@earthdata.nasa.gov"
                "someone@gmail.com"
                '("https://cmr.link/g1" "https://cmr.link/g2" "https://cmr.link/g3")
                {:extra-fields {:collection-concept-id "C1200370131-EDF_DEV06"}
                 :metadata "{\"Name\": \"valid1\",
                            \"CollectionConceptId\": \"C1200370131-EDF_DEV06\",
                            \"Query\": \"updated_since[]=2020-05-04T12:51:36Z\",
                            \"SubscriberId\": \"someone1\",
                            \"EmailAddress\": \"someone@gmail.com\"}"
                 :start-time "2020-05-04T12:51:36Z"
                 :end-time "2020-05-05T12:51:36Z"})]
    (is (= "someone@gmail.com" (:to actual)))
    (is (= "Email Subscription Notification" (:subject actual)))
    (is (= "text/html" (:type (first (:body actual)))))
    (is (= (str "<p>You have subscribed to receive notifications when data is added to the following query:</p>"
                "<p><code>C1200370131-EDF&#95;DEV06</code></p>"
                "<p><code>updated&#95;since&#91;&#93;=2020-05-04T12:51:36Z</code></p>"
                "<p>Since this query was last run at 2020-05-04T12:51:36Z, the following granules have been "
                "added or updated:</p>"
                "<ul><li><a href='https://cmr.link/g1'>https://cmr.link/g1</a></li><li>"
                "<a href='https://cmr.link/g2'>https://cmr.link/g2</a></li><li>"
                "<a href='https://cmr.link/g3'>https://cmr.link/g3</a></li></ul>"
                "<p>To unsubscribe from these notifications, or if you have any questions, "
                "please contact us at <a href='mailto:cmr-support@earthdata.nasa.gov'>"
                "cmr-support@earthdata.nasa.gov</a>.</p>")
           (:content (first (:body actual)))))))

(deftest subscription->time-constraint-test
  "Test the subscription->time-constraint function as it is critical for internal
   use. Test for when there is and is not a last-notified-at date and when there
   is no date, test that the code will look back with a specified number of seconds"
  (let [now (t/now)
        one-hour-back (t/minus now (t/seconds 3600))
        two-hours-back (t/minus now (t/seconds 7200))]
    (testing
     "Usecase one: no last-notified-at date, so start an hour ago and end now"
      (let [data {:extra-fields {}}
            expected (str one-hour-back "," now)
            actual (#'jobs/subscription->time-constraint data now 3600)]
        (is (= expected actual))))
    (testing
     "Usecase one: no last-notified-at date, it's explicitly set to nil, so
     start an hour ago and end now"
      (let [data {:extra-fields {:last-notified-at nil}}
            expected (str one-hour-back "," now)
            actual (#'jobs/subscription->time-constraint data now 3600)]
        (is (= expected actual))))
    (testing
     "Usecase one: no last-notified-at date, it's explicitly set to nil, so start
      two hours ago and end now ; test that look back is not hardcoded"
      (let [data {:extra-fields {:last-notified-at nil}}
            expected (str two-hours-back "," now)
            actual (#'jobs/subscription->time-constraint data now 7200)]
        (is (= expected actual))))

    (testing
     "Use case two: last-notified-at date is specified, start then and end now"
      (let [start "2012-01-10T08:00:00.000Z"
            data {:extra-fields {:last-notified-at start}}
            expected (str start "," now)
            actual (#'jobs/subscription->time-constraint data now 3600)]
        (is (= expected actual))))
    (testing
     "Use case two: last-notified-at date is specified, start then and end now,
                                      look back seconds is ignored and can even be crazy"
      (let [start "2012-01-10T08:00:00.000Z"
            data {:extra-fields {:last-notified-at start}}
            expected (str start "," now)
            actual (#'jobs/subscription->time-constraint data now -1234)]
        (is (= expected actual))))))
