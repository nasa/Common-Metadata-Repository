(ns cmr.system-int-test.search.tool.tool-search-test
  "This tests searching tools."
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mime-types]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-json :as data-umm-json]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.system-int-test.data2.umm-spec-tool :as data-umm-t]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.association-util :as au]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.tool-util :as tool]
   [cmr.umm-spec.versioning :as umm-version]))

(use-fixtures :each
              (join-fixtures
               [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"})
                tool/grant-all-tool-fixture]))

(deftest search-for-tools-validation-test
  (testing "Unrecognized parameters"
    (is (= {:status 400
            :errors ["Parameter [foo] was not recognized."]}
           (tool/search-refs {:foo "bar"}))))

  (testing "Unsupported sort-key parameters"
    (is (= {:status 400
            :errors ["The sort key [concept_id] is not a valid field for sorting tools."]}
           (tool/search-refs {:sort-key "concept_id"}))))

  (testing "Search with wildcards in concept_id param not supported."
    (is (= {:status 400
            :errors ["Concept-id [TL*] is not valid."
                     "Option [pattern] is not supported for param [concept_id]"]}
           (tool/search-refs {:concept-id "TL*" "options[concept-id][pattern]" true}))))

  (testing "Search with ignore_case in concept_id param not supported."
    (is (= {:status 400
            :errors ["Option [ignore_case] is not supported for param [concept_id]"]}
           (tool/search-refs
            {:concept-id "TL1000-PROV1" "options[concept-id][ignore-case]" true}))))

  (testing "Default tool search result format is XML"
    (let [{:keys [status headers]} (search/find-concepts-in-format nil :tool {})]
      (is (= 200 status))
      (is (= "application/xml; charset=utf-8" (get headers "Content-Type")))))

  (testing "Unsuported result format in headers"
    (is (= {:status 400
            :errors ["The mime type [application/atom+xml] is not supported for tools."]}
           (search/get-search-failure-xml-data
            (search/find-concepts-in-format :atom+xml :tool {})))))

  (testing "Unsuported result format in url extension"
    (is (= {:status 400
            :errors ["The mime type [application/atom+xml] is not supported for tools."]}
           (search/get-search-failure-xml-data
            (search/find-concepts-in-format
             nil :tool {} {:url-extension "atom"}))))))

(deftest search-for-tools-test
  (let [tool1 (tool/ingest-tool-with-attrs {:native-id "TL1"
                                            :Name "Tool1"
                                            :provider-id "PROV1"})
        tool2 (tool/ingest-tool-with-attrs {:native-id "tl2"
                                            :Name "Tool2"
                                            :provider-id "PROV1"})
        tool3 (tool/ingest-tool-with-attrs {:native-id "tl3"
                                            :Name "a sub for tool2"
                                            :provider-id "PROV2"})
        tool4 (tool/ingest-tool-with-attrs {:native-id "tool4"
                                            :Name "t.other"
                                            :provider-id "PROV2"})
        prov1-tools [tool1 tool2]
        prov2-tools [tool3 tool4]
        all-tools (concat prov1-tools prov2-tools)]
    (index/wait-until-indexed)

    (are3 [expected-tools query]
      (do
        (testing "XML references format"
          (d/assert-refs-match expected-tools (tool/search-refs query)))
        (testing "JSON format"
          (tool/assert-tool-search expected-tools (tool/search-json query))))

      "Find all"
      all-tools {}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; name Param
      "By name case sensitive - exact match"
      [tool1]
      {:name "Tool1"}

      "By name case sensitive, default ignore-case true"
      [tool1]
      {:name "tool1"}

      "By name ignore case false"
      []
      {:name "tool1" "options[name][ignore-case]" false}

      "By name ignore case true"
      [tool1]
      {:name "tool1" "options[name][ignore-case]" true}

      "By name Pattern, default false"
      []
      {:name "*other"}

      "By name Pattern true"
      [tool4]
      {:name "*other" "options[name][pattern]" true}

      "By name Pattern false"
      []
      {:name "*other" "options[name][pattern]" false}

      "By multiple names"
      [tool1 tool2]
      {:name ["Tool1" "tool2"]}

      "By multiple names with options"
      [tool1 tool4]
      {:name ["Tool1" "*other"] "options[name][pattern]" true}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; provider Param
      "By provider - exact match"
      prov1-tools
      {:provider "PROV1"}

      "By provider, default ignore-case true"
      prov1-tools
      {:provider "prov1"}

      "By provider ignore case false"
      []
      {:provider "prov1" "options[provider][ignore-case]" false}

      "By provider ignore case true"
      prov1-tools
      {:provider "prov1" "options[provider][ignore-case]" true}

      "By provider Pattern, default false"
      []
      {:provider "PROV?"}

      "By provider Pattern true"
      all-tools
      {:provider "PROV?" "options[provider][pattern]" true}

      "By provider Pattern false"
      []
      {:provider "PROV?" "options[provider][pattern]" false}

      "By multiple providers"
      prov2-tools
      {:provider ["PROV2" "PROV3"]}

      "By multiple providers with options"
      all-tools
      {:provider ["PROV1" "*2"] "options[provider][pattern]" true}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; native-id Param
      "By native-id case sensitive - exact match"
      [tool1]
      {:native-id "TL1"}

      "By native-id case sensitive, default ignore-case true"
      [tool1]
      {:native-id "tl1"}

      "By native-id ignore case false"
      []
      {:native-id "tl1" "options[native-id][ignore-case]" false}

      "By native-id ignore case true"
      [tool1]
      {:native-id "tl1" "options[native-id][ignore-case]" true}

      "By native-id Pattern, default false"
      []
      {:native-id "tl*"}

      "By native-id Pattern true"
      [tool1 tool2 tool3]
      {:native-id "tl*" "options[native-id][pattern]" true}

      "By native-id Pattern false"
      []
      {:native-id "tl*" "options[native-id][pattern]" false}

      "By multiple native-ids"
      [tool1 tool2]
      {:native-id ["TL1" "tl2"]}

      "By multiple native-ids with options"
      [tool1 tool4]
      {:native-id ["TL1" "tool*"] "options[native-id][pattern]" true}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; concept-id Param
      "By concept-id - single"
      [tool1]
      {:concept-id (:concept-id tool1)}

      "By concept-id - multiple"
      [tool1 tool2]
      {:concept-id [(:concept-id tool1) (:concept-id tool2)]}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; Combination of params
      "Combination of params"
      [tool3]
      {:native-id "tl*" :provider "PROV2" "options[native-id][pattern]" true})))

