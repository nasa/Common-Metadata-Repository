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

(def coll-data-3
  {:meta
    {:provider-id "PROV1"
     :concept-id "C1200000003-PROV1"}
   :umm
    {"ShortName" "s3"}})

(def coll-data-4
  {:meta
    {:provider-id "PROV1"
     :concept-id "C1200000003-PROV1"}
   :umm
    {}})

(def coll-data-5
  {:meta
    {:provider-id "PROV1"
     :concept-id "C1200000003-PROV1"}
   :umm
    {"EntryTitle" "coll3"}})

(deftest doi-link
  (testing "generate a link from doi data"
    (let [data {"DOI" "doi:10.7265/N5R78C49"}]
      (is (= (data/doi-link data)
             "http://dx.doi.org/doi:10.7265/N5R78C49")))))

(deftest cmr-link
  (testing "generate a cmr landing page from a host and a concept id"
    (let [cmr-host "cmr.sit.earthdata.nasa.gov"
          concept-id "C1200196931-SCIOPS"]
      (is (= (data/cmr-link cmr-host concept-id)
             "https://cmr.sit.earthdata.nasa.gov/concepts/C1200196931-SCIOPS.html")))))

(deftest get-doi
  (testing "try to get the doi entry from data that doesn't have one"
    (is (not (data/get-doi coll-data-1))))
  (testing "get the doi entry"
    (is (= (data/get-doi coll-data-2)
           {"DOI" "doi6" "Authority" "auth6"}))))

(deftest has-doi?
  (testing "check for doi in data that doesn't have one"
    (is (not (data/has-doi? coll-data-1))))
  (testing "check for doi in data that has one"
    (is (data/has-doi? coll-data-2))))

(deftest cmr-link
  (is (= (data/cmr-link cmr-base-url "C1200000003-PROV1")
         "http://cmr.test.host/concepts/C1200000003-PROV1.html")))

(deftest doi-link
  (is (= (data/doi-link {"DOI" "doi6" "Authority" "auth6"})
         "http://dx.doi.org/doi6")))

(deftest make-href
  (testing "with no doi data (cmr-only)"
    (is (= (data/make-href cmr-base-url coll-data-1)
           "http://cmr.test.host/concepts/C1200000003-PROV1.html")))
  (testing "with doi data"
    (is (= (data/make-href cmr-base-url coll-data-2)
           "http://dx.doi.org/doi6"))))

(deftest get-long-name
  (testing "with an entry title"
    (is (= (data/get-long-name coll-data-1)
           "coll3")))
  (testing "with no entry title"
    (is (= (data/get-long-name coll-data-3)
           "C1200000003-PROV1"))))

(deftest get-short-name
  (testing "with a short name"
    (is (= (data/get-short-name coll-data-1)
           " (s3)")))
  (testing "with no short name"
    (is (= (data/get-short-name coll-data-4)
           ""))))

(deftest make-text
  (testing "with an entry title and short name"
    (is (= (data/make-text coll-data-1)
           "coll3 (s3)")))
  (testing "with an entry title and no short name"
    (is (= (data/make-text coll-data-5)
           "coll3")))
  (testing "with no entry title and short name"
    (is (= (data/make-text coll-data-3)
           "C1200000003-PROV1 (s3)")))
  (testing "with no entry title and no short name"
    (is (= (data/make-text coll-data-4)
           "C1200000003-PROV1"))))

(deftest make-link
  (testing "with an entry title and short name"
    (is (= (data/make-link cmr-base-url coll-data-1)
           {:href "http://cmr.test.host/concepts/C1200000003-PROV1.html"
            :text "coll3 (s3)"})))
  (testing "with an entry title, short name, and doi"
    (is (= (data/make-link cmr-base-url coll-data-2)
           {:href "http://dx.doi.org/doi6"
            :text "coll3 (s3)"})))
  (testing "with an entry title and no short name"
    (is (= (data/make-link cmr-base-url coll-data-5)
           {:href "http://cmr.test.host/concepts/C1200000003-PROV1.html"
            :text "coll3"})))
  (testing "with no entry title and short name"
    (is (= (data/make-link cmr-base-url coll-data-3)
           {:href "http://cmr.test.host/concepts/C1200000003-PROV1.html"
            :text "C1200000003-PROV1 (s3)"})))
  (testing "with no entry title and no short name"
    (is (= (data/make-link cmr-base-url coll-data-4)
           {:href "http://cmr.test.host/concepts/C1200000003-PROV1.html"
            :text "C1200000003-PROV1"}))))

(deftest make-links
  (let [coll [coll-data-1 coll-data-2 coll-data-3 coll-data-4 coll-data-5]]
    (is (= (data/make-links cmr-base-url coll)
           [{:href "http://cmr.test.host/concepts/C1200000003-PROV1.html"
             :text "coll3 (s3)"}
            {:href "http://dx.doi.org/doi6", :text "coll3 (s3)"}
            {:href "http://cmr.test.host/concepts/C1200000003-PROV1.html"
             :text "C1200000003-PROV1 (s3)"}
            {:href "http://cmr.test.host/concepts/C1200000003-PROV1.html"
             :text "C1200000003-PROV1"}
            {:href "http://cmr.test.host/concepts/C1200000003-PROV1.html"
             :text "coll3"}]))))
