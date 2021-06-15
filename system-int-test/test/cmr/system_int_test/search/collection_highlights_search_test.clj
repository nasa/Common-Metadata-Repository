(ns cmr.system-int-test.search.collection-highlights-search-test
  "This tests the highlighting capability when searching for collections"
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(defn- make-coll
  "Helper for creating and ingesting a collection"
  [n & attribs]
  (d/ingest-umm-spec-collection "PROV1"
   (data-umm-c/collection n (apply merge {:EntryTitle (str "coll" n)} attribs))))

(defn- get-search-results-summaries
  "Returns a list of the highlighted-summary-snippets fields from a list of search results."
  [search-results]
  (->> search-results
       :results
       :entries
       (map :highlighted-summary-snippets)
       set))

(defn- ingest-collections-for-test
  "Ingest all of the collections that will be used for testing"
  []
  (make-coll 1 {:Abstract "no highlights"})
  (make-coll 2 {:Abstract (str "This summary has a lot of characters in it. **Findme** So many that "
                               "elasticsearch will break this summary into multiple snippets. I may "
                               "just have to keep typing until that happens. I will have to figure "
                               "out what keyword to search for in order to make two different "
                               "snippets have a match, but that seems (findme) doable. "
                               "The quick brown fox jumped --findme-- over the lazy dog. "
                               "Now is the time for all good men >>FINDME<< to come to the aid of "
                               "the party.")})
  (make-coll 3 {:Abstract "Match on 'collection'"})
  (make-coll 4 {:Abstract "Match on 'ocean'."})
  (make-coll 5 {:Abstract "Match on either 'ocean' or 'collection'."})
  (make-coll 6 {:Abstract "Match on 'ocean collection'"})
  (index/wait-until-indexed))

(deftest summary-highlighting-using-parameter-api
  (ingest-collections-for-test)
  (util/are2
    [expected-results search-params options]
    (= (set expected-results)
       (get-search-results-summaries
        (search/find-concepts-json :collection (merge {:include-highlights true} search-params options))))

    "No matching highlight in the summary field"
    [nil]
    {:keyword "coll1"}
    {}

    "Long summary with multiple snippets and case insensitive"
    [["**<em>Findme</em>** So many that elasticsearch will break this summary into multiple snippets."
      "out what keyword to search for in order to make two different snippets have a match, but that seems (<em>findme</em>"
      "The quick brown fox jumped --<em>findme</em>-- over the lazy dog."
      "Now is the time for all good men >><em>FINDME</em><< to come to the aid of the party."]]
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

    "Search with keyword and begin_tag/end-tag"
    [["Match on either '<b>ocean</b>' or '<b>collection</b>'."]
     ["Match on '<b>ocean</b> <b>collection</b>'"]]
    {:keyword "ocean collection"}
    {"options[highlights][begin_tag]" "<b>"
     "options[highlights][end_tag]" "</b>"}

    "Search with keyword and non-html begin_tag/end-tag"
    [["Match on either '!!!!!ocean!!!!!' or '!!!!!collection!!!!!'."]
     ["Match on '!!!!!ocean!!!!! !!!!!collection!!!!!'"]]
    {:keyword "ocean collection"}
    {"options[highlights][begin_tag]" "!!!!!"
     "options[highlights][end_tag]" "!!!!!"}

    ;; CMR-1986 There is a known bug in Elasticsearch highlighting with regard to snippet_length:
    ;; https://github.com/elastic/elasticsearch/issues/9442
    ;; This test should be updated to expect the correct length when the bug is fixed
    "Search with keyword and snippet_length = 50 and num_snippets = 2"
    [["**<em>Findme</em>** So many that elasticsearch will break this"
      "The quick brown fox jumped --<em>findme</em>-- over the lazy"]]
    {:keyword "findme"}
    {"options[highlights][snippet_length]" 50
     "options[highlights][num_snippets]" 2}))

(deftest summary-highlighting-using-json-query
  (ingest-collections-for-test)
  (util/are2
    [expected-results json-query-conditions options]
    (= (set expected-results)
       (get-search-results-summaries (search/find-concepts-in-json-with-json-query
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

    "Search with keyword and different begin_tag"
    [["Match on either '<br>ocean</em>' or '<br>collection</em>'."]
     ["Match on '<br>ocean</em> <br>collection</em>'"]]
    {:keyword "ocean collection"}
    {"options[highlights][begin_tag]" "<br>"
     "options[highlights][end_tag]" "</em>"}

    "Search with keyword and different end_tag"
    [["Match on either '<em>ocean</br>' or '<em>collection</br>'."]
     ["Match on '<em>ocean</br> <em>collection</br>'"]]
    {:keyword "ocean collection"}
    {"options[highlights][begin_tag]" "<em>"
     "options[highlights][end_tag]" "</br>"}

    ;; CMR-1986 - There is a known bug in Elasticsearch highlighting with regard to snippet_length:
    ;; https://github.com/elastic/elasticsearch/issues/9442
    ;; This test should be updated to expect the correct length when the bug is fixed.
    "Search with keyword and snippet_length and num_snippets"
    [["**<em>Findme</em>** So many that"
      "brown fox jumped --<em>findme</em>"
      "for all good men >><em>FINDME</em>"]]
    {:keyword "findme"}
    {"options[highlights][snippet_length]" 20
     "options[highlights][num_snippets]" 3}))

(deftest validate-highlights-options
  (testing "include_highlights must be set to true for highlights options"
    (is (= {:status 400
            :errors ["Highlights options are not allowed unless the include-highlights is true."]}
           (search/find-concepts-json :collection {"options[highlights][begin_tag]" "<br>"}))))

  (testing "snippet_length and num_snippets must be valid integers"
    (are [param value error]
         (= {:status 400 :errors [error]}
            (search/find-concepts-json :collection
                                       {:include-highlights true
                                        (format "options[highlights][%s]" param) value}))
         "snippet_length" "FOO" "snippet_length option [FOO] for highlights is not a valid integer."
         "snippet_length" 10.5 "snippet_length option [10.5] for highlights is not a valid integer."
         "snippet_length" -5 "snippet_length option [-5] for highlights must be an integer greater than 0."
         "num_snippets" "FOO" "num_snippets option [FOO] for highlights is not a valid integer."
         "num_snippets" 10.5 "num_snippets option [10.5] for highlights is not a valid integer."
         "num_snippets" -5 "num_snippets option [-5] for highlights must be an integer greater than 0."))

  (testing "invalid highlights options"
    (is (= {:status 400
            :errors ["Option [bad_option] is not supported for param [highlights]"]}
           (search/find-concepts-json :collection {:include-highlights true
                                                   "options[highlights][bad-option]" "<br>"})))))

(deftest invalid-highlight-response-formats
  (testing "invalid xml response formats"
    (are [resp-format]
         (= {:status 400 :errors ["Highlights are only supported in the JSON format."]}
            (search/get-search-failure-xml-data
              (search/find-concepts-in-format resp-format :collection {:include-highlights true})))
         mt/echo10
         mt/dif
         mt/dif10
         mt/xml
         mt/kml
         mt/atom
         mt/iso19115))

  (testing "invalid json response formats"
     (are [resp-format]
         (= {:status 400 :errors ["Highlights are only supported in the JSON format."]}
            (search/get-search-failure-data
              (search/find-concepts-in-format resp-format :collection {:include-highlights true})))
         mt/umm-json
         mt/opendata)))

(deftest special-characters-test
  (make-coll 1 {:Abstract "MODIS/Terra dataset."})
  (index/wait-until-indexed)
  (is (= #{["<em>MODIS</em>/<em>Terra</em> dataset."]}
         (get-search-results-summaries (search/find-concepts-in-json-with-json-query
                                        :collection
                                        {:include-highlights true}
                                        {:keyword "MODIS/Terra"})))))

(deftest reserved-characters-test
  ;; This test documents the current elasticsearch highlighting behavior with respect to reserved
  ;; characters. The behavior is inconsistent for the : ? and * characters.
  ;; Note: original reserved string "\"" is now used as keyword-phrase boundary, literal "\"" needs
  ;; to be entered as "\\\"" to pass the keyword-phrase validation. It will be converted back to "\""
  ;; afterwards.
  (let [reserved-strings #{"+" "-" "=" "&&" "||" ">" "<" "!" "(" ")" "{" "}" "[" "]" "^" "\"" "~" "*"
                           "?" ":" "\\" "/"}
        reserved-strings-with-different-behavior #{":" "?" "*"}]
    ;; Ingest one collection with a different reserved character for each collection. For example,
    ;; MODIS+TERRA, MODIS-TERRA, MODIS&&TERRA, etc.
    (dorun (map-indexed #(make-coll (inc %1) {:Abstract (format "MODIS%sTERRA dataset." %2)})
                        reserved-strings))
    (index/wait-until-indexed)

    (testing "Most reserved characters are not highlighted when they are searched against."
      (doseq [reserved-string (set/difference reserved-strings
                                reserved-strings-with-different-behavior)]
        (is (= #{[(format "<em>MODIS</em>%s<em>TERRA</em> dataset." reserved-string)]}
               (get-search-results-summaries (search/find-concepts-in-json-with-json-query
                                              :collection
                                              {:include-highlights true}
                                              {:keyword (if (= "\"" reserved-string)
                                                          "MODIS\\\"TERRA"
                                                          (format "MODIS%sTERRA"
                                                                  reserved-string))}))))))

    (testing "Colons are highlighted along with the search string."
      (is (= #{["<em>MODIS:TERRA</em> dataset."]}
             (get-search-results-summaries (search/find-concepts-in-json-with-json-query
                                            :collection
                                            {:include-highlights true}
                                            {:keyword "MODIS:TERRA"})))))

    (testing "Wildcard searches do not highlight any strings with reserved characters except for
             colon."
      (doseq [reserved-string #{"*" "?"}]
        (is (= (set [nil ["<em>MODIS:TERRA</em> dataset."]])
               (get-search-results-summaries
                (search/find-concepts-in-json-with-json-query
                 :collection
                 {:include-highlights true
                  :page-size 1000}
                 {:keyword (format "MODIS%sTERRA"
                                   reserved-string)}))))))))