(deftest search-tool-simple-keywords-test
  (let [tl1 (tool/ingest-tool-with-attrs {:native-id "tl-1"
                                          :Name "Tool 1"
                                          :LongName "Long Tool Name-1"
                                          :Version "40.0"})
        tl2 (tool/ingest-tool-with-attrs {:native-id "tl-2"
                                          :Name "Tool 2"
                                          :LongName "Long Tool Name-2"
                                          :Version "42.0"
                                          :AncillaryKeywords ["stuff" "things"]})]
    (index/wait-until-indexed)

    (are3 [expected-tools keyword-query]
      (tool/assert-tool-search
       expected-tools (tool/search-json {:keyword keyword-query}))

      "Name"
      [tl1 tl2]
      "Tool"

      "Long Name"
      [tl1 tl2]
      "Long"

      "Version"
      [tl2]
      "42.0"

      "Combination of keywords"
      [tl1]
      "Tool Name-1"

      "Ancillary Keywords"
      [tl2]
      "things stuff"

      "Combination of keywords - different order, case insensitive"
      [tl2]
      "nAmE-2 tOoL"

      "Wildcards"
      [tl1 tl2]
      "T?ol Name*")))

(deftest search-tool-url-keywords-test
  (let [url1 (data-umm-t/url {:URLValue "http://data.space/downloads"
                              :Description "Pertinent Data Source Page 1"
                              :URLContentType "DistributionURL"
                              :Type "GOTO WEB TOOL"
                              :Subtype "SUBSETTER"})
        url2 (data-umm-t/url {:URLValue "http://data.space/home"
                              :Description "Pertinent Data Source Page 2"
                              :URLContentType "DistributionURL"
                              :Type "DOWNLOAD SOFTWARE"
                              :Subtype "MOBILE APP"})
        tl1 (tool/ingest-tool-with-attrs {:native-id "tl-1"
                                          :Name "Tool 1"
                                          :URL url1})
        tl2 (tool/ingest-tool-with-attrs {:native-id "tl-2"
                                          :Name "Tool 2"
                                          :URL url2})]
    (index/wait-until-indexed)

    (are3 [expected-tools keyword-query]
      (d/assert-refs-match
       expected-tools (tool/search-refs {:keyword keyword-query}))

      "URL"
      [tl1]
      "space downloads"

      "Description"
      [tl1 tl2]
      "Data Source"

      "URLContentType"
      [tl1 tl2]
      "DistributionURL"

      "Type"
      [tl2]
      "download software"

      "Subtype"
      [tl1]
      "SUBSETTER")))

