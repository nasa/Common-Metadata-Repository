(ns cmr.system-int-test.search.all-granule-search-test
  (:require
   [clojure.test :refer :all]
   [cmr.common-app.test.side-api :as side]
   [cmr.search.api.concepts-search :as concepts-search]
   [cmr.system-int-test.utils.search-util :as search]))

;; Tests all granule search when allow-all-granule-params-flag is false
;; Existing tests should cover the case when the flag is set to true.
(deftest allow-all-granule-params-flag-false-search-test
  (let [saved-flag-value (concepts-search/allow-all-granule-params-flag)
        saved-header-value (concepts-search/allow-all-gran-header)
        _ (side/eval-form `(concepts-search/set-allow-all-granule-params-flag! false))
        _ (side/eval-form `(concepts-search/set-allow-all-gran-header! "ALL_GRANS_2172"))
        header1 {"client-id" "testing", "ALL_GRANS_2172" true}
        header1-insensitive {"client-id" "testing", "alL_GrAnS_2172" true}
        header2 {"client-id" "testing", "ALL_GRANS_2172" false}
        header3 {"client-id" "testing"}
        header4 {"ALL_GRANS_2172" true}
        header5 {"ALL_GRANS_2172" false}
        header6 {}
        params1 {:provider "PROV1"}
        params2 {:provider_id "PROV1"}
        params3 {:concept_id "G1234-PROV1"}
        params3-alias {:echo_granule_id "G1234-PROV1"}
        params3-collection {:concept_id "C1234-PROV1"}
        params3-service {:concept_id "S1234-PROV1"}
        params3-variable {:concept_id "V1234-PROV1"}
        params3-empty-array {:concept_id ["" ""]}
        params3-array-with-service {:concept_id ["" "S1234-PROV1"]}
        params3-empty {:concept_id ""}
        params3-number {:concept_id 1}
        params4 {:collection_concept_id "C1234-PROV1"}
        params4-alias {:echo_collection_id "C1234-PROV1"}
        params5 {:short_name "short name"}
        params5-alias {:shortName "short name"}
        params6 {:version "1.0"}
        params7 {:entry_title "testing"}
        params7-alias {:dataset_id "testing"}
        params8 {:bounding-box "-10,-5,10,5"}
        params9 {"temporal[]" "2010-12-12T12:00:00Z,"}
        params10 {"page_num" 2 "page_size" 5}
        ;; With allow-all-granule-params-flag being set to false, all granule query is allowed
        ;; only when client-id is present and allow-all-gran header is set to true.
        result1 (search/find-refs :granule {} {:headers header1})
        ;; allow-all-gran header is case insensitive
        result1-insensitive (search/find-refs :granule {} {:headers header1-insensitive})

        ;; Without the proper headers and collection constriants, these should be rejected
        result2 (search/find-refs :granule {} {:headers header2})
        result3 (search/find-refs :granule {} {:headers header3})
        result4 (search/find-refs :granule {} {:headers header4})
        result5 (search/find-refs :granule {} {:headers header5})
        result6 (search/find-refs :granule {} {:headers header6})
        ;; Search with collection constraints should be allowed.
        result7 (search/find-refs :granule params1)
        result8 (search/find-refs :granule params2)
        result9 (search/find-refs :granule params3)
        result9-alias (search/find-refs :granule params3-alias)
        result9-collection (search/find-refs :granule params3-collection)
        ;; except for when service/variable concept ids are passed in.
        ;; we only support granule and collection concept ids for the granule search.
        result9-service (search/find-refs :granule params3-service)
        result9-variable (search/find-refs :granule params3-variable)
        result9-empty-array (search/find-refs :granule params3-empty-array)
        result9-array-with-service (search/find-refs :granule params3-array-with-service)
        result9-empty (search/find-refs :granule params3-empty)
        result9-number (search/find-refs :granule params3-number)
        result10 (search/find-refs :granule params4)
        result10-alias (search/find-refs :granule params4-alias)
        result11 (search/find-refs :granule params5)
        result11-alias (search/find-refs :granule params5-alias)
        result12 (search/find-refs :granule params6)
        result13 (search/find-refs :granule params7)
        result13-alias (search/find-refs :granule params7-alias)
        ;; Search without collection constraints, but with other spatial, temporal page_num, page_size are rejected.
        result14 (search/find-refs :granule params8)
        result15 (search/find-refs :granule params9)
        result16 (search/find-refs :granule params10)
        err-msg "The CMR does not allow querying across granules in all collections. To help optimize your search, you should limit your query using conditions that identify one or more collections, such as provider, provider_id, concept_id, collection_concept_id, short_name, version or entry_title. For any questions please contact cmr-support@earthdata.nasa.gov."
        err-msg-illegal-service-id "Invalid concept_id [S1234-PROV1]. For granule queries concept_id must be either a granule or collection concept ID."
        err-msg-illegal-variable-id "Invalid concept_id [V1234-PROV1]. For granule queries concept_id must be either a granule or collection concept ID."
        err-msg-illegal-service-id-in-array "Invalid concept_id [[\"\" \"S1234-PROV1\"]]. For granule queries concept_id must be either a granule or collection concept ID."
        err-msg-illegal-number-id "Invalid concept_id [1]. For granule queries concept_id must be either a granule or collection concept ID."
        _ (side/eval-form `(concepts-search/set-allow-all-granule-params-flag! ~saved-flag-value))
        _ (side/eval-form `(concepts-search/set-allow-all-gran-header! ~saved-header-value))]
    (is (= nil
           (:errors result1)))
    (is (= nil
           (:errors result1-insensitive)))
    (is (= [err-msg]
           (:errors result2)))
    (is (= [err-msg]
           (:errors result3)))
    (is (= [err-msg]
           (:errors result4)))
    (is (= [err-msg]
           (:errors result5)))
    (is (= [err-msg]
           (:errors result6)))
    (is (= nil
           (:errors result7)))
    (is (= nil
           (:errors result8)))
    (is (= nil
           (:errors result9)))
    (is (= nil
           (:errors result9-alias)))
    (is (= nil
           (:errors result9-collection)))
    (is (= [err-msg-illegal-service-id]
           (:errors result9-service)))
    (is (= [err-msg-illegal-variable-id]
           (:errors result9-variable)))
    (is (= [err-msg]
           (:errors result9-empty-array)))
    (is (= [err-msg-illegal-service-id-in-array]
           (:errors result9-array-with-service)))
    (is (= [err-msg]
           (:errors result9-empty)))
    (is (= [err-msg-illegal-number-id]
           (:errors result9-number)))
    (is (= nil
           (:errors result10)))
    (is (= nil
           (:errors result10-alias)))
    (is (= nil
           (:errors result11)))
    (is (= nil
           (:errors result11-alias)))
    (is (= nil
           (:errors result12)))
    (is (= nil
           (:errors result13)))
    (is (= nil
           (:errors result13-alias)))
    (is (= [err-msg]
           (:errors result14)))
    (is (= [err-msg]
           (:errors result15)))
    (is (= [err-msg]
           (:errors result16)))))
