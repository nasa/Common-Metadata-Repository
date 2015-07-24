(ns cmr.system-int-test.search.collection-highlights-search-test
  "This tests the highlighting capability when searching for collections"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.atom :as da]
            [cmr.system-int-test.data2.core :as d]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
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
                              "snippets have a match, but that seems (Findme) doable.")})
  (make-coll 3 {:summary "Match on 'collection'"})
  (make-coll 4 {:summary "Match on 'ocean'."})
  (make-coll 5 {:summary "Match on either 'ocean' or 'collection'."})
  (make-coll 6 {:summary "Match on 'ocean collection'"})
  (index/wait-until-indexed))

(deftest summary-highlighting-using-parameter-api
  (ingest-collections-for-test)
  (util/are2
    [expected-results search-options]
    (= expected-results
       (get-search-results-summaries
         (search/find-concepts-json :collection (merge {:include-highlights true} search-options))))

    "No matching highlight in the summary field"
    [nil]
    {:keyword "coll1"}

    "Long summary with multiple snippets"
    [[(str "This summary has a lot of characters in it. **<em>Findme</em>** So many that "
           "elasticsearch will break this")
      (str " figure out what keyword to search for in order to make two different snippets have a "
           "match, but that seems (<em>Findme</em>) doable.")]]
    {:keyword "Findme"}

    "Some highlights and some not using a wildcard"
    [nil
     nil
     ["Match on '<em>collection</em>'"]
     nil
     ["Match on either 'ocean' or '<em>collection</em>'."]
     ["Match on 'ocean <em>collection</em>'"]]
    {:keyword "c?ll*"}

    "Search with keyword with spaces treats as two separate keywords AND'ed together"
    [["Match on either '<em>ocean</em>' or '<em>collection</em>'."]
     ["Match on '<em>ocean</em> <em>collection</em>'"]]
    {:keyword "ocean collection"}))

(deftest summary-highlighting-using-json-query
  (ingest-collections-for-test)
  (util/are2
    [expected-results json-query-conditions]
    (= expected-results (get-search-results-summaries (search/find-concepts-in-json-with-json-query
                                                        :collection
                                                        {:include-highlights true}
                                                        json-query-conditions)))

    "JSON Query keyword with spaces treats as two separate keywords AND'ed together"
    [["Match on either '<em>ocean</em>' or '<em>collection</em>'."]
     ["Match on '<em>ocean</em> <em>collection</em>'"]]
    {:keyword "ocean collection"}

    "JSON Query AND multiple keyword conditions highlights multiple terms"
    [["Match on either '<em>ocean</em>' or '<em>collection</em>'."]
     ["Match on '<em>ocean</em> <em>collection</em>'"]]
    {:and [{:keyword "ocean"}
           {:keyword "collection"}
           {:not {:keyword "foo"}}]}

    "JSON Query OR multiple keyword conditions highlights multiple terms"
    [nil
     nil
     ["Match on '<em>collection</em>'"]
     ["Match on '<em>ocean</em>'."]
     ["Match on either '<em>ocean</em>' or '<em>collection</em>'."]
     ["Match on '<em>ocean</em> <em>collection</em>'"]]
    {:or [{:keyword "ocean"}
          {:keyword "collection"}
          {:not {:keyword "foo"}}]}))
