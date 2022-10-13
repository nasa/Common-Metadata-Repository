(ns cmr.system-int-test.search.service.service-search-test
  "This tests searching services."
  (:require
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mime-types]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-json :as data-umm-json]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-service :as data-umm-s]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.association-util :as au]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.service-util :as services]
   [cmr.umm-spec.versioning :as umm-version]))

(use-fixtures :each
              (join-fixtures
               [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"})
                services/grant-all-service-fixture]))

(deftest search-for-services-validation-test
  (testing "Unrecognized parameters"
    (is (= {:status 400
            :errors ["Parameter [foo] was not recognized."]}
           (services/search-refs {:foo "bar"}))))

  (testing "Unsupported sort-key parameters"
    (is (= {:status 400
            :errors ["The sort key [concept_id] is not a valid field for sorting services."]}
           (services/search-refs {:sort-key "concept_id"}))))

  (testing "Search with wildcards in concept_id param not supported."
    (is (= {:status 400
            :errors ["Concept-id [S*] is not valid."
                     "Option [pattern] is not supported for param [concept_id]"]}
           (services/search-refs {:concept-id "S*" "options[concept-id][pattern]" true}))))

  (testing "Search with ignore_case in concept_id param not supported."
    (is (= {:status 400
            :errors ["Option [ignore_case] is not supported for param [concept_id]"]}
           (services/search-refs
            {:concept-id "S1000-PROV1" "options[concept-id][ignore-case]" true}))))

  (testing "Default service search result format is XML"
    (let [{:keys [status headers]} (search/find-concepts-in-format nil :service {})]
      (is (= 200 status))
      (is (= "application/xml; charset=utf-8" (get headers "Content-Type")))))

  (testing "Unsuported result format in headers"
    (is (= {:status 400
            :errors ["The mime type [application/atom+xml] is not supported for services."]}
           (search/get-search-failure-xml-data
            (search/find-concepts-in-format :atom+xml :service {})))))

  (testing "Unsuported result format in url extension"
    (is (= {:status 400
            :errors ["The mime type [application/atom+xml] is not supported for services."]}
           (search/get-search-failure-xml-data
            (search/find-concepts-in-format
             nil :service {} {:url-extension "atom"}))))))

