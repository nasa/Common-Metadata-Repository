(ns cmr.system-int-test.site.csw-page-test
  "Integration tests for csw retirement page"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.system-int-test.utils.html-helper :refer [find-element-by-type]]
   [cmr.system-int-test.utils.url-helper :as url]
   [crouton.html :as html]))

(deftest csw-retirement-test
  (testing "Page renders"
    (are3 [sub-url]
      (let [page-data (html/parse (format (str "%s" sub-url) (url/search-root)))
            links (->> page-data
                       (find-element-by-type :a)
                       (map #(get-in % [:attrs :href])))]
        ;; has Retirement Announcement h1 header
        (is (->> page-data
                 (find-element-by-type :h1)
                 (some #(= ["Retirement Announcement"] (:content %)))))
        ;; has link to CMR search api
        (is (some #(= "http://localhost:3003/site/docs/search/api.html" %) links))
        ;; has link to Opensearch
        (is (some #(= "http://localhost:3000/opensearch" %) links))
        ;; has link to STAC
        (is (some #(= "http://localhost:3000/stac" %) links)))

      "base csw url"
      "csw"

      "sub csw url"
      "csw/collection")))
