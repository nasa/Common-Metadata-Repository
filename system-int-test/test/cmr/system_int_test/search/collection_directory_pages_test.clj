(ns cmr.system-int-test.search.collection-directory-pages-test
  "This tests a running CMR site's directory links at all levels: top-most,
  eosdis, and provider."
  (:require [clj-http.client :as client]
            [clojure.test :refer :all]
            [clojure.string :as string]
            [cmr.search.site.routes :as r]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.tag-util :as tags]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]
            [cmr.transmit.config :as transmit-config]
            [cmr.umm-spec.models.umm-common-models :as cm]
            [cmr.umm-spec.test.expected-conversion :as exp-conv]))

(def ^{:doc "We don't call to (transmit-config/application-public-root-url)
             due to the fact that it requires a context and we're not creating
             contexts for these integration tests, we're simply using an HTTP
             client."
       :private true}
  base-url (format "%s://%s:%s/"
                   (transmit-config/search-protocol)
                   (transmit-config/search-host)
                   (transmit-config/search-port)))

;;; General utility functions for the tests

(defn- make-link
  [{href :href text :text}]
  (format "<li><a href=\"%s\">%s</a></li>" href text))

(defn- make-links
  [data]
  (string/join
    "\n  \n    "
    (map make-link data)))

;;; Create expected data for the tests

(def expected-header-link
  (make-link {:href (str base-url "site/collections/directory")
              :text "Directory"}))

(def expected-top-level-links
  (make-links [{:href (str base-url "site/collections/directory/eosdis")
                :text "Directory for EOSDIS Collections"}]))

(def expected-eosdis-level-links
  (let [url (str base-url "site/collections/directory")
        tag "gov.nasa.eosdis"]
    (make-links [{:href (format "%s/%s/%s" url "PROV1" tag)
                  :text "PROV1"}
                 {:href (format "%s/%s/%s" url "PROV2" tag)
                  :text "PROV2"}
                 {:href (format "%s/%s/%s" url "PROV3" tag)
                  :text "PROV3"}])))

(def expected-provider1-level-links
  (let [url (format "%sconcepts" base-url)]
    (make-links [{:href (format "%s/%s" url "C1200000002-PROV1.html")
                  :text "Collection Item 2 (s2)"}
                 {:href (format "%s/%s" url "C1200000003-PROV1.html")
                  :text "Collection Item 3 (s3)"}])))

(def expected-provider2-level-links
  (let [url (format "%sconcepts" base-url)]
    (make-links [{:href (format "%s/%s" url "C1200000005-PROV2.html")
                  :text "Collection Item 2 (s2)"}
                 {:href (format "%s/%s" url "C1200000006-PROV2.html")
                  :text "Collection Item 3 (s3)"}])))

(def expected-provider3-level-links
  (let [url "http://dx.doi.org"]
    (make-links [{:href (format "%s/%s" url "doi5")
                  :text "Collection Item 5 (s5)"}
                 {:href (format "%s/%s" url "doi6")
                  :text "Collection Item 6 (s6)"}])))

(def notexpected-provider-level-link
  (let [url (format "%sconcepts" base-url)]
    (make-links [{:href (format "%s/%s" url "C1200000001-PROV1.html")
                  :text "Collection Item 1 (s1)"}])))

;;; Functions for creating testing data

(defn- setup-collections
  "A utility function that generates testing collections data with the bits we
  need to test."
  []
  (let [[c1-p1 c2-p1 c3-p1
         c1-p2 c2-p2 c3-p2] (for [p ["PROV1" "PROV2"]
                                  n (range 1 4)]
                              (d/ingest-umm-spec-collection
                                p
                                (-> exp-conv/example-collection-record
                                    (assoc :ShortName (str "s" n))
                                    (assoc :EntryTitle (str "Collection Item " n)))
                                {:format :umm-json
                                 :accept-format :json}))
         [c1-p3 c2-p3 c3-p3] (for [n (range 4 7)]
                               (d/ingest-umm-spec-collection
                                 "PROV3"
                                 (-> exp-conv/example-collection-record
                                     (assoc :ShortName (str "s" n))
                                     (assoc :EntryTitle (str "Collection Item " n))
                                     (assoc :DOI (cm/map->DoiType
                                                   {:DOI (str "doi" n)
                                                    :Authority (str "auth" n)})))
                                 {:format :umm-json
                                  :accept-format :json}))]
    ;; Wait until collections are indexed so tags can be associated with them
    (index/wait-until-indexed)
    ;; Use the following to generate html links that will be matched in tests
    (let [user-token (e/login (s/context) "user")
          notag-colls [c1-p1 c1-p2 c1-p3]
          nodoi-colls [c1-p1 c2-p1 c3-p1 c1-p2 c2-p2 c3-p2]
          doi-colls [c1-p3 c2-p3 c3-p3]
          all-colls (into nodoi-colls doi-colls)
          tag-colls [c2-p1 c2-p2 c2-p3 c3-p1 c3-p2 c3-p3]
          tag (tags/save-tag
                user-token
                (tags/make-tag {:tag-key "gov.nasa.eosdis"})
                tag-colls)]
    (index/wait-until-indexed)
    ;; Sanity checks
    (assert (= (count notag-colls) 3))
    (assert (= (count nodoi-colls) 6))
    (assert (= (count doi-colls) 3))
    (assert (= (count tag-colls) 6))
    (assert (= (count all-colls) 9)))))

