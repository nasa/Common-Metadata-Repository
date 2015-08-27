(ns cmr.system-int-test.search.collection-highlights-search-test
  "This tests the highlighting capability when searching for collections"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common.util :as util]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(defn- make-coll
  "Helper for creating and ingesting a collection"
  [n & attribs]
  (d/ingest "PROV1" (dc/collection (apply merge {:entry-title (str "coll" n)} attribs))))

(defn- get-search-results-summaries
  "Returns a list of the highlighted-summary-snippets fields from a list of search results."
  [search-results]
  (->> search-results
       :results
       :entries
       (map :highlighted-summary-snippets)))

(defn- ingest-collections-for-test
  "Ingest all of the collections that will be used for testing"
  []
  (make-coll 1 {:summary "no highlights"})
  (make-coll 2 {:summary (str "This summary has a lot of characters in it. **Findme** So many that "
                              "elasticsearch will break this summary into multiple snippets. I may "
                              "just have to keep typing until that happens. I will have to figure "
                              "out what keyword to search for in order to make two different "
                              "snippets have a match, but that seems (findme) doable.")})
  (make-coll 3 {:summary "Match on 'collection'"})
  (make-coll 4 {:summary "Match on 'ocean'."})
  (make-coll 5 {:summary "Match on either 'ocean' or 'collection'."})
  (make-coll 6 {:summary "Match on 'ocean collection'"})
  (index/wait-until-indexed))

(deftest summary-highlighting-using-parameter-api
  (ingest-collections-for-test)
  (util/are2
    [expected-results search-params options]
    (= expected-results
       (get-search-results-summaries
         (search/find-concepts-json :collection (merge {:include-highlights true} search-params options))))

    "No matching highlight in the summary field"
    [nil]
    {:keyword "coll1"}
    {}

    "Long summary with multiple snippets and case insensitive"
    [[(str "This summary has a lot of characters in it. **<em>Findme</em>** So many that "
           "elasticsearch will break this")
      (str " figure out what keyword to search for in order to make two different snippets have a "
           "match, but that seems (<em>findme</em>) doable.")]]
    {:keyword "FiNdmE"}
    {}

    "Some highlights and some not using a wildcard"
    [nil
     nil
     ["Match on '<em>collection</em>'"]
     nil
     ["Match on either 'ocean' or '<em>collection</em>'."]
     ["Match on 'ocean <em>collection</em>'"]]
    {:keyword "c?ll*"}
    {}

    "Search with keyword with spaces treats as two separate keywords AND'ed together"
    [["Match on either '<em>ocean</em>' or '<em>collection</em>'."]
     ["Match on '<em>ocean</em> <em>collection</em>'"]]
    {:keyword "ocean collection"}
    {}

    "Search with keyord and begin-tag"
    [["Match on either '<br>ocean</em>' or '<br>collection</em>'."]
     ["Match on '<br>ocean</em> <br>collection</em>'"]]
    {:keyword "ocean collection"}
    {"options[highlights][begin-tag]" "<br>"}

    "Search with keyord and end-tag"
    [["Match on either '<em>ocean</br>' or '<em>collection</br>'."]
     ["Match on '<em>ocean</br> <em>collection</br>'"]]
    {:keyword "ocean collection"}
    {"options[highlights][end-tag]" "</br>"}

    "Search with keyord and snippet-length and num-fragments"
    [[" it. **<em>Findme</em>** So"]]
    {:keyword "findme"}
    {"options[highlights][snippet-length]" 20
     "options[highlights][num-fragments]" 1}))

(deftest summary-highlighting-using-json-query
  (ingest-collections-for-test)
  (util/are2
    [expected-results json-query-conditions options]
    (= expected-results (get-search-results-summaries (search/find-concepts-in-json-with-json-query
                                                        :collection
                                                        (merge {:include-highlights true} options)
                                                        json-query-conditions)))

    "JSON Query keyword with spaces treats as two separate keywords AND'ed together"
    [["Match on either '<em>ocean</em>' or '<em>collection</em>'."]
     ["Match on '<em>ocean</em> <em>collection</em>'"]]
    {:keyword "ocean collection"}
    {}

    "JSON Query AND multiple keyword conditions highlights multiple terms"
    [["Match on either '<em>ocean</em>' or '<em>collection</em>'."]
     ["Match on '<em>ocean</em> <em>collection</em>'"]]
    {:and [{:keyword "ocean"}
           {:keyword "collection"}
           {:not {:keyword "foo"}}]}
    {}

    "JSON Query OR multiple keyword conditions highlights multiple terms"
    [nil
     nil
     ["Match on '<em>collection</em>'"]
     ["Match on '<em>ocean</em>'."]
     ["Match on either '<em>ocean</em>' or '<em>collection</em>'."]
     ["Match on '<em>ocean</em> <em>collection</em>'"]]
    {:or [{:keyword "ocean"}
          {:keyword "collection"}
          {:not {:keyword "foo"}}]}
    {}

    "Search with keyord and begin-tag"
    [["Match on either '<br>ocean</em>' or '<br>collection</em>'."]
     ["Match on '<br>ocean</em> <br>collection</em>'"]]
    {:keyword "ocean collection"}
    {"options[highlights][begin-tag]" "<br>"}

    "Search with keyord and end-tag"
    [["Match on either '<em>ocean</br>' or '<em>collection</br>'."]
     ["Match on '<em>ocean</br> <em>collection</br>'"]]
    {:keyword "ocean collection"}
    {"options[highlights][end-tag]" "</br>"}

    "Search with keyord and snippet-length and num-fragments"
    [[" it. **<em>Findme</em>** So"]]
    {:keyword "findme"}
    {"options[highlights][snippet-length]" 20
     "options[highlights][num-fragments]" 1}))

(deftest validate-highlight-options
  (testing "include_highlights must be set to true for highlights options"
    (is (= {:status 400
            :errors ["Highlights options are not allowed unless the include-highlights is true."]}
           (search/find-concepts-json :collection {"options[highlights][begin-tag]" "<br>"})))))
