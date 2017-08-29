(ns cmr.system-int-test.search.collection-directory-pages-test
  "This tests a running CMR site's directory links at all levels: top-most,
  eosdis, and provider."
  (:require
   [clj-http.client :as client]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.search.site.static :as static]
   [cmr.search.site.data :as site-data]
   [cmr.search.site.routes :as r]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.utils.search-util :as search]
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

(def ^:private test-collections (atom {}))
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
  (string/includes? body "site/collections/directory"))

(defn expected-top-level-links?
  [body]
  (string/includes? body "site/collections/directory/eosdis"))

(defn expected-eosdis-level-links?
  [body]
  (let [url "site/collections/directory"
        tag "gov.nasa.eosdis"]
    (and
      (string/includes? body (format "%s/%s/%s" url "PROV1" tag))
      (string/includes? body (format "%s/%s/%s" url "PROV2" tag))
      (string/includes? body (format "%s/%s/%s" url "PROV3" tag))
      (not (string/includes? body "NOCOLLS"))
      (not (string/includes? body "NONEOSDIS")))))

(defn expected-provider1-level-links?
  [body]
  (string/includes? body "EOSDIS holdings for the PROV1 provider")
  (let [url "concepts"
        colls (@test-collections "PROV1")]
    (and
      (string/includes? body (format "%s/%s" url (nth colls 1)))
      (string/includes? body (format "%s/%s" url (nth colls 2)))
      (string/includes? body "Collection Item 2</a>")
      (string/includes? body "Collection Item 3</a>")
      (not (string/includes? body "Collection Item 1")))))

(defn expected-provider2-level-links?
  [body]
  (string/includes? body "EOSDIS holdings for the PROV2 provider")
  (let [url "concepts"
        colls (@test-collections "PROV2")]
    (and
      (string/includes? body (format "%s/%s" url (nth colls 1)))
      (string/includes? body (format "%s/%s" url (nth colls 2)))
      (string/includes? body "Collection Item 2</a>")
      (string/includes? body "Collection Item 3</a>")
      (string/includes? body "Collection Item 1001</a>")
      (string/includes? body "Collection Item 1016</a>")
      (not (string/includes? body "Collection Item 1</a>"))
      (not (string/includes? body "Collection Item 4</a>")))))

(defn expected-provider3-level-links?
  [body]
  (string/includes? body "EOSDIS holdings for the PROV3 provider")
  (let [url "http://dx.doi.org"]
    (and
      (string/includes? body (format "%s/%s" url "doi102"))
      (string/includes? body (format "%s/%s" url "doi103"))
      (string/includes? body "Collection Item 102</a>")
      (string/includes? body "Collection Item 103</a>")
      (not (string/includes? body "Collection Item 101</a>")))))

(defn expected-provider1-col1-link?
  [body]
  (let [url "concepts"
        colls (@test-collections "PROV1")]
    (and
      (string/includes? body (format "%s/%s" url (first colls)))
      (string/includes? body "Collection Item 1</a>"))))

(def expected-over-ten-count
  "<td class=\"align-r\">\n        18\n      </td>")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Functions for creating testing data
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- setup-collections
  "A utility function that generates testing collections data with the bits we
  need to test."
  []
  (let [[c1-p1 c2-p1 c3-p1
         c1-p2 c2-p2 c3-p2
         c1-p4 c2-p4 c3-p4] (doall (for [p ["PROV1" "PROV2" "NONEOSDIS"]
                                  n (range 1 4)]
                              (d/ingest-umm-spec-collection
                               p
                               (assoc exp-conv/example-collection-record
                                      :ShortName (str "s" n)
                                      :EntryTitle (str "Collection Item " n))
                               {:format :umm-json
                                :accept-format :json})))
         _ (index/wait-until-indexed)
         [c1-p3 c2-p3 c3-p3] (doall (for [n (range 101 104)]
                               (d/ingest-umm-spec-collection
                                "PROV3"
                                (assoc exp-conv/example-collection-record
                                       :ShortName (str "s" n)
                                       :EntryTitle (str "Collection Item " n)
                                       :DOI (cm/map->DoiType
                                             {:DOI (str "doi" n)
                                              :Authority (str "auth" n)}))
                                {:format :umm-json
                                 :accept-format :json})))
         _ (index/wait-until-indexed)
         ;; Let's create another collection that will put the total over the
         ;; default of 10 values so that we can ensure the :unlimited option
         ;; is being used in the directory page data.
         over-ten-colls (doall (for [n (range 1001 1017)]
                          (d/ingest-umm-spec-collection
                           "PROV2"
                           (assoc exp-conv/example-collection-record
                                  :ShortName (str "s" n)
                                  :EntryTitle (str "Collection Item " n))
                           {:format :umm-json
                            :accept-format :json})))]
    (reset! test-collections
            {"PROV1" (sort (map :concept-id [c1-p1 c2-p1 c3-p1]))
             "PROV2" (sort (map :concept-id (conj over-ten-colls c1-p2 c2-p2 c3-p2)))
             "PROV3" (sort (map :concept-id [c1-p3 c2-p3 c3-p3]))})
    ;; Wait until collections are indexed so tags can be associated with them
    (index/wait-until-indexed)
    ;; Use the following to generate html links that will be matched in tests
    (let [user-token (e/login (s/context) "user")
          notag-colls [c1-p1 c1-p2 c1-p3]
          nodoi-colls [c1-p1 c2-p1 c3-p1 c1-p2 c2-p2 c3-p2]
          doi-colls [c1-p3 c2-p3 c3-p3]
          non-eosdis-provider-colls [c1-p4 c2-p4 c3-p4]
          all-colls (flatten [over-ten-colls nodoi-colls doi-colls non-eosdis-provider-colls])
          tag-colls (conj over-ten-colls c2-p1 c2-p2 c2-p3 c3-p1 c3-p2 c3-p3)
          tag (tags/save-tag
                user-token
                (tags/make-tag {:tag-key "gov.nasa.eosdis"})
                tag-colls)]
      (index/wait-until-indexed)
      ;; Sanity checks
      (is (= 3 (count notag-colls)))
      (is (= 6 (count nodoi-colls)))
      (is (= 3 (count doi-colls)))
      (is (= 22 (count tag-colls)))
      (is (= 28 (count all-colls))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def collections-fixture
  (fn [f]
    (search/refresh-collection-metadata-cache)
    (setup-collections)
    (static/generate-site-resources)
    (f)))

;; Note tha the fixtures are created out of order such that sorting can be
;; checked.
(use-fixtures :once (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1"
                                             "provguid2" "PROV2"
                                             "provguid3" "PROV3"
                                             "provguid4" "NONEOSDIS"
                                             "provguid5" "NOCOLLS"})
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
  (Thread/sleep 1000)
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
      (is (expected-header-link? body)))
    (testing "provider page should have header links"
      (is (expected-header-link? body)))
    (testing "collection count is greater than the default 10-limit"
      (is (string/includes? body expected-over-ten-count)))))

(deftest provider1-level-links
  (let [provider "PROV1"
        tag "gov.nasa.eosdis"
        url-path (format
                  "site/collections/directory/%s/%s"
                  provider tag)
        response (get-response url-path)
        body (:body response)]
    (testing "check the status and links for PROV1"
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
    (testing "check the status and links for PROV2"
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
           "Provider Holdings Directory"))
      (is (string/includes?
           (:body response)
           "EOSDIS")))))
