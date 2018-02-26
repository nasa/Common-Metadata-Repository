(ns cmr.collection-renderer.test.services.collection-renderer
  (require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [cmr.collection-renderer.services.collection-renderer :as cr]
   [cmr.common.lifecycle :as l]
   [cmr.common.test.test-check-ext :as ext :refer [defspec]]
   [cmr.umm-spec.test.expected-conversion :as expected-conversion]
   [cmr.umm-spec.test.umm-generators :as umm-gen]
   [com.gfredericks.test.chuck.clojure-test :refer [for-all]]))

(defn create-renderer-context
  "Returns a context to use with testing collection"
  []
  {:system (l/start {cr/system-key (cr/create-collection-renderer)
                     :public-conf {:relative-root-url "/search"
                                   :edsc-url "https://search.earthdata.nasa.gov"}}
                    nil)})

(def renderer-context
  "Returns a context to use with testing collection and caches it"
  (memoize create-renderer-context))

(deftest render-default-collection
  (testing "Get returned HTML"
    (let [coll expected-conversion/example-collection-record
          ^String html (cr/render-collection (renderer-context) coll "C1234-PROV1")]
      (is (string/includes? html (:EntryTitle coll))))))

;; This checks that we can render any UMM collection without getting an exception.
;; A small number of tries is done to avoid making tests take a long time.
(defspec render-any-umm-collection 10
  (testing "Render without exception"
    (for-all [umm-record (gen/no-shrink umm-gen/umm-c-generator)]
      (let [^String html (cr/render-collection (renderer-context) umm-record "C1234-PROV1")]
        (and html (string/includes? html (:EntryTitle umm-record)))))))

(deftest render-title-as-entry-title
  (testing "Verify that the title of the html is the entry title"
    (let [coll expected-conversion/example-collection-record
          ^String html (cr/render-collection (renderer-context) coll "C1234-PROV1")]
      (is (string/includes? html (str "<title>" (:EntryTitle coll) "</title>"))))))

(deftest render-edsc-link
  (testing "Verify that the correct EDSC url is rendered"
    (let [coll expected-conversion/example-collection-record
          ^String html (cr/render-collection (renderer-context) coll "C1234-PROV1")]
      (is (string/includes?
           html
           "https://search.earthdata.nasa.gov/search/granules?p=C1234-PROV1")))))
