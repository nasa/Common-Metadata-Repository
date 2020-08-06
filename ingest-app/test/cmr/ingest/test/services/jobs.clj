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

(deftest create-email-test
 "This tests the HTML output of the email generation"
 (def actual (jobs/create-email-content
  "cmr-support@earthdata.nasa.gov"
  "someone@gmail.com"
  '("https://cmr.link/g1" "https://cmr.link/g2" "https://cmr.link/g3")
  {:metadata {:SubscriberId "tcherry"}}))
 ;(println actual)
 (def expected {:from "cmr-support@earthdata.nasa.gov"
  :to "someone@gmail.com"
  :subject "Email Subscription Notification"
  :body [{:type "text/html"
   :content "<p>You have subscribed to receive notifications when the following query is updated:</p><p>Since this query was last run at, the following granules have been added or updated:</p><ul><li><a href='https://cmr.link/g1'>https://cmr.link/g1</a></li><li><a href='https://cmr.link/g2'>https://cmr.link/g2</a></li><li><a href='https://cmr.link/g3'>https://cmr.link/g3</a></li></ul><p>To unsubscribe from these notifications, or if you have any questions, please contact us at <a href='mailto:cmr-support@earthdata.nasa.gov'>cmr-support@earthdata.nasa.gov</a>.</p>"}]})
 (are [exp act] (= exp act)
  (:to expected) (:to actual)
  (:subject expected) (:subject actual)
  (:type (first (:body expected))) (:type (first (:body actual)))
  (:content (first (:body expected))) (:content (first (:body actual)))
  ))