(deftest search-tool-related-url-keywords-test
  (let [url1 (data-umm-cmn/related-url {:URL "http://data.space/downloads"
                                        :Description "Pertinent Data Source Page 1"
                                        :URLContentType "CollectionURL"
                                        :Type "EXTENDED METADATA"
                                        :Subtype "DMR++"})
        url2 (data-umm-cmn/related-url {:URL "http://data.space/home"
                                        :Description "Pertinent Data Source Page 2"
                                        :URLContentType "PublicationURL"
                                        :Type "VIEW RELATED INFORMATION"
                                        :Subtype "ALGORITHM DOCUMENTATION"})
        tl1 (tool/ingest-tool-with-attrs {:native-id "tl-1"
                                          :Name "Tool 1"
                                          :RelatedURLs [url1]})
        tl2 (tool/ingest-tool-with-attrs {:native-id "tl-2"
                                          :Name "Tool 2"
                                          :RelatedURLs [url2]})
        tl3 (tool/ingest-tool-with-attrs {:native-id "tl-3"
                                          :Name "Tool 3"})]
    (index/wait-until-indexed)

    (are3 [expected-tools keyword-query]
      (d/assert-refs-match
       expected-tools (tool/search-refs {:keyword keyword-query}))

      "URL"
      [tl1]
      "space downloads"

      "Description"
      [tl1 tl2]
      "Data Source"

      "URLContentType"
      [tl2]
      "PublicationURL"

      "Type"
      [tl2]
      "view related information"

      "Subtype"
      [tl1]
      "dmr++")))

(deftest search-tool-contact-group-keywords-test
  (let [tl1 (tool/ingest-tool-with-attrs {:native-id "tl-1"
                                          :Name "Tool 1"
                                          :ContactGroups [(data-umm-t/contact-group)]})
        tl2 (tool/ingest-tool-with-attrs {:native-id "tl-2"
                                          :Name "Tool 2"})]
    (index/wait-until-indexed)

    (are3 [expected-tools keyword-query]
      (tool/assert-tool-search
       expected-tools (tool/search-json {:keyword keyword-query}))

      "Roles"
      [tl1]
      "AUTHOR"

      "Group Name"
      [tl1]
      "Group Name")))

(deftest search-tool-contact-persons-keywords-test
  (let [tl1 (tool/ingest-tool-with-attrs {:native-id "tl-1"
                                          :Name "Tool 1"
                                          :ContactPersons [(data-umm-t/contact-person)]})
        tl2 (tool/ingest-tool-with-attrs {:native-id "tl-2"
                                          :Name "Tool 2"})]
    (index/wait-until-indexed)

    (are3 [expected-tools keyword-query]
      (d/assert-refs-match
       expected-tools (tool/search-refs {:keyword keyword-query}))

      "Roles"
      [tl1]
      "AUTHOR"

      "First Name"
      [tl1]
      "Alice"

      "Last Name"
      [tl1]
      "Bob")))

(deftest search-tool-keywords-test
  (let [tl1 (tool/ingest-tool-with-attrs {:native-id "tl-1"
                                          :Name "Tool 1"
                                          :ToolKeywords [(data-umm-t/tool-keywords)]})
        tl2 (tool/ingest-tool-with-attrs {:native-id "tl-2"
                                          :Name "Tool 2"})]
    (index/wait-until-indexed)

    (are3 [expected-tools keyword-query]
      (d/assert-refs-match
       expected-tools (tool/search-refs {:keyword keyword-query}))

      "Tool Category"
      [tl1]
      "tool category"

      "Tool Specific Term"
      [tl1]
      "specific tool term"

      "Tool Term"
      [tl1]
      "tool term"

      "Tool Topic"
      [tl1]
      "tool topic")))

(deftest search-organization-keywords-test
  (let [tl1 (tool/ingest-tool-with-attrs {:native-id "tl-1"
                                          :Name "Tool 1"
                                          :Organizations [(data-umm-t/organization)]})
        tl2 (tool/ingest-tool-with-attrs {:native-id "tl-2"
                                          :Name "Tool 2"})]
    (index/wait-until-indexed)

    (are3 [expected-tools keyword-query]
      (d/assert-refs-match
       expected-tools (tool/search-refs {:keyword keyword-query}))

      "Short Name"
      [tl1]
      "tlorg1"

      "Long Name"
      [tl1]
      "Tool Org 1"

      "Roles"
      [tl1 tl2]
      "SERVICE PROVIDER")))