(deftest search-for-services-test
  (let [service1 (services/ingest-service-with-attrs {:native-id "SVC1"
                                                      :Name "Service1"
                                                      :provider-id "PROV1"})
        service2 (services/ingest-service-with-attrs {:native-id "svc2"
                                                      :Name "Service2"
                                                      :provider-id "PROV1"})
        service3 (services/ingest-service-with-attrs {:native-id "svc3"
                                                      :Name "a sub for service2"
                                                      :provider-id "PROV2"})
        service4 (services/ingest-service-with-attrs {:native-id "serv4"
                                                      :Name "s.other"
                                                      :Type "Harmony"
                                                      :provider-id "PROV2"})
        prov1-services [service1 service2]
        prov2-services [service3 service4]
        all-services (concat prov1-services prov2-services)]
    (index/wait-until-indexed)

    (are3 [expected-services query]
      (do
        (testing "XML references format"
          (d/assert-refs-match expected-services (services/search-refs query)))
        (testing "JSON format"
          (services/assert-service-search expected-services (services/search-json query))))

      "Find all"
      all-services {}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; name Param
      "By name case sensitive - exact match"
      [service1]
      {:name "Service1"}

      "By name case sensitive, default ignore-case true"
      [service1]
      {:name "service1"}

      "By name ignore case false"
      []
      {:name "service1" "options[name][ignore-case]" false}

      "By name ignore case true"
      [service1]
      {:name "service1" "options[name][ignore-case]" true}

      "By name Pattern, default false"
      []
      {:name "*other"}

      "By name Pattern true"
      [service4]
      {:name "*other" "options[name][pattern]" true}

      "By name Pattern false"
      []
      {:name "*other" "options[name][pattern]" false}

      "By multiple names"
      [service1 service2]
      {:name ["Service1" "service2"]}

      "By multiple names with options"
      [service1 service4]
      {:name ["Service1" "*other"] "options[name][pattern]" true}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; provider Param
      "By provider - exact match"
      prov1-services
      {:provider "PROV1"}

      "By provider, default ignore-case true"
      prov1-services
      {:provider "prov1"}

      "By provider ignore case false"
      []
      {:provider "prov1" "options[provider][ignore-case]" false}

      "By provider ignore case true"
      prov1-services
      {:provider "prov1" "options[provider][ignore-case]" true}

      "By provider Pattern, default false"
      []
      {:provider "PROV?"}

      "By provider Pattern true"
      all-services
      {:provider "PROV?" "options[provider][pattern]" true}

      "By provider Pattern false"
      []
      {:provider "PROV?" "options[provider][pattern]" false}

      "By multiple providers"
      prov2-services
      {:provider ["PROV2" "PROV3"]}

      "By multiple providers with options"
      all-services
      {:provider ["PROV1" "*2"] "options[provider][pattern]" true}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; native-id Param
      "By native-id case sensitive - exact match"
      [service1]
      {:native-id "SVC1"}

      "By native-id case sensitive, default ignore-case true"
      [service1]
      {:native-id "svc1"}

      "By native-id ignore case false"
      []
      {:native-id "svc1" "options[native-id][ignore-case]" false}

      "By native-id ignore case true"
      [service1]
      {:native-id "svc1" "options[native-id][ignore-case]" true}

      "By native-id Pattern, default false"
      []
      {:native-id "svc*"}

      "By native-id Pattern true"
      [service1 service2 service3]
      {:native-id "svc*" "options[native-id][pattern]" true}

      "By native-id Pattern false"
      []
      {:native-id "svc*" "options[native-id][pattern]" false}

      "By multiple native-ids"
      [service1 service2]
      {:native-id ["SVC1" "svc2"]}

      "By multiple native-ids with options"
      [service1 service4]
      {:native-id ["SVC1" "serv*"] "options[native-id][pattern]" true}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; concept-id Param
      "By concept-id - single"
      [service1]
      {:concept-id (:concept-id service1)}

      "By concept-id - multiple"
      [service1 service2]
      {:concept-id [(:concept-id service1) (:concept-id service2)]}

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; Combination of params
      "Combination of params"
      [service3]
      {:native-id "svc*" :provider "PROV2" "options[native-id][pattern]" true}

      "By service type"
      [service1 service2 service3]
      {:type "OPeNDAP"}

      "By service type case insensitive by default."
      [service1 service2 service3]
      {:type "OPeNDAp"}

      "By service types"
      [service1 service2 service3 service4]
      {:type ["OPeNDAP" "Harmony"]}

      "By service type pattern true"
      [service1 service2 service3]
      {:type "OPeN*" "options[type][pattern]" true}

      "By service type pattern false"
      []
      {:type "OPeN*" "options[type][pattern]" false}

      "By service type ignore case true"
      [service1 service2 service3]
      {:type "OPeNDAp" "options[type][ignore-case]" true}

      "By service type ignore case true 2"
      [service1 service2 service3]
      {:type "opendap" "options[type][ignore-case]" true}

      "By service type ignore case missing"
      [service1 service2 service3]
      {:type "opendap"}

      "By service type ignore case false"
      [service1 service2 service3]
      {:type "opendap" "options[type][ignore-case]" false}

      "By service type ignore case false 2"
      []
      {:type "OPeNDAP" "options[type][ignore-case]" false})))

(deftest search-service-simple-keywords-test
  (let [svc1 (services/ingest-service-with-attrs {:native-id "svc-1"
                                                  :Name "Service 1"
                                                  :LongName "Long Service Name-1"
                                                  :Version "40.0"})
        svc2 (services/ingest-service-with-attrs {:native-id "svc-2"
                                                  :Name "Service 2"
                                                  :LongName "Long Service Name-2"
                                                  :Version "42.0"
                                                  :AncillaryKeywords ["stuff" "things"]})]
    (index/wait-until-indexed)

    (are3 [expected-services keyword-query]
      (services/assert-service-search
       expected-services (services/search-json {:keyword keyword-query}))

      "Name"
      [svc1 svc2]
      "Service"

      "Long Name"
      [svc1 svc2]
      "Long"

      "Version"
      [svc2]
      "42.0"

      "Combination of keywords"
      [svc1]
      "Service Name-1"

      "Ancillary Keywords"
      [svc2]
      "things stuff"

      "Combination of keywords - different order, case insensitive"
      [svc2]
      "nAmE-2 sERviCE"

      "Wildcards"
      [svc1 svc2]
      "Ser?ice Name*")))

(deftest search-service-url-keywords-test
  (let [url1 (data-umm-s/url {:URLValue "http://data.space/downloads"
                              :Description "Pertinent Data Source Page 1"})
        url2 (data-umm-s/url {:URLValue "http://data.space/home"
                              :Description "Pertinent Data Source Page 2"})
        svc1 (services/ingest-service-with-attrs {:native-id "svc-1"
                                                  :Name "Service 1"
                                                  :URL url1})
        svc2 (services/ingest-service-with-attrs {:native-id "svc-2"
                                                  :Name "Service 2"
                                                  :URL url2})
        svc3 (services/ingest-service-with-attrs {:native-id "svc-3"
                                                  :Name "Service 3"
                                                  :URL nil})]
    (index/wait-until-indexed)

    (are3 [expected-services keyword-query]
      (d/assert-refs-match
       expected-services (services/search-refs {:keyword keyword-query}))

      "URL"
      [svc1]
      "space downloads"

      "Description"
      [svc1 svc2]
      "Data Source")))

