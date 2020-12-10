(ns cmr.ingest.test.services.jobs
  "This tests some of the more complicated functions of cmr.ingest.services.jobs"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as u :refer [are3]]
    [cmr.ingest.services.jobs :as jobs]))

(deftest create-query-params
  (is (= {"polygon" "-78,-18,-77,-22,-73,-16,-74,-13,-78,-18"
          "concept-id" "G123-PROV1"}
         (#'jobs/create-query-params "polygon=-78,-18,-77,-22,-73,-16,-74,-13,-78,-18&concept-id=G123-PROV1"))))

(deftest email-granule-url-list-test
 "This tests the utility function that unpacts a list of urls and turns it into markdown"
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

(deftest create-permission-notification-email-test
  "This tests the HTML output of permission notification email generation"
  (let [actual (jobs/failed-permission-content
                "cmr-support@earthdata.nasa.gov"
                "someone@gmail.com"
                "C1200370131-EDF_DEV06")]
   (is (= "someone@gmail.com" (:to actual)))
   (is (= "You can no longer view this collection" (:subject actual)))
   (is (= "text/html" (:type (first (:body actual)))))
   (is (= (str "<p>You are currently subscribed to receive updates for new data added to C1200370131-EDF_DEV06.</p>"
               "<p>Because you no longer have access to view this collection, you will not receive notifications "
               "related to this collection.</p>"
               "<p>If you believe this subscription was deleted in error, please contact us at <a href='mailto:cmr-support@earthdata.nasa.gov'>"
               "cmr-support@earthdata.nasa.gov</a>.</p>")
          (:content (first (:body actual)))))))
