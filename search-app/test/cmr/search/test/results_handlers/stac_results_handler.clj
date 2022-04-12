(ns cmr.search.test.results-handlers.stac-results-handler
  "This tests the stac-results-handler namespace."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.search.results-handlers.stac-results-handler :as stac-results-handler]
   [ring.util.codec :as codec]))

(def ^:private metadata-link
  "example metadata-link for test"
  "https://example.com/metadata-link")

(deftest atom-links->assets
  (testing "Atom links to STAC assets"
    (are3 [links assets]
      (let [expected-assets (merge assets
                                   {:metadata {:href metadata-link
                                               :type "application/xml"}})]
        (is (= expected-assets
               (#'stac-results-handler/atom-links->assets metadata-link links))))

      "no extra links"
      nil
      nil

      "single data link"
      [{:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data"
        :link-type "data"
        :title "example data"
        :mime-type "application/x-hdfeos"}]
      {:data {:title "example data"
              :href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data"
              :type "application/x-hdfeos"}}

      "multiple data links"
      [{:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data"
        :link-type "data"
        :title "example data"
        :mime-type "application/x-hdfeos"}
       {:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data11"
        :link-type "data"
        :mime-type "application/gzip"}
       {:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data99"
        :link-type "data"
        :mime-type "application/x-hdf"}]
      {:data {:title "example data"
              :href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data"
              :type "application/x-hdfeos"}
       :data1 {:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data11"
               :type "application/gzip"}
       :data2 {:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data99"
               :type "application/x-hdf"}}

      "single browse link with valid mime-type"
      [{:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse"
        :link-type "browse"
        :title "example browse"
        :mime-type "image/tiff"}]
      {:browse {:title "example browse"
                :href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse"
                :type "image/tiff"}}

      "single browse link with valid mime-type to show mime-type has precedence over href suffix."
      [{:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse.jpeg"
        :link-type "browse"
        :title "example browse"
        :mime-type "image/tiff"}]
      {:browse {:title "example browse"
                :href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse.jpeg"
                :type "image/tiff"}}

      "single browse link without mime-type"
      [{:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse"
        :link-type "browse"
        :title "example browse"}]
      {:browse {:title "example browse"
                :href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse"
                :type "image/jpeg"}}

      "single browse link with invalid mime-type, type is determined by href suffix"
      [{:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse.tif"
        :link-type "browse"
        :title "example browse"
        :mime-type "image"}]
      {:browse {:title "example browse"
                :href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse.tif"
                :type "image/tiff"}}

      "multiple browse links, only the first one is used"
      [{:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse"
        :link-type "browse"
        :title "example browse"
        :mime-type "image/tiff"}
       {:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse1"
        :link-type "browse"
        :mime-type "image/jpeg"}]
      {:browse {:title "example browse"
                :href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse"
                :type "image/tiff"}}

      "single opendap link"
      [{:href "https://f5eil01v.edn.ecs.nasa.gov/opendapurl"
        :link-type "service"
        :title "opendap link"
        :mime-type "text/xml"}]
      {:opendap {:title "opendap link"
                 :href "https://f5eil01v.edn.ecs.nasa.gov/opendapurl"
                 :type "text/xml"}}

      "multiple opendap links, only the first one is used"
      [{:href "https://f5eil01v.edn.ecs.nasa.gov/opendapurl"
        :link-type "service"
        :title "opendap link"}
       {:href "https://f5eil01v.edn.ecs.nasa.gov/opendapurl2"
        :link-type "service"
        :title "opendap link2"}]
      {:opendap {:title "opendap link"
                 :href "https://f5eil01v.edn.ecs.nasa.gov/opendapurl"}}

      "multiple type of links"
      [{:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data"
        :link-type "data"
        :title "example data"
        :mime-type "application/x-hdfeos"}
       {:href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse"
        :link-type "browse"
        :title "example browse"
        :mime-type "image/tiff"}
       {:href "https://f5eil01v.edn.ecs.nasa.gov/opendapurl"
        :link-type "service"
        :title "opendap link"
        :mime-type "text/xml"}]
      {:data {:title "example data"
              :href "ftp://f5eil01v.edn.ecs.nasa.gov/example-data"
              :type "application/x-hdfeos"}
       :browse {:title "example browse"
                :href "ftp://f5eil01v.edn.ecs.nasa.gov/example-browse"
                :type "image/tiff"}
       :opendap {:title "opendap link"
                 :href "https://f5eil01v.edn.ecs.nasa.gov/opendapurl"
                 :type "text/xml"}})))

(defn- href
  "Returns the href for the given base query string and page-num"
  [base-string page-num]
  (format "http://localhost:3003/granules.stac?%s&page_num=%s" base-string page-num))

(defn- self-link
  "Returns the self link with the given page-num"
  [base-string page-num]
  {:rel "self"
   :href (href base-string page-num)})

(def ^:private root-link
  "Defines the root link"
  {:rel "root"
   :href "http://localhost:3003/"})

(defn- prev-link-get
  "Returns the prev-link with the given page-num"
  [base-string page-num]
  (when page-num
    {:rel "prev"
     :method "GET"
     :href (href base-string page-num)}))

(defn- next-link-get
  "Returns the next-link with the given page-num"
  [base-string page-num]
 (when page-num
   {:rel "next"
    :method "GET"
    :href (href base-string page-num)}))

(defn- prev-link-post
  "Returns the prev-link with the given page-num"
  [query-string page-num]
  (when page-num
    {:rel "prev"
     :method "POST"
     :merge true
     :body (as-> query-string query-string
                 (stac-results-handler/stac-post-body query-string)
                 (util/map-keys->snake_case query-string)
                 (assoc query-string :page_num (str page-num))
                 (into (sorted-map) query-string))
     :href "http://localhost:3003/granules.stac"}))

(defn- next-link-post
  "Returns the next-link with the given page-num"
  [query-string page-num]
  (when page-num
    {:rel "next"
     :method "POST"
     :merge true
     :body (as-> query-string query-string
                 (stac-results-handler/stac-post-body query-string)
                 (util/map-keys->snake_case query-string)
                 (assoc query-string :page_num (str page-num))
                 (into (sorted-map) query-string))
     :href "http://localhost:3003/granules.stac"}))

(deftest get-fc-links
  (testing "GET feature collection links"
    (are3 [query-string page-size page-num base-string expected-nav]
      (let [context (merge {:system {:public-conf {:protocol "http"
                                                   :host "localhost"
                                                   :port "3003"}}
                            :method :get}
                           {:query-string query-string})
             query {:page-size page-size
                    :offset (* (dec page-num) page-size)}
             hits 30
             [self-num prev-num next-num] expected-nav
             expected-links (remove nil?
                                    [(self-link base-string self-num)
                                     root-link
                                     (prev-link-get base-string prev-num)
                                     (next-link-get base-string next-num)])]
        (is (= expected-links
               (#'stac-results-handler/get-fc-links context query hits))))

      "default query string with no page-size or page-num"
      "collection_concept_id=C111-PROV"
      10
      1
      "collection_concept_id=C111-PROV"
      [1 nil 2]

      "query string with explicit page-num at the front of query string"
      "page-num=1&collection_concept_id=C111-PROV"
      10
      1
      "collection_concept_id=C111-PROV"
      [1 nil 2]

      "query string with explicit page_num at the front of query string"
      "page_num=1&collection_concept_id=C111-PROV"
      10
      1
      "collection_concept_id=C111-PROV"
      [1 nil 2]

      "query string with explicit page-num not at the front of query string"
      "collection_concept_id=C111-PROV&page-num=1"
      10
      1
      "collection_concept_id=C111-PROV"
      [1 nil 2]

      "query string with explicit page_num not at the front of query string"
      "collection_concept_id=C111-PROV&page_num=1"
      10
      1
      "collection_concept_id=C111-PROV"
      [1 nil 2]

      "query string with explicit page-size in query string"
      "page_size=20&collection_concept_id=C111-PROV"
      20
      1
      "page_size=20&collection_concept_id=C111-PROV"
      [1 nil 2]

      "query with both previous and next pages"
      "page_size=2&page_num=5&collection_concept_id=C111-PROV"
      2
      5
      "page_size=2&collection_concept_id=C111-PROV"
      [5 4 6]

      "query with no next page"
      "page_size=10&page_num=3&collection_concept_id=C111-PROV"
      10
      3
      "page_size=10&collection_concept_id=C111-PROV"
      [3 2 nil]

      "query with neither previous page nor next page"
      "page_num=1&collection_concept_id=C111-PROV&page_size=30"
      30
      1
      "collection_concept_id=C111-PROV&page_size=30"
      [1 nil nil]))

  (testing "POST feature collection links"
    (are3 [query-string page-size page-num base-string expected-nav]
      (let [context (merge {:system {:public-conf {:protocol "http"
                                                   :host "localhost"
                                                   :port "3003"}}
                            :method :post}
                           {:query-string query-string})
             query {:page-size page-size
                    :offset (* (dec page-num) page-size)}
             hits 30
             [self-num prev-num next-num] expected-nav
             expected-links (remove nil?
                                    [(self-link base-string self-num)
                                     root-link
                                     (prev-link-post base-string prev-num)
                                     (next-link-post base-string next-num)])]
        (is (= expected-links
               (#'stac-results-handler/get-fc-links context query hits))))

      "default query string with no page-size or page-num"
      "collection_concept_id=C111-PROV"
      10
      1
      "collection_concept_id=C111-PROV"
      [1 nil 2]

      "query string with explicit page-num at the front of query string"
      "page-num=1&collection_concept_id=C111-PROV"
      10
      1
      "collection_concept_id=C111-PROV"
      [1 nil 2]

      "query string with explicit page_num at the front of query string"
      "page_num=1&collection_concept_id=C111-PROV"
      10
      1
      "collection_concept_id=C111-PROV"
      [1 nil 2]

      "query string with explicit page-num not at the front of query string"
      "collection_concept_id=C111-PROV&page-num=1"
      10
      1
      "collection_concept_id=C111-PROV"
      [1 nil 2]

      "query string with explicit page_num not at the front of query string"
      "collection_concept_id=C111-PROV&page_num=1"
      10
      1
      "collection_concept_id=C111-PROV"
      [1 nil 2]

      "query string with explicit page-size in query string"
      "page_size=20&collection_concept_id=C111-PROV"
      20
      1
      "page_size=20&collection_concept_id=C111-PROV"
      [1 nil 2]

      "query with both previous and next pages"
      "page_size=2&page_num=5&collection_concept_id=C111-PROV"
      2
      5
      "page_size=2&collection_concept_id=C111-PROV"
      [5 4 6]

      "query with no next page"
      "page_size=10&page_num=3&collection_concept_id=C111-PROV"
      10
      3
      "page_size=10&collection_concept_id=C111-PROV"
      [3 2 nil]

      "query with neither previous page nor next page"
      "page_num=1&collection_concept_id=C111-PROV&page_size=30"
      30
      1
      "collection_concept_id=C111-PROV&page_size=30"
      [1 nil nil])))
