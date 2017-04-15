(ns cmr.system-int-test.search.landing-pages-links-test
  "This tests searching by tags to generate links for landing pages"
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.common.util :as util :refer [are2]]
            [cmr.search.site.routes :as r]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.tag-util :as tags]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.atom :as da]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]
            [cmr.umm-spec.models.umm-common-models :as cm]
            [cmr.umm-spec.test.expected-conversion :as exp-conv]
            [ring.mock.request :refer :all]))

(def ^:private base-url "https://cmr.example.com/search")
(def ^:private site (#'cmr.search.site.routes/build-routes
                     {:public-conf {:protocol "https"
                                    :relative-root-url "/search"}}))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"
                                              "provguid2" "PROV2"
                                              "provguid3" "PROV3"})
                       tags/grant-all-tag-fixture]))

(defn- substring?
  [test-value string]
  (.contains string test-value))

(defn- make-link
  [{href :href text :text}]
  (format "<li><a href=\"%s\">%s</a></li>" href text))

(defn- make-links
  [data]
  (str/join
    "\n"
    (map make-link data)))

(def expected-header-link
  (make-link {:href "/site/collections/directory"
              :text "Landing Pages"}))

(def expected-top-level-links
  (make-links [{:href "/site/collections/directory/eosdis"
                :text "Directory for EOSDIS Collections"}]))

(def expected-eosdis-level-links
  (let [url-path "/site/collections/directory"
        tag "gov.nasa.eosdis"]
    (make-links [{:href (format "%s/%s/%s" url-path "PROV1" tag)
                  :text "PROV1"}
                 {:href (format "%s/%s/%s" url-path "PROV2" tag)
                  :text "PROV2"}
                 {:href (format "%s/%s/%s" url-path "PROV3" tag)
                  :text "PROV3"}])))

(def expected-provider1-level-links
  (let [url-prefix "http://localhost:3003/concepts"]
    (make-links [{:href (format "%s/%s" url-prefix "C1200000002-PROV1.html")
                  :text "coll2 (s2)"}
                 {:href (format "%s/%s" url-prefix "C1200000003-PROV1.html")
                  :text "coll3 (s3)"}])))

(def expected-provider2-level-links
  (let [url-prefix "http://localhost:3003/concepts"]
    (make-links [{:href (format "%s/%s" url-prefix "C1200000005-PROV2.html")
                  :text "coll2 (s2)"}
                 {:href (format "%s/%s" url-prefix "C1200000006-PROV2.html")
                  :text "coll3 (s3)"}])))

(def expected-provider3-level-links
  (let [url-prefix "http://dx.doi.org"]
    (make-links [{:href (format "%s/%s" url-prefix "doi5")
                  :text "coll5 (s5)"}
                 {:href (format "%s/%s" url-prefix "doi6")
                  :text "coll6 (s6)"}])))

(def notexpected-provider-level-link
  (let [url-prefix "http://localhost:3003/concepts"]
    (make-links [{:href (format "%s/%s" url-prefix "C1200000001-PROV1.html")
                  :text "coll1 (s1)"}])))

(defn setup-providers
  ""
  []
  (let [p1 {:provider-id "PROV1" :short-name "S1" :cmr-only true :small true}
        p2 {:provider-id "PROV2" :short-name "S2" :cmr-only true :small true}
        p3 {:provider-id "PROV3" :short-name "S3" :cmr-only true :small true}]
    (map ingest/create-provider [p1 p2 p3])))

(defn setup-collections
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
    ; (tags/associate-by-concept-ids
    ;   user-token
    ;   "gov.nasa.eosdis"
    ;   [{:concept-id (:concept-id tag-colls)
    ;     :revision-id (:revision-id tag-colls)
    ;     :data "stuff"}])
    (index/wait-until-indexed)
    [notag-colls nodoi-colls doi-colls tag-colls all-colls])))

(deftest check-testing-collections
  (testing "sanity check for test collection setup"
    (setup-providers)
    (let [[notag-colls nodoi-colls doi-colls tag-colls all-colls] (setup-collections)]
      (is (= (count notag-colls) 3))
      (is (= (count nodoi-colls) 6))
      (is (= (count doi-colls) 3))
      (is (= (count tag-colls) 6))
      (is (= (count all-colls) 9)))))

(deftest header-link
  (testing "check the link for landing pages in the header"
    (let [url "https://cmr.example.com/search"
          response (site (request :get url))]
      (is (= (:status response) 200))
      (is (substring? expected-header-link (:body response))))))

(deftest top-level-links
  (testing "check top level links"
    (let [url (str base-url "/site/collections/directory")
          response (site (request :get url))]
      (is (= (:status response) 200))
      (is (substring? expected-top-level-links (:body response)))
      ;; This page should also have a header link
      (is (substring? expected-header-link (:body response))))))

; (deftest eosdis-level-links
;   (testing "check eosdis level links"
;     (let [url (str base-url "/site/collections/landing-pages/eosdis")
;           response (site (request :get url))
;           body (:body response)]
;       (is (= (:status response) 200))
;       (is (= body "XXX"))
;       ;; XXX how do you set public-conf (or mock it) in cmr integration tests?
;       ;; XXX is done by creating a custom system that's set up in a fixture?
;       ;; XXX how do I reuse a system that's already set up in a fixture?
;       (is (substring? expected-eosdis-level-links body))
;       ;; This page should also have a header link
;       (is (substring? expected-header-link body)))))