;;; Fixtures

(def collections-fixture
  (fn [f]
    (setup-collections)
    (f)))

(use-fixtures :once (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"
                                              "provguid2" "PROV2"
                                              "provguid3" "PROV3"})
                       tags/grant-all-tag-fixture
                       collections-fixture]))

;;; Tests

(deftest header-link
  (testing "check the link for landing pages in the header"
    (let [response (client/get base-url)]
      (is (= 200 (:status response)))
      (is (string/includes? (:body response) expected-header-link)))))

(deftest top-level-links
  (testing "check top level links"
    (let [url (str base-url "site/collections/directory")
          response (client/get url)
          body (:body response)]
      (is (= 200 (:status response)))
      ;; The collections not tagged with eosdis shouldn't show up
      (is (string/includes? body expected-top-level-links))
      ;; This page should also have a header link
      (is (string/includes? body expected-header-link)))))

(deftest eosdis-level-links
  (testing "check eosdis level links"
    (let [url (str base-url "site/collections/directory/eosdis")
          response (client/get url)
          body (:body response)]
      (is (= 200 (:status response)))
      ;; The collections not tagged with eosdis shouldn't show up
      (is (string/includes? body expected-eosdis-level-links))
      ;; This page should also have a header link
      (is (string/includes? body expected-header-link)))))

(deftest provider1-level-links
  (testing "check the links for PROV1"
    (let [provider "PROV1"
          tag "gov.nasa.eosdis"
          url (format
               "%ssite/collections/directory/%s/%s"
               base-url provider tag)
          response (client/get url)
          body (:body response)]
      (is (= 200 (:status response)))
      (is (string/includes? body expected-provider1-level-links))
      ;; The collections not tagged with eosdis shouldn't show up
      (is (not (string/includes? body notexpected-provider-level-link)))
      ;; This page should also have a header link
      (is (string/includes? body expected-header-link)))))

(deftest provider2-level-links
  (testing "check the links for PROV2"
    (let [provider "PROV2"
          tag "gov.nasa.eosdis"
          url (format
               "%ssite/collections/directory/%s/%s"
               base-url provider tag)
          response (client/get url)
          body (:body response)]
      (is (= 200 (:status response)))
      (is (string/includes? body expected-provider2-level-links))
      ;; The collections not tagged with eosdis shouldn't show up
      (is (not (string/includes? body notexpected-provider-level-link)))
      ;; This page should also have a header link
      (is (string/includes? body expected-header-link)))))

(deftest provider3-level-links
  (testing "check the links for PROV3"
    (let [provider "PROV3"
          tag "gov.nasa.eosdis"
          url (format
               "%ssite/collections/directory/%s/%s"
               base-url provider tag)
          response (client/get url)
          body (:body response)]
      (is (= 200 (:status response)))
      (is (string/includes? body expected-provider3-level-links))
      ;; The collections not tagged with eosdis shouldn't show up
      (is (not (string/includes? body notexpected-provider-level-link)))
      ;; This page should also have a header link
      (is (string/includes? body expected-header-link)))))

;; Note that the following test was originally in the unit tests for this code
;; (thus the similarity of it to those tests) but had to be moved to an
;; integration test with the introduction of `base-url` support in the
;; templates (which the following text exercises). The base URL is obtained
;; (ultimately) by calling c.t.config/application-public-root-url which needs
;; `public-conf` data set in both the route-creation as well as the request. It
;; was thus just easier and more natural to perform the required test as part
;; of the integration tests, since the running system already has that data set
;; up.
(deftest eosdis-collections-directory-page
  (testing "eosdis collections collections directory page returns content"
    (let [url (str base-url "site/collections/directory/eosdis")
          response (client/get url)]
      (is (= (:status response) 200))
      (is (string/includes?
           (:body response)
           "Directory of Landing Pages for EOSDIS Collections")))))

(deftest sitemap-top-level
  (let [url (str base-url "/sitemap.xml")
        response (client/get url)
        body (:body response)]
    (testing "presence and content of sitemap.xml file"
      (is (= (:status response) 200))
      (is (string/includes? body "<urlset"))
      (is (string/includes? body "<url"))
      (is (string/includes? body "<loc>"))
      (is (string/includes? body "<lastmod>"))
      (is (string/includes? body "/docs/api</loc>"))
      (is (string/includes? body "/collections/directory</loc>"))
      (is (string/includes? body "/collections/directory/eosdis</loc>"))
      (is (string/includes? body "/collections/directory/PROV1/gov.nasa.eosdis</loc>"))
      (is (string/includes? body "/collections/directory/PROV2/gov.nasa.eosdis</loc>"))
      (is (string/includes? body "/collections/directory/PROV3/gov.nasa.eosdis</loc>")))))