(deftest tool-search-sort
  (let [tool1 (tool/ingest-tool-with-attrs {:native-id "tl1"
                                            :Name "tool"
                                            :LongName "LongName4"
                                            :provider-id "PROV2"})
        tool2 (tool/ingest-tool-with-attrs {:native-id "tl2"
                                            :Name "Tool 2"
                                            :LongName "LongName2"
                                            :provider-id "PROV1"})
        tool3 (tool/ingest-tool-with-attrs {:native-id "tl3"
                                            :Name "a tool"
                                            :LongName "LongName1"
                                            :provider-id "PROV1"})
        tool4 (tool/ingest-tool-with-attrs {:native-id "tl4"
                                            :Name "tool"
                                            :LongName "LongName3"
                                            :provider-id "PROV1"})]
    (index/wait-until-indexed)

    (are3 [sort-key expected-tools]
      (is (d/refs-match-order?
           expected-tools
           (tool/search-refs {:sort-key sort-key})))

      "Default sort"
      nil
      [tool3 tool4 tool1 tool2]

      "Sort by name"
      "name"
      [tool3 tool4 tool1 tool2]

      "Sort by name descending order"
      "-name"
      [tool2 tool4 tool1 tool3]

      "Sort by provider id"
      "provider"
      [tool2 tool3 tool4 tool1]

      "Sort by provider id descending order"
      "-provider"
      [tool1 tool2 tool3 tool4]

      "Sort by revision-date"
      "revision_date"
      [tool1 tool2 tool3 tool4]

      "Sort by revision-date descending order"
      "-revision_date"
      [tool4 tool3 tool2 tool1]

      "Sort by long-name"
      "long_name"
      [tool3 tool2 tool4 tool1]

      "Sort by long-name descending order"
      "-long_name"
      [tool1 tool4 tool2 tool3]

      "Sort by name ascending then provider id ascending explicitly"
      ["name" "provider"]
      [tool3 tool4 tool1 tool2]

      "Sort by name ascending then provider id descending order"
      ["name" "-provider"]
      [tool3 tool1 tool4 tool2]

      "Sort by name then provider id descending order"
      ["-name" "-provider"]
      [tool2 tool1 tool4 tool3])))

(deftest collection-tool-search-in-umm-json-format-test
  (testing "collection search result in UMM JSON does have associated tools. tool search result in UMM JSON format does not have associated collections"
    (let [token (e/login (s/context) "user1")
          coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection) {:format :umm-json :token token})
          ;; create tools
          tool1-concept (tool/make-tool-concept {:native-id "svc1"
                                                           :Name "tool1"})
          tool1 (tool/ingest-tool tool1-concept)
          tool2-concept (tool/make-tool-concept {:native-id "svc2"
                                                           :Name "tool2"})
          tool2 (tool/ingest-tool tool2-concept)
          tool1-concept-id (:concept-id tool1)
          tool1-assoc-colls [{:concept-id (:concept-id coll1)}]
          associations {:associations {:collections [(:concept-id coll1)]}
                        :association-details {:collections [{:concept-id (:concept-id coll1)}]}}
          expected-tool1 (merge tool1-concept tool1 associations)
          expected-tool2 (merge tool2-concept tool2)]
      ;; index the collections so that they can be found during tool association
      (index/wait-until-indexed)
      ;; create tool associations
      (au/associate-by-concept-ids token tool1-concept-id tool1-assoc-colls)
      (index/wait-until-indexed)

      ;; Verify collection search UMM JSON response does contain tool association.
      (let [response (search/find-concepts-umm-json :collection {} {})
            meta (-> response
                     :body
                     (json/parse-string true)
                     :items
                     first
                     :meta)
            tool-associations (get-in meta [:associations :tools])]

        (is (= tool-associations [tool1-concept-id])))

      ;; verify tool search UMM JSON response is correct.
      (are3 [umm-version options]
        (data-umm-json/assert-tool-umm-jsons-match
         umm-version [expected-tool1 expected-tool2]
         (search/find-concepts-umm-json :tool {} options))

        "without specifying UMM JSON version"
        umm-version/current-tool-version
        {}

        "explicit UMM JSON version through accept header"
        umm-version/current-tool-version
        {:accept (mime-types/with-version mime-types/umm-json umm-version/current-tool-version)}

        "explicit UMM JSON version through suffix"
        "1.2.0"
        {:url-extension "umm_json_v1_2_0"})))

  (testing "Searching with non-existent UMM JSON version"
    (are3 [options]
      (let [{:keys [status errors]} (search/find-concepts-umm-json :tool {} options)]
        (is (= 400 status))
        (is (= ["The mime type [application/vnd.nasa.cmr.umm_results+json] with version [0.1] is not supported for tools."]
               errors)))

      "explicit UMM JSON version through suffix"
      {:url-extension "umm_json_v0_1"}

      "explicit UMM JSON version through accept header"
      {:accept (mime-types/with-version mime-types/umm-json "0.1")})))
