(ns cmr.ingest.api.collections-test
  "Unit tests for cmr.ingest.api.collections, starting with
   get-validation-options. More tests for other functions in this
   namespace can be added here over time."
  (:require [clojure.test :refer [deftest testing is]]
            [cmr.ingest.api.collections :as v :refer [get-validation-options
                                                      VALIDATE_KEYWORDS_HEADER
                                                      ENABLE_UMM_C_VALIDATION_HEADER
                                                      TESTING_EXISTING_ERRORS_HEADER
                                                      SEND_KMS_METADATA_FIXER_HEADER]]
            [cmr.ingest.config :as ingest-config]))

;; ---------------------------------------------------------------------
;; :validate-keywords? — default-true-enabled? = true
;;   (only an explicit "false" header value turns validation off)
;; ---------------------------------------------------------------------
(deftest validate-keywords-default-true-enabled-true
  (with-redefs [v/validate-keywords-default-true-enabled? true]
    (testing "header explicitly \"false\" -> false"
      (is (= false (:validate-keywords?
                    (get-validation-options {VALIDATE_KEYWORDS_HEADER "false"} "PROV1")))))

    (testing "header explicitly \"true\" -> true"
      (is (= true (:validate-keywords?
                   (get-validation-options {VALIDATE_KEYWORDS_HEADER "true"} "PROV1")))))

    (testing "header missing -> true (defaults on)"
      (is (= true (:validate-keywords?
                   (get-validation-options {} "PROV1")))))

    (testing "header present but garbage value -> true (defaults on)"
      (is (= true (:validate-keywords?
                   (get-validation-options {VALIDATE_KEYWORDS_HEADER "nope"} "PROV1")))))))

;; ---------------------------------------------------------------------
;; :validate-keywords? — default-true-enabled? = false
;;   (must explicitly opt in with "true")
;; ---------------------------------------------------------------------
(deftest validate-keywords-default-true-enabled-false
  (with-redefs [v/validate-keywords-default-true-enabled? false]
    (testing "header explicitly \"true\" -> true"
      (is (= true (:validate-keywords?
                   (get-validation-options {VALIDATE_KEYWORDS_HEADER "true"} "PROV1")))))

    (testing "header explicitly \"false\" -> false"
      (is (= false (:validate-keywords?
                    (get-validation-options {VALIDATE_KEYWORDS_HEADER "false"} "PROV1")))))

    (testing "header missing -> false (defaults off)"
      (is (= false (:validate-keywords?
                    (get-validation-options {} "PROV1")))))

    (testing "header present but garbage value -> false (defaults off)"
      (is (= false (:validate-keywords?
                    (get-validation-options {VALIDATE_KEYWORDS_HEADER "nope"} "PROV1")))))))

