(ns cmr.common.test.doi
  "Unit test DOI functions."
  (:require
   [clojure.test :refer :all]
   [cmr.common.doi :as doi]))

(def cmr-base-url "http://cmr.test.host/")

(def coll-data-1
  {:concept-id "C1200000003-PROV1"
   :short-name "s3"
   :entry-title "coll3"
   :version-id "6"})

(def coll-data-2
  {:concept-id "C1200000003-PROV1"
   :short-name "s3"
   :entry-title "coll3"
   :version-id "7"
   :doi "doi6"})

(def coll-data-3
  {:concept-id "C1200000003-PROV1"
   :short-name "s3"
   :entry-title "coll3"
   :version-id "8"
   :doi "http://dx.doi.org/doi7"})

(deftest doi->url-test
  (testing "DOI is a URL already"
    (is (= "http://dx.doi.org/doi7" (doi/doi->url (:doi coll-data-3)))))
  (testing "DOI is not a URL"
    (is (= "https://doi.org/doi6" (doi/doi->url (:doi coll-data-2))))))

(deftest get-cmr-landing-page-test
  (is (= "http://cmr.test.host/concepts/C1200000003-PROV1.html"
         (doi/get-cmr-landing-page cmr-base-url "C1200000003-PROV1"))))

(deftest get-landing-page-test
  (testing "with no DOI data (cmr-only)"
    (is (= "http://cmr.test.host/concepts/C1200000003-PROV1.html"
           (doi/get-landing-page cmr-base-url coll-data-1))))
  (testing "with DOI data and NO URL in the DOI"
    (is (= "https://doi.org/doi6"
           (doi/get-landing-page cmr-base-url coll-data-2))))
  (testing "with DOI data and URL in the DOI"
    (is (= "http://dx.doi.org/doi7"
           (doi/get-landing-page cmr-base-url coll-data-3)))))
