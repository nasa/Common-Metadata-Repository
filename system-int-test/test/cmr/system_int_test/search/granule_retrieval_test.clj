
(ns cmr.system-int-test.search.granule-retrieval-test
  "Integration test for granule retrieval with cmr-concept-id"
  (:require
    [clojure.test :refer :all]
    [cmr.common.mime-types :as mt]
    [cmr.common.util :refer [are3]]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]
    [cmr.umm-spec.legacy :as legacy]
    [cmr.umm-spec.versioning :as versioning]
    [cmr.umm.echo10.granule :as g]
    [cmr.umm.umm-granule :as umm-g]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest retrieve-granule-by-cmr-concept-id
  (let [coll1 (d/ingest-umm-spec-collection "PROV1"
                                            (data-umm-c/collection
                                             {:Projects (data-umm-cmn/projects "ABC" "KLM" "XYZ")}))
        gran1 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection
                         coll1 (:concept-id coll1) {:granule-ur "Granule1"
                                                    :project-refs ["ABC"]}))
        gran1 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection
                         coll1 (:concept-id coll1) {:granule-ur "Granule1"
                                                    :project-refs ["KLM"]}))
        umm-gran (dg/granule-with-umm-spec-collection
                  coll1 (:concept-id coll1) {:granule-ur "Granule1"
                                             :project-refs ["XYZ"]})
        gran1 (d/ingest "PROV1" umm-gran)
        del-gran (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1)))
        umm-gran (-> umm-gran
                     (assoc-in [:collection-ref :short-name] nil)
                     (assoc-in [:collection-ref :version-id] nil)
                     (assoc-in [:collection-ref :entry-id] nil)
                     (dissoc :collection-concept-id))]
    (ingest/delete-concept (d/item->concept del-gran :echo10))
    (index/wait-until-indexed)
    (testing "retrieval by granule cmr-concept-id returns the latest revision."
      (let [response (search/retrieve-concept (:concept-id gran1))
            parsed-granule (g/parse-granule (:body response))]
        (is (search/mime-type-matches-response? response mt/echo10))
        (is (= umm-gran parsed-granule))))
    (testing "retrieval of a deleted granule results in a 404"
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                     (search/retrieve-concept
                                      (:concept-id del-gran) nil {:throw-exceptions true}))]
        (is (= 404 status))
        (is (= [(format "Concept with concept-id [%s] could not be found." (:concept-id del-gran))] errors))))
    (testing "retrieval by granule cmr-concept-id, not found."
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                     (search/retrieve-concept
                                      "G1111-PROV1" nil {:throw-exceptions true}))]
        (is (= 404 status))
        (is (= ["Concept with concept-id [G1111-PROV1] could not be found."] errors))))
    (testing "retrieval by un-supported accept header."
      (let [{:keys [status errors]} (search/get-search-failure-xml-data
                                     (search/retrieve-concept
                                      (:concept-id gran1)
                                      (:revision-id gran1)
                                      {:accept "application/atom"
                                       :throw-exceptions true}))]
        (is (= 400 status))
        (is (= ["The mime types specified in the accept header [application/atom] are not supported."]
               errors))))))

(deftest retrieve-umm-g-granule-test
  (let [collection (d/ingest-umm-spec-collection
                    "PROV1" (data-umm-c/collection {:EntryTitle "correct"
                                                    :ShortName "S1"
                                                    :Version "V1"}))
        granule (-> (dg/granule-with-umm-spec-collection
                     collection
                     (:concept-id collection)
                     {:granule-ur "Gran1"
                      :collection-ref (umm-g/map->CollectionRef {:entry-title "correct"})}))
        echo10-gran (d/item->concept granule :echo10)
        smap-gran (d/item->concept granule :iso-smap)
        umm-g-gran (d/item->concept granule :umm-json)
        {echo10-gran-concept-id :concept-id
         echo10-gran-revision-id :revision-id} (ingest/ingest-concept echo10-gran)
        {smap-gran-concept-id :concept-id
         smap-gran-revision-id :revision-id} (ingest/ingest-concept smap-gran)
        {umm-g-gran-concept-id :concept-id
         umm-g-gran-revision-id :revision-id} (ingest/ingest-concept umm-g-gran)]
    (index/wait-until-indexed)
    (testing "retrieve UMM-G granule in UMM JSON format in latest UMM version"
      (are3
        [accept-format]
        (let [response (search/retrieve-concept
                        umm-g-gran-concept-id umm-g-gran-revision-id {:accept accept-format})
              response-format (-> response
                                  (get-in [:headers :Content-Type])
                                  mt/mime-type->format)]
          (is (= {:format :umm-json :version versioning/current-granule-version}
                 response-format))
          (is (= (:metadata umm-g-gran) (:body response))))

        "without specifying accept format"
        nil

        "specifying umm json accept format without version"
        "application/vnd.nasa.cmr.umm+json"

        "specifying umm json accept format with version"
        (str "application/vnd.nasa.cmr.umm+json;version=" versioning/current-granule-version)))

    (testing "retrieve UMM-G granule in UMM JSON format in specific UMM version"
      (let [accept-format "application/vnd.nasa.cmr.umm+json;version=1.4"
            response (search/retrieve-concept
                      umm-g-gran-concept-id umm-g-gran-revision-id {:accept accept-format})
            response-format (-> response
                                (get-in [:headers :Content-Type])
                                mt/mime-type->format)]
        (is (= 200 (:status response)))
        (is (= {:format :umm-json :version "1.4"}
               response-format))))

    (testing "retrieve UMM-G granule in ECHO10 format"
      (let [response (search/retrieve-concept
                      umm-g-gran-concept-id umm-g-gran-revision-id {:accept mt/echo10})
            response-format (-> response
                                (get-in [:headers :Content-Type])
                                mt/mime-type->format)]
        (is (= :echo10 response-format))
        (is (= (:metadata echo10-gran) (:body response)))))

    (testing "retrieve UMM-G granule in ISO MENS format"
      (let [response (search/retrieve-concept
                      umm-g-gran-concept-id umm-g-gran-revision-id {:accept mt/iso19115})
            response-format (-> response
                                (get-in [:headers :Content-Type])
                                mt/mime-type->format)]
        (is (= :iso19115 response-format))
        ;; Just verify that the ISO granule element name exists in the response
        (is (re-matches #"(?s).*<gmi:MI_Metadata.*" (:body response)))))

    (testing "retrieve ECHO10 granule in UMM JSON format"
      (let [response (search/retrieve-concept
                      echo10-gran-concept-id
                      echo10-gran-revision-id
                      {:accept "application/vnd.nasa.cmr.umm+json"})
            response-format (-> response
                                (get-in [:headers :Content-Type])
                                mt/mime-type->format)]
        (is (= {:format :umm-json :version versioning/current-granule-version}
               response-format))
        (is (= (:metadata umm-g-gran) (:body response)))))

    (testing "retrieve ISO SMAP granule in UMM JSON format"
      (let [response (search/retrieve-concept
                      smap-gran-concept-id
                      smap-gran-revision-id
                      {:accept "application/vnd.nasa.cmr.umm+json"})
            response-format (-> response
                                (get-in [:headers :Content-Type])
                                mt/mime-type->format)]
        (is (= {:format :umm-json :version versioning/current-granule-version}
               response-format))
        (is (= (:metadata umm-g-gran) (:body response)))))))
