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
     "EntryTitle" "coll3"
     "Version" "6"}})

(def coll-data-2
  {:meta
    {:provider-id "PROV1"
     :concept-id "C1200000003-PROV1"}
   :umm
    {"ShortName" "s3"
     "EntryTitle" "coll3"
     "Version" "7"
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

(deftest make-holding-data
  (testing "with an entry title and short name"
    (let [data (data/make-holding-data cmr-base-url coll-data-1)]
      (is (= "http://cmr.test.host/concepts/C1200000003-PROV1.html"
             (:link-href data)))
      (is (= "coll3" (get-in data [:umm "EntryTitle"])))
      (is (= "s3" (get-in data [:umm "ShortName"])))
      (is (= "6" (get-in data [:umm "Version"])))))
  (testing "with an entry title, short name, and doi"
    (let [data (data/make-holding-data cmr-base-url coll-data-2)]
      (is (= "http://dx.doi.org/doi6" (:link-href data)))
      (is (= "s3" (get-in data [:umm "ShortName"])))
      (is (= "7" (get-in data [:umm "Version"]))))))

(deftest make-holdings-data
  (let [data (->> [coll-data-1 coll-data-2]
                  (data/make-holdings-data cmr-base-url)
                  (vec))]
    (is (= "http://cmr.test.host/concepts/C1200000003-PROV1.html"
           (get-in data [0 :link-href])))
    (is (= "s3" (get-in data [0 :umm "ShortName"])))
    (is (= "6" (get-in data [0 :umm "Version"])))
    (is (= "http://dx.doi.org/doi6" (get-in data [1 :link-href])))
    (is (= "s3" (get-in data [1 :umm "ShortName"])))
    (is (= "7" (get-in data [1 :umm "Version"])))))
