(ns cmr.system-int-test.search.sitemaps
  (:require [clj-http.client :as client]
            [clojure.test :refer :all]
            [cmr.search.site.data :as data]
            [cmr.transmit.config :as config]))

(deftest sitemap-master
  (let [url (str base-url "/sitemap.xml")
        response (client/get url)
        body (:body response)]
    (testing "presence and content of master sitemap.xml index file"
      (is (= (:status response) 200))
      (is (substring? "<sitemapindex" body))
      (is (substring? "<sitemap" body))
      (is (substring? "<loc>" body))
      (is (substring? "<lastmod>" body))
      (is (substring? "/site/sitemap.xml</loc>" body))
      (is (substring? "/collections/directory/PROV1/gov.nasa.eosdis/sitemap.xml</loc>" body))
      (is (substring? "/collections/directory/PROV2/gov.nasa.eosdis/sitemap.xml</loc>" body))
      (is (substring? "/collections/directory/PROV3/gov.nasa.eosdis/sitemap.xml</loc>" body)))))

(deftest sitemap-top-level
  (let [url (str base-url "/site/sitemap.xml")
        response (client/get url)
        body (:body response)]
    (testing "presence and content of sitemap.xml file"
      (is (= (:status response) 200))
      (is (substring? "<urlset" body))
      (is (substring? "<url" body))
      (is (substring? "<loc>" body))
      (is (substring? "<lastmod>" body))
      (is (substring? "/docs/api</loc>" body))
      (is (substring? "/collections/directory</loc>" body))
      (is (substring? "/collections/directory/eosdis</loc>" body))
      (is (substring? "/collections/directory/PROV1/gov.nasa.eosdis</loc>" body))
      (is (substring? "/collections/directory/PROV2/gov.nasa.eosdis</loc>" body))
      (is (substring? "/collections/directory/PROV3/gov.nasa.eosdis</loc>" body)))))

(deftest sitemap-provider1
  (testing "check the sitemap for PROV1"
    (let [provider "PROV1"
          tag "gov.nasa.eosdis"
          url (format
               "%s/site/collections/directory/%s/%s/sitemap.xml"
               base-url provider tag)
          response (client/get url)
          body (:body response)]
      (is (= 200 (:status response)))
      (is (substring? "<urlset" body))
      (is (substring? "<url" body))
      (is (substring? "<loc>" body))
      (is (substring? "<lastmod>" body))
      (is (substring? "concepts/C1200000002-PROV1.html</loc>" body))
      (is (substring? "concepts/C1200000003-PROV1.html</loc>" body))
      (is (not (substring? "C1200000001-PROV1.html</loc>" body))))))

(deftest sitemap-provider2
  (testing "check the sitemap for PROV2"
    (let [provider "PROV2"
          tag "gov.nasa.eosdis"
          url (format
               "%s/site/collections/directory/%s/%s/sitemap.xml"
               base-url provider tag)
          response (client/get url)
          body (:body response)]
      (is (= 200 (:status response)))
      (is (substring? "<urlset" body))
      (is (substring? "<url" body))
      (is (substring? "<loc>" body))
      (is (substring? "<lastmod>" body))
      (is (substring? "concepts/C1200000005-PROV2.html</loc>" body))
      (is (substring? "concepts/C1200000006-PROV2.html</loc>" body))
      (is (not (substring? "C1200000001-PROV1.html</loc>" body))))))

(deftest sitemap-provider3
  (testing "check the sitemap for PROV3"
    (let [provider "PROV3"
          tag "gov.nasa.eosdis"
          url (format
               "%s/site/collections/directory/%s/%s/sitemap.xml"
               base-url provider tag)
          response (client/get url)
          body (:body response)]
      (is (= 200 (:status response)))
      (is (substring? "<urlset" body))
      (is (substring? "<url" body))
      (is (substring? "<loc>" body))
      (is (substring? "<lastmod>" body))
      (is (substring? "http://dx.doi.org/doi5</loc>" body))
      (is (substring? "http://dx.doi.org/doi5</loc>" body))
      (is (not (substring? "C1200000001-PROV1.html</loc>" body))))))
