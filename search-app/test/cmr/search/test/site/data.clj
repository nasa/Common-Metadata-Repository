(ns cmr.search.test.site.data
  (:require [clojure.test :refer :all]
            [cmr.search.site.data :as data]
            [cmr.transmit.config :as config]))

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
   :doi-stored "doi6"})

(deftest cmr-link
  (testing "generate a cmr landing page from a host and a concept id"
    (let [cmr-host "cmr.sit.earthdata.nasa.gov"
          concept-id "C1200196931-SCIOPS"]
      (is (= "https://cmr.sit.earthdata.nasa.gov/concepts/C1200196931-SCIOPS.html"
             (data/cmr-link cmr-host concept-id))))))

(deftest has-doi?
  (testing "check for doi in data that doesn't have one"
    (is (not (data/has-doi? coll-data-1))))
  (testing "check for doi in data that has one"
    (is (data/has-doi? coll-data-2))))

(deftest cmr-link
  (is (= "http://cmr.test.host/concepts/C1200000003-PROV1.html"
         (data/cmr-link cmr-base-url "C1200000003-PROV1"))))

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
      (is (= "coll3" (:entry-title data)))
      (is (= "s3" (:short-name data)))
      (is (= "6" (:version-id data)))))
  (testing "with an entry title, short name, and doi"
    (let [data (data/make-holding-data cmr-base-url coll-data-2)]
      (is (= "http://dx.doi.org/doi6" (:link-href data)))
      (is (= "s3" (:short-name data)))
      (is (= "7" (:version-id data))))))

(deftest make-holdings-data
  (let [data (->> [coll-data-1 coll-data-2]
                  (data/make-holdings-data cmr-base-url)
                  (vec))]
    (is (= "http://cmr.test.host/concepts/C1200000003-PROV1.html"
           (get-in data [0 :link-href])))
    (is (= "s3" (get-in data [0 :short-name])))
    (is (= "6" (get-in data [0 :version-id])))
    (is (= "http://dx.doi.org/doi6" (get-in data [1 :link-href])))
    (is (= "s3" (get-in data [1 :short-name])))
    (is (= "7" (get-in data [1 :version-id])))))
