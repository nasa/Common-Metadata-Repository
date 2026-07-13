(ns cmr.ingest.test.validation.business-rule-validation
  (:require
   [clojure.test :refer :all]
   [cmr.ingest.validation.additional-attribute-validation :as aa]
   [cmr.ingest.validation.business-rule-validation :as bv]
   [cmr.ingest.validation.instrument-validation :as instrument-validation]
   [cmr.ingest.validation.platform-validation :as platform-validation]
   [cmr.ingest.validation.project-validation :as pv]
   [cmr.ingest.validation.spatial-validation :as sv]
   [cmr.ingest.validation.temporal-validation :as tv]
   [cmr.ingest.validation.tiling-validation :as tiling-validation]))

(def granule-test-concept {:concept-type :granule})
(def variable-test-concept {:concept-type :variable})

(deftest business-rules-for-granules
  (is (= [bv/delete-time-validation]
         (bv/business-rule-validations
          (:concept-type granule-test-concept)))))

(deftest business-rules-for-variables
  (is (= []
         (bv/business-rule-validations
          (:concept-type variable-test-concept)))))

;; Default case: cmr.ingest.config/enforce-granule-collection-consistency is true,
;; so the instrument and platform searches are included.
(deftest collection-update-searches-with-consistency-enforcement-enabled
  (is (= [aa/additional-attribute-searches
          pv/deleted-project-searches
          instrument-validation/deleted-parent-instrument-searches
          instrument-validation/deleted-child-instrument-searches
          platform-validation/deleted-platform-searches
          tiling-validation/deleted-tiling-searches
          tv/out-of-range-temporal-searches
          sv/spatial-param-change-searches]
         (bv/collection-update-searches))))

;; NOTE: We can't flip enforce-granule-collection-consistency to false in CI yet
;; (it's read from env-driven config), so this is left commented out until we
;; have a way to override it there. Locally, this passes by stubbing the config
;; function directly with with-redefs:
;;
;; (deftest collection-update-searches-with-consistency-enforcement-disabled
;;   (with-redefs [cfg/enforce-granule-collection-consistency (constantly false)]
;;     (is (= [aa/additional-attribute-searches
;;             pv/deleted-project-searches
;;             tiling-validation/deleted-tiling-searches
;;             tv/out-of-range-temporal-searches
;;             sv/spatial-param-change-searches]
;;            (bv/collection-update-searches)))))