;; ---------------------------------------------------------------------
;; :validate-keywords? — provider is in keyword-enforced-providers,
;;   SEND_KMS_METADATA_FIXER_HEADER absent -> enforced true
;; Since we cache this value we need to redef the whole cache on these tests
;; ---------------------------------------------------------------------
(deftest validate-keywords-enforced-provider-header-absent-test
  (with-redefs [ingest-config/keyword-enforced-providers (constantly ["PROV1" "PROV2"])]

    (testing "enforced provider + fixer header absent + keywords header explicitly \"false\" -> still true"
      (with-redefs [v/enforced-providers-cache (delay #{"PROV1" "PROV2"})]
        (is (= true (:validate-keywords?
                     (get-validation-options {VALIDATE_KEYWORDS_HEADER "false"} "PROV1"))))))

    (testing "enforced provider + fixer header absent, default-true-enabled? false -> still true"
      (with-redefs [v/enforced-providers-cache (delay #{"PROV1" "PROV2"})]
        (is (= true (:validate-keywords?
                     (get-validation-options {VALIDATE_KEYWORDS_HEADER "false"} "PROV1"))))))

    (testing "enforced provider + no headers at all -> still true"
      (with-redefs [v/enforced-providers-cache (delay #{"PROV1" "PROV2"})]
        (is (= true (:validate-keywords?
                     (get-validation-options {} "PROV1"))))))

    (testing "non-enforced provider, fixer header absent -> falls back to normal header logic"
      (with-redefs [v/validate-keywords-default-true-enabled? true]
        (is (= false (:validate-keywords?
                      (get-validation-options {VALIDATE_KEYWORDS_HEADER "false"} "PROV3"))))))))

;; ---------------------------------------------------------------------
;; :validate-keywords? — provider is in keyword-enforced-providers,
;;   SEND_KMS_METADATA_FIXER_HEADER present -> enforced list is bypassed,
;;   normal header logic applies regardless of its value
;; ---------------------------------------------------------------------
(deftest validate-keywords-enforced-provider-header-present-test
  (with-redefs [ingest-config/keyword-enforced-providers (constantly ["PROV1" "PROV2"])]

    (testing "enforced provider + fixer header present (\"true\") + keywords header \"false\" -> false (enforcement bypassed)"
      (with-redefs [v/validate-keywords-default-true-enabled? true]
        (is (= false (:validate-keywords?
                      (get-validation-options {VALIDATE_KEYWORDS_HEADER "false"
                                                SEND_KMS_METADATA_FIXER_HEADER "true"}
                                               "PROV1"))))))

    (testing "enforced provider + fixer header present (\"false\") + keywords header \"false\" -> false (enforcement bypassed)"
      (with-redefs [v/validate-keywords-default-true-enabled? true]
        (is (= false (:validate-keywords?
                      (get-validation-options {VALIDATE_KEYWORDS_HEADER "false"
                                                SEND_KMS_METADATA_FIXER_HEADER "false"}
                                               "PROV1"))))))

    (testing "enforced provider + fixer header present + no keywords header, default-true-enabled? true -> true (normal default, not enforcement)"
      (with-redefs [v/validate-keywords-default-true-enabled? true]
        (is (= true (:validate-keywords?
                     (get-validation-options {SEND_KMS_METADATA_FIXER_HEADER "true"} "PROV1"))))))

    (testing "enforced provider + fixer header present + no keywords header, default-true-enabled? false -> false (normal default, not enforcement)"
      (with-redefs [v/validate-keywords-default-true-enabled? false]
        (is (= false (:validate-keywords?
                      (get-validation-options {SEND_KMS_METADATA_FIXER_HEADER "true"} "PROV1"))))))))

;; ---------------------------------------------------------------------
;; :validate-keywords? — empty keyword-enforced-providers config
;; ---------------------------------------------------------------------
(deftest validate-keywords-no-enforced-providers-test
  (with-redefs [ingest-config/keyword-enforced-providers (constantly [])
                v/validate-keywords-default-true-enabled? true]
    (testing "no enforced providers configured -> normal header logic applies"
      (is (= false (:validate-keywords?
                    (get-validation-options {VALIDATE_KEYWORDS_HEADER "false"} "PROV1")))))))

;; ---------------------------------------------------------------------
;; :validate-umm?  — defaults to false, only "true" turns it on
;; ---------------------------------------------------------------------
(deftest validate-umm-test
  (testing "header \"true\" -> true"
    (is (= true (:validate-umm?
                 (get-validation-options {ENABLE_UMM_C_VALIDATION_HEADER "true"} "PROV1")))))

  (testing "header missing -> false"
    (is (= false (:validate-umm? (get-validation-options {} "PROV1")))))

  (testing "header \"false\" -> false"
    (is (= false (:validate-umm?
                  (get-validation-options {ENABLE_UMM_C_VALIDATION_HEADER "false"} "PROV1")))))

  (testing "header garbage value -> false"
    (is (= false (:validate-umm?
                  (get-validation-options {ENABLE_UMM_C_VALIDATION_HEADER "yes"} "PROV1"))))))

;; ---------------------------------------------------------------------
;; :test-existing-errors? — defaults to false, only "true" turns it on
;; ---------------------------------------------------------------------
(deftest test-existing-errors-test
  (testing "header \"true\" -> true"
    (is (= true (:test-existing-errors?
                 (get-validation-options {TESTING_EXISTING_ERRORS_HEADER "true"} "PROV1")))))

  (testing "header missing -> false"
    (is (= false (:test-existing-errors? (get-validation-options {} "PROV1")))))

  (testing "header \"false\" -> false"
    (is (= false (:test-existing-errors?
                  (get-validation-options {TESTING_EXISTING_ERRORS_HEADER "false"} "PROV1")))))

  (testing "header garbage value -> false"
    (is (= false (:test-existing-errors?
                  (get-validation-options {TESTING_EXISTING_ERRORS_HEADER "yes"} "PROV1"))))))

;; ---------------------------------------------------------------------
;; :send-metadata-fixer? — defaults to true, only explicit "false" turns it off
;; ---------------------------------------------------------------------
(deftest send-metadata-fixer-test
  (testing "header missing -> true (defaults on)"
    (is (= true (:send-metadata-fixer? (get-validation-options {} "PROV1")))))

  (testing "header \"true\" -> true"
    (is (= true (:send-metadata-fixer?
                 (get-validation-options {SEND_KMS_METADATA_FIXER_HEADER "true"} "PROV1")))))

  (testing "header \"false\" -> false"
    (is (= false (:send-metadata-fixer?
                  (get-validation-options {SEND_KMS_METADATA_FIXER_HEADER "false"} "PROV1")))))

  (testing "header garbage value -> true (anything other than \"false\" is on)"
    (is (= true (:send-metadata-fixer?
                 (get-validation-options {SEND_KMS_METADATA_FIXER_HEADER "nope"} "PROV1"))))))

;; ---------------------------------------------------------------------
;; Combination / full-map tests
;; ---------------------------------------------------------------------
(deftest get-validation-options-combined-test
  (testing "empty headers map, default-true-enabled? true -> keywords on, rest off/on defaults"
    (with-redefs [v/validate-keywords-default-true-enabled? true]
      (is (= {:validate-keywords? true
              :validate-umm? false
              :test-existing-errors? false
              :send-metadata-fixer? true}
             (get-validation-options {} "PROV1")))))

  (testing "empty headers map, default-true-enabled? false -> keywords off, rest off/on defaults"
    (with-redefs [v/validate-keywords-default-true-enabled? false]
      (is (= {:validate-keywords? false
              :validate-umm? false
              :test-existing-errors? false
              :send-metadata-fixer? true}
             (get-validation-options {} "PROV1")))))

  (testing "all headers explicitly set"
    (with-redefs [v/validate-keywords-default-true-enabled? true]
      (is (= {:validate-keywords? false
              :validate-umm? true
              :test-existing-errors? true
              :send-metadata-fixer? false}
             (get-validation-options
              {VALIDATE_KEYWORDS_HEADER "false"
               ENABLE_UMM_C_VALIDATION_HEADER "true"
               TESTING_EXISTING_ERRORS_HEADER "true"
               SEND_KMS_METADATA_FIXER_HEADER "false"}
              "PROV1"))))))