(deftest search-service-contact-group-keywords-test
  (let [svc1 (services/ingest-service-with-attrs {:native-id "svc-1"
                                                  :Name "Service 1"
                                                  :ContactGroups [(data-umm-s/contact-group)]})
        svc2 (services/ingest-service-with-attrs {:native-id "svc-2"
                                                  :Name "Service 2"})]
    (index/wait-until-indexed)

    (are3 [expected-services keyword-query]
      (services/assert-service-search
       expected-services (services/search-json {:keyword keyword-query}))

      "Roles"
      [svc1]
      "TECHNICAL CONTACT"

      "Group Name"
      [svc1]
      "Group Name")))

(deftest search-service-contact-persons-keywords-test
  (let [svc1 (services/ingest-service-with-attrs {:native-id "svc-1"
                                                  :Name "Service 1"
                                                  :ContactPersons [(data-umm-s/contact-person)]})
        svc2 (services/ingest-service-with-attrs {:native-id "svc-2"
                                                  :Name "Service 2"})]
    (index/wait-until-indexed)

    (are3 [expected-services keyword-query]
      (d/assert-refs-match
       expected-services (services/search-refs {:keyword keyword-query}))

      "Roles"
      [svc1]
      "AUTHOR"

      "First Name"
      [svc1]
      "Alice"

      "Last Name"
      [svc1]
      "Bob")))


(deftest search-service-keywords-test
  (let [svc1 (services/ingest-service-with-attrs {:native-id "svc-1"
                                                  :Name "Service 1"
                                                  :ServiceKeywords [(data-umm-s/service-keywords)]})
        svc2 (services/ingest-service-with-attrs {:native-id "svc-2"
                                                  :Name "Service 2"})]
    (index/wait-until-indexed)

    (are3 [expected-services keyword-query]
      (d/assert-refs-match
       expected-services (services/search-refs {:keyword keyword-query}))

      "Service Category"
      [svc1]
      "service category"

      "Service Specific Term"
      [svc1]
      "specific service term"

      "Service Term"
      [svc1]
      "service term"

      "Service Topic"
      [svc1]
      "service topic")))

(deftest search-service-organization-keywords-test
  (let [svc1 (services/ingest-service-with-attrs {:native-id "svc-1"
                                                  :Name "Service 1"
                                                  :ServiceOrganizations [(data-umm-s/service-organization)]})
        svc2 (services/ingest-service-with-attrs {:native-id "svc-2"
                                                  :Name "Service 2"})]
    (index/wait-until-indexed)

    (are3 [expected-services keyword-query]
      (d/assert-refs-match
       expected-services (services/search-refs {:keyword keyword-query}))

      "Short Name"
      [svc1]
      "svcorg1"

      "Long Name"
      [svc1]
      "Service Org 1"

      "Roles"
      [svc1 svc2]
      "SERVICE PROVIDER")))

(deftest deleted-services-not-found-test
  (let [token (e/login (s/context) "user1")
        coll1 (d/ingest-umm-spec-collection "PROV1"
                                            (data-umm-c/collection 1 {})
                                            {:token token})
        svc1-concept (services/make-service-concept {:native-id "svc1"
                                                     :Name "Service1"})
        service1 (services/ingest-service svc1-concept {:token token})
        svc2-concept (services/make-service-concept {:native-id "svc2"
                                                     :Name "Service2"})
        service2 (services/ingest-service svc2-concept {:token token})
        all-services [service1 service2]]
    (index/wait-until-indexed)

    ;; Now I should find the all services when searching
    (d/assert-refs-match all-services (services/search-refs {}))

    ;; Delete service1
    (ingest/delete-concept svc1-concept {:token token})
    (index/wait-until-indexed)
    ;; Now searching services does not find the deleted service
    (d/assert-refs-match [service2] (services/search-refs {}))

    ;; Now verify that after we delete a service that has service association,
    ;; we can't find it through search
    ;; create service associations on service2
    (au/associate-by-concept-ids token
                                 (:concept-id service2)
                                 [{:concept-id (:concept-id coll1)}])
    ;; Delete service2
    (ingest/delete-concept svc2-concept {:token token})
    (index/wait-until-indexed)
    ;; Now searching services does not find the deleted services
    (d/assert-refs-match [] (services/search-refs {}))))

