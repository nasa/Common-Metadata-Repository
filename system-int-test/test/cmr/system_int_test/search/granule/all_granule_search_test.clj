(ns cmr.system-int-test.search.granule.all-granule-search-test
  (:require
   [clojure.test :refer :all]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.util :as util :refer [are3]]
   [cmr.search.api.concepts-search :as concepts-search]
   [cmr.system-int-test.utils.search-util :as search]))

;; Tests all granule search when allow-all-granule-params-flag is false
;; Existing tests should cover the case when the flag is set to true.
(deftest allow-all-granule-params-flag-false-search-test
  (let [saved-flag-value   (concepts-search/allow-all-granule-params-flag)
        saved-header-value (concepts-search/allow-all-gran-header)]
    (try
      ;; set state for the test
      (side/eval-form `(concepts-search/set-allow-all-granule-params-flag! false))
      (side/eval-form `(concepts-search/set-allow-all-gran-header! "ALL_GRANS_2172"))

      (let [err-msg "The CMR does not allow querying across granules in all collections. To help optimize your search, you should limit your query using conditions that identify one or more collections, such as provider, provider_id, concept_id, collection_concept_id, short_name, version or entry_title. For any questions please contact cmr-support@nasa.gov."
            invalid-concept-id-err-msg "Invalid concept_id [S1234-PROV1]. For granule queries concept_id must be either a granule or collection concept ID."
            invalid-concept-id-err-msg-in-array "Invalid concept_id [[\"\" \"S1234-PROV1\"]]. For granule queries concept_id must be either a granule or collection concept ID."
            invalid-number-id-err-msg "Invalid concept_id [1]. For granule queries concept_id must be either a granule or collection concept ID."]

        ;; -----------------
        ;; Header-based tests
        ;; -----------------
        (are3 [expected header]
              (is (= expected
                     (:errors (search/find-refs :granule {} {:headers header}))))

              "headers good"
              nil
              {"client-id" "testing", "ALL_GRANS_2172" true}

              "headers good case-insensitive"
              nil
              {"client-id" "testing", "alL_GrAnS_2172" true}

              "header wrong flag"
              [err-msg]
              {"client-id" "testing", "ALL_GRANS_2172" false}

              "header missing flag"
              [err-msg]
              {"client-id" "testing"}

              "header missing client-id"
              [err-msg]
              {"ALL_GRANS_2172" true}

              "header wrong flag no client-id"
              [err-msg]
              {"ALL_GRANS_2172" false}

              "header missing all"
              [err-msg]
              {})

        ;; -----------------
        ;; Param-based tests
        ;; -----------------
        (are3 [expected params]
              (is (= expected
                     (:errors (search/find-refs :granule params))))

              "provider constraint"
              nil
              {:provider "PROV1"}

              "provider_id constraint"
              nil
              {:provider_id "PROV1"}

              "collection concept_id"
              nil
              {:concept_id "C1234-PROV1"}

              "granule concept_id"
              nil
              {:concept_id "G1234-PROV1"}

              "granule concept_id alias"
              nil
              {:echo_granule_id "G1234-PROV1"}

              "collection_concept_id"
              nil {:collection_concept_id "C1234-PROV1"}

              "collection_concept_id alias"
              nil
              {:echo_collection_id "C1234-PROV1"}

              "empty array"
              [err-msg]
              {:concept_id ["" ""]}

              "array with service id"
              [invalid-concept-id-err-msg-in-array]
              {:concept_id ["" "S1234-PROV1"]}

              "empty concept_id"
              [err-msg]
              {:concept_id ""}

              "number concept_id"
              [invalid-number-id-err-msg]
              {:concept_id 1}

              "invalid concept_id"
              [invalid-concept-id-err-msg]
              {:concept_id "S1234-PROV1"}

              "invalid collection_concept_id"
              [invalid-concept-id-err-msg]
              {:collection_concept_id "S1234-PROV1"}

              "invalid collection_concept_id alias"
              [invalid-concept-id-err-msg]
              {:echo_collection_id "S1234-PROV1"}

              "invalid granule concept_id alias"
              [invalid-concept-id-err-msg]
              {:echo_granule_id "S1234-PROV1"}

              "short_name"
              nil
              {:short_name "short name"}

              "shortName alias"
              nil
              {:shortName "short name"}

              "version"
              nil
              {:version "1.0"}

              "entry_title"
              nil
              {:entry_title "testing"}

              "dataset_id alias"
              nil
              {:dataset_id "testing"}

              "bounding-box without collection"
              [err-msg]
              {:bounding-box "-10,-5,10,5"}

              "temporal without collection"
              [err-msg]
              {"temporal[]" "2010-12-12T12:00:00Z,"}

              "page num/size without collection"
              [err-msg]
              {"page_num" 2 "page_size" 5}))
      (finally
        ;; always restore original state
        (side/eval-form `(concepts-search/set-allow-all-granule-params-flag! ~saved-flag-value))
        (side/eval-form `(concepts-search/set-allow-all-gran-header! ~saved-header-value))))))
