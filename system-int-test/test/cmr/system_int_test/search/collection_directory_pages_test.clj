(ns cmr.system-int-test.search.collection-directory-pages-test
  "This tests a running CMR site's directory links at all levels: top-most,
  eosdis, and provider."
  (:require [clj-http.client :as client]
            [clojure.test :refer :all]
            [clojure.string :as string]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.search.site.routes :as r]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.tag-util :as tags]
            [cmr.transmit.config :as transmit-config]
            [cmr.umm-spec.models.umm-common-models :as cm]
            [cmr.umm-spec.test.expected-conversion :as exp-conv]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Constants and general utility functions for the tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private base-url
  "We don't call to `(transmit-config/application-public-root-url)`
   due to the fact that it requires a context and we're not creating
   contexts for these integration tests, we're simply using an HTTP
   client."
   (format "%s://%s:%s/"
           (transmit-config/search-protocol)
           (transmit-config/search-host)
           (transmit-config/search-port)))

(defn- get-response
  [url-path]
  (->> url-path
       (str base-url)
       (client/get)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Exepcted results check functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn expected-header-link?
  [body]
  (string/includes? body (str base-url "site/collections/directory")))

(defn expected-top-level-links?
  [body]
  (string/includes? body (str base-url "site/collections/directory/eosdis")))

(defn expected-eosdis-level-links?
  [body]
  (let [url (str base-url "site/collections/directory")
        tag "gov.nasa.eosdis"]
    (and
      (string/includes? body (format "%s/%s/%s" url "PROV1" tag))
      (string/includes? body (format "%s/%s/%s" url "PROV2" tag))
      (string/includes? body (format "%s/%s/%s" url "PROV3" tag))
      (not (string/includes? body (format "%s/%s/%s" url "PROV4" tag)))
      (string/includes? body "PROV4"))))

(defn expected-provider1-level-links?
  [body]
  (let [url (format "%sconcepts" base-url)]
    (and
      (string/includes? body (format "%s/%s" url "C1200000015-PROV1.html"))
      (string/includes? body "Collection Item 2 (s2)")
      (string/includes? body (format "%s/%s" url "C1200000016-PROV1.html"))
      (string/includes? body "Collection Item 3 (s3)"))))

(defn expected-provider2-level-links?
  [body]
  (let [url (format "%sconcepts" base-url)]
    (and
      (string/includes? body (format "%s/%s" url "C1200000018-PROV2.html"))
      (string/includes? body "Collection Item 2 (s2)")
      (string/includes? body (format "%s/%s" url "C1200000019-PROV2.html"))
      (string/includes? body "Collection Item 3 (s3)"))))

(defn expected-provider3-level-links?
  [body]
  (let [url "http://dx.doi.org"]
    (and
      (string/includes? body (format "%s/%s" url "doi5"))
      (string/includes? body "Collection Item 5 (s5)")
      (string/includes? body (format "%s/%s" url "doi6"))
      (string/includes? body "Collection Item 6 (s6)"))))

(defn expected-provider1-col1-link?
  [body]
  (let [url (format "%sconcepts" base-url)]
    (and
      (string/includes? body (format "%s/%s" url "C1200000014-PROV1.html"))
      (string/includes? body "Collection Item 1 (s1)"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Functions for creating testing data
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def collections-fixture
  (fn [f]
    (setup-collections)
    (f)))

;; Note tha the fixtures are created out of order such that sorting can be
;; checked.
(use-fixtures :once (join-fixtures
                      [(ingest/reset-fixture {"provguid4" "PROV4"
                                              "provguid3" "PROV3"
                                              "provguid1" "PROV1"
                                              "provguid2" "PROV2"})
                       tags/grant-all-tag-fixture
                       collections-fixture]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest header-link
  (testing "check the link for landing pages in the header"
    (let [response (get-response "")]
      (is (= 200 (:status response)))
      (is (expected-header-link? (:body response))))))

(deftest top-level-links
  (let [response (get-response "site/collections/directory")
        body (:body response)]
    (testing "check top level status and links"
      (is (= 200 (:status response)))
      (is (expected-top-level-links? body)))
    (testing "top level directory page should have header links"
      (is (expected-header-link? body)))))

(deftest eosdis-level-links
  (let [response (get-response "site/collections/directory/eosdis")
        body (:body response)]
    (testing "check eosdis level status and links"
      (is (= 200 (:status response)))
      (is (expected-eosdis-level-links? body)))
    (testing "providers should be sorted by name"
      (is (and
           (< (string/index-of body "PROV1")
              (string/index-of body "PROV2"))
           (< (string/index-of body "PROV2")
              (string/index-of body "PROV3")))))
    (testing "eosdis-level directory page should have header links"
      (is (expected-header-link? body)))))

(deftest provider1-level-links
  (let [provider "PROV1"
        tag "gov.nasa.eosdis"
        url-path (format
                  "site/collections/directory/%s/%s"
                  provider tag)
        response (get-response url-path)
        body (:body response)]
    (testing "check the status and links for PROV3"
      (is (= 200 (:status response)))
      (is (expected-provider1-level-links? body)))
    (testing "the collections not tagged with eosdis shouldn't show up"
      (is (not (expected-provider1-col1-link? body))))
    (testing "provider page should have header links"
      (is (expected-header-link? body)))))

(deftest provider2-level-links
  (let [provider "PROV2"
        tag "gov.nasa.eosdis"
        url-path (format
                  "site/collections/directory/%s/%s"
                  provider tag)
        response (get-response url-path)
        body (:body response)]
    (testing "check the status and links for PROV3"
      (is (= 200 (:status response)))
      (is (expected-provider2-level-links? body)))
    (testing "the collections not tagged with eosdis shouldn't show up"
      (is (not (expected-provider1-col1-link? body))))
    (testing "provider page should have header links"
      (is (expected-header-link? body)))))

(deftest provider3-level-links
  (let [provider "PROV3"
        tag "gov.nasa.eosdis"
        url-path (format
                  "site/collections/directory/%s/%s"
                  provider tag)
        response (get-response url-path)
        body (:body response)]
    (testing "check the status and links for PROV3"
      (is (= 200 (:status response)))
      (is (expected-provider3-level-links? body)))
    (testing "the collections not tagged with eosdis shouldn't show up"
      (is (not (expected-provider1-col1-link? body))))
    (testing "provider page should have header links"
      (is (expected-header-link? body)))))

;; Note that the following test was originally in the unit tests for
;; cmr-search (thus its similarity to those tests) but had to be moved into
;; the integration tests due to the introduction of `base-url` support in the
;; templates (which the following text exercises). The base URL is obtained
;; (ulimately) by calling c.t.config/application-public-root-url which needs
;; `public-conf` data set in both the route-creation as well as the request.
;; It was thus just easier and more natural to perform the required checks as
;; part the integration tests, since the running system already has that data
;; set up.
(deftest eosdis-collections-directory-page
  (testing "eosdis collections collections directory page returns content"
    (let [response (get-response "site/collections/directory/eosdis")]
      (is (= (:status response) 200))
      (is (string/includes?
           (:body response)
           "Directory of Collections Landing Pages"))
      (is (string/includes?
           (:body response)
           "EOSDIS")))))
