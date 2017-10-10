(ns cmr.collection-renderer.test.services.collection-renderer
  (require [clojure.test :refer :all]
           [cmr.common.test.test-check-ext :as ext :refer [defspec]]
           [clojure.test.check.generators :as gen]
           [com.gfredericks.test.chuck.clojure-test :refer [for-all]]
           [clojure.string :as str]
           [cmr.common.lifecycle :as l]
           [cmr.collection-renderer.services.collection-renderer :as cr]
           [cmr.umm-spec.test.expected-conversion :as expected-conversion]
           [cmr.umm-spec.test.umm-generators :as umm-gen]))

(defn create-renderer-context
  "Returns a context to use with testing collection"
  []
  {:system (l/start {cr/system-key (cr/create-collection-renderer)
                     :public-conf {:relative-root-url "/search"}}
                    nil)})

(def renderer-context
  "Returns a context to use with testing collection and caches it"
  (memoize create-renderer-context))

(deftest render-default-collection
  (let [coll expected-conversion/example-collection-record
          ^String html (cr/render-collection (renderer-context) coll)]
      (is html "Expected some HTML to be returned")
      (is (.contains html (:EntryTitle coll)))))

;; This checks that we can render any UMM collection without getting an exception.
;; A small number of tries is done to avoid making tests take a long time.
(defspec render-any-umm-collection 10
  (for-all [umm-record (gen/no-shrink umm-gen/umm-c-generator)]
    (let [^String html (cr/render-collection (renderer-context) umm-record)]
      (and html (.contains html (:EntryTitle umm-record))))))

;; Verify that the title of the html is the entry title
(deftest render-title-as-entry-title
  (let [coll expected-conversion/example-collection-record
            ^String html (cr/render-collection (renderer-context) coll)]
        (is html "Expected a title containing the entry title")
        (is (.contains html (str "<title>" (:EntryTitle coll) "</title>")))))