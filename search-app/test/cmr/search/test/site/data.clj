(ns cmr.search.test.site.data
  (:require [clojure.test :refer :all]
            [cmr.search.site.data :as data]
            [cmr.transmit.config :as config]))

(def cmr-base-url "http://cmr.test.host/")

(def coll-data-1
  {:meta
    {:provider-id "PROV1"
     :concept-id "C1200000003-PROV1"}
   :umm
    {"ShortName" "s3"
     "EntryTitle" "coll3"}})

(def coll-data-2
  {:meta
    {:provider-id "PROV1"
     :concept-id "C1200000003-PROV1"}
   :umm
    {"ShortName" "s3"
     "EntryTitle" "coll3"
     "DOI"
     {"Authority" "auth6"
      "DOI" "doi6"}}})

(deftest doi-link
  (testing "generate a link from doi data"
    (let [data {"DOI" "doi:10.7265/N5R78C49"}]
      (is (= "http://dx.doi.org/doi:10.7265/N5R78C49"
             (data/doi-link data))))))

(deftest cmr-link
  (testing "generate a cmr landing page from a host and a concept id"
    (let [cmr-host "cmr.sit.earthdata.nasa.gov"
          concept-id "C1200196931-SCIOPS"]
      (is (= "https://cmr.sit.earthdata.nasa.gov/concepts/C1200196931-SCIOPS.html"
             (data/cmr-link cmr-host concept-id))))))

(deftest get-doi
  (testing "try to get the doi entry from data that doesn't have one"
    (is (not (data/get-doi coll-data-1))))
  (testing "get the doi entry"
    (is (= {"DOI" "doi6" "Authority" "auth6"}
           (data/get-doi coll-data-2)))))

(deftest has-doi?
  (testing "check for doi in data that doesn't have one"
    (is (not (data/has-doi? coll-data-1))))
  (testing "check for doi in data that has one"
    (is (data/has-doi? coll-data-2))))

(deftest cmr-link
  (is (= "http://cmr.test.host/concepts/C1200000003-PROV1.html"
         (data/cmr-link cmr-base-url "C1200000003-PROV1"))))

(deftest doi-link
  (is (= "http://dx.doi.org/doi6"
         (data/doi-link {"DOI" "doi6" "Authority" "auth6"}))))

(deftest make-href
  (testing "with no doi data (cmr-only)"
    (is (= "http://cmr.test.host/concepts/C1200000003-PROV1.html"
           (data/make-href cmr-base-url coll-data-1))))
  (testing "with doi data"
    (is (= "http://dx.doi.org/doi6"
           (data/make-href cmr-base-url coll-data-2)))))

(deftest get-entry-title
  (testing "with an entry title"
    (is (= "coll3"
           (data/get-entry-title coll-data-1)))))

(deftest get-short-name
  (testing "short name"
    (is (= "s3"
           (data/get-short-name coll-data-1)))))

(deftest make-text
  (testing "with an entry title and short name"
    (is (= "coll3 (s3)"
           (data/make-text coll-data-1)))))

(deftest make-link
  (testing "with an entry title and short name"
    (is (= {:href "http://cmr.test.host/concepts/C1200000003-PROV1.html"
            :text "coll3 (s3)"}
           (data/make-link cmr-base-url coll-data-1))))
  (testing "with an entry title, short name, and doi"
    (is (= {:href "http://dx.doi.org/doi6"
            :text "coll3 (s3)"}
           (data/make-link cmr-base-url coll-data-2)))))

(deftest make-links
  (let [coll [coll-data-1 coll-data-2]]
    (is (= [{:href "http://cmr.test.host/concepts/C1200000003-PROV1.html"
             :text "coll3 (s3)"}
            {:href "http://dx.doi.org/doi6", :text "coll3 (s3)"}]
           (data/make-links cmr-base-url coll)))))