(deftest service-search-sort
  (let [service1 (services/ingest-service-with-attrs {:native-id "svc1"
                                                      :Name "service"
                                                      :LongName "LongName4"
                                                      :provider-id "PROV2"})
        service2 (services/ingest-service-with-attrs {:native-id "svc2"
                                                      :Name "Service 2"
                                                      :LongName "LongName2"
                                                      :provider-id "PROV1"})
        service3 (services/ingest-service-with-attrs {:native-id "svc3"
                                                      :Name "a service"
                                                      :LongName "LongName1"
                                                      :provider-id "PROV1"})
        service4 (services/ingest-service-with-attrs {:native-id "svc4"
                                                      :Name "service"
                                                      :Type "Harmony"
                                                      :LongName "LongName3"
                                                      :provider-id "PROV1"})]
    (index/wait-until-indexed)

    (are3 [sort-key expected-services]
      (is (d/refs-match-order?
           expected-services
           (services/search-refs {:sort-key sort-key})))

      "Default sort"
      nil
      [service3 service4 service1 service2]

      "Sort by name"
      "name"
      [service3 service4 service1 service2]

      "Sort by name descending order"
      "-name"
      [service2 service4 service1 service3]

      "Sort by provider id"
      "provider"
      [service2 service3 service4 service1]

      "Sort by provider id descending order"
      "-provider"
      [service1 service2 service3 service4]

      "Sort by revision-date"
      "revision_date"
      [service1 service2 service3 service4]

      "Sort by revision-date descending order"
      "-revision_date"
      [service4 service3 service2 service1]

      "Sort by long-name"
      "long_name"
      [service3 service2 service4 service1]

      "Sort by long-name descending order"
      "-long_name"
      [service1 service4 service2 service3]

      "Sort by name ascending then provider id ascending explicitly"
      ["name" "provider"]
      [service3 service4 service1 service2]

      "Sort by name ascending then provider id descending order"
      ["name" "-provider"]
      [service3 service1 service4 service2]

      "Sort by name then provider id descending order"
      ["-name" "-provider"]
      [service2 service1 service4 service3]

      "Sort by service type"
      ["type"]
      [service4 service2 service3 service1]

      "Sort by service type in descending order"
      ["-type"]
      [service2 service3 service1 service4]

      "Sort by type name ascending then provider id descending order"
      ["type" "name" "-provider"]
      [service4 service3 service1 service2])))

(deftest service-search-in-umm-json-format-test
  (testing "service search result in UMM JSON format does not have associated collections"
    (let [token (e/login (s/context) "user1")
          coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection) {:format :umm-json :token token})
          ;; create services
          service1-concept (services/make-service-concept {:native-id "svc1"
                                                           :Name "service1"})
          service1 (services/ingest-service service1-concept)
          service2-concept (services/make-service-concept {:native-id "svc2"
                                                           :Name "service2"})
          service2 (services/ingest-service service2-concept)
          service1-concept-id (:concept-id service1)
          service1-assoc-colls [{:concept-id (:concept-id coll1)}]
          associations {:associations {:collections [(:concept-id coll1)]}
                        :association-details {:collections [{:concept-id (:concept-id coll1)}]}}
          expected-service1 (merge service1-concept service1 associations)
          expected-service2 (merge service2-concept service2)]
      ;; index the collections so that they can be found during service association
      (index/wait-until-indexed)
      ;; create service associations
      (au/associate-by-concept-ids token service1-concept-id service1-assoc-colls)
      (index/wait-until-indexed)

      ;; verify service search UMM JSON response is correct.
      (are3 [umm-version options]
        (data-umm-json/assert-service-umm-jsons-match
         umm-version [expected-service1 expected-service2]
         (search/find-concepts-umm-json :service {} options))

        "without specifying UMM JSON version"
        umm-version/current-service-version
        {}

        "explicit UMM JSON version through accept header"
        umm-version/current-service-version
        {:accept (mime-types/with-version mime-types/umm-json umm-version/current-service-version)}

        "explicit UMM JSON version through suffix"
        "1.5.0"
        {:url-extension "umm_json_v1_5_0"})))

  (testing "Searching with non-existent UMM JSON version"
    (are3 [options]
      (let [{:keys [status errors]} (search/find-concepts-umm-json :service {} options)]
        (is (= 400 status))
        (is (= ["The mime type [application/vnd.nasa.cmr.umm_results+json] with version [0.1] is not supported for services."]
               errors)))

      "explicit UMM JSON version through suffix"
      {:url-extension "umm_json_v0_1"}

      "explicit UMM JSON version through accept header"
      {:accept (mime-types/with-version mime-types/umm-json "0.1")})))
