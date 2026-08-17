(ns cmr.ingest.api.collections-test
  "Unit tests for cmr.ingest.api.collections, starting with
   get-validation-options. More tests for other functions in this
   namespace can be added here over time."
  (:require [clojure.test :refer [deftest testing is]]
            [cmr.ingest.api.collections :as v :refer [get-validation-options
                                                      VALIDATE_KEYWORDS_HEADER
                                                      ENABLE_UMM_C_VALIDATION_HEADER
                                                      TESTING_EXISTING_ERRORS_HEADER
                                                      SEND_KMS_METADATA_FIXER_HEADER]]))

;; ---------------------------------------------------------------------
;; :validate-keywords? — default-true-enabled? = true
;;   (only an explicit "false" header value turns validation off)
;; ---------------------------------------------------------------------
(deftest validate-keywords-default-true-enabled-true
  (with-redefs [v/validate-keywords-default-true-enabled? true]
    (testing "header explicitly \"false\" -> false"
      (is (= false (:validate-keywords?
                    (get-validation-options {VALIDATE_KEYWORDS_HEADER "false"})))))

    (testing "header explicitly \"true\" -> true"
      (is (= true (:validate-keywords?
                   (get-validation-options {VALIDATE_KEYWORDS_HEADER "true"})))))

    (testing "header missing -> true (defaults on)"
      (is (= true (:validate-keywords?
                   (get-validation-options {})))))

    (testing "header present but garbage value -> true (defaults on)"
      (is (= true (:validate-keywords?
                   (get-validation-options {VALIDATE_KEYWORDS_HEADER "nope"})))))))

;; ---------------------------------------------------------------------
;; :validate-keywords? — default-true-enabled? = false
;;   (must explicitly opt in with "true")
;; ---------------------------------------------------------------------
(deftest validate-keywords-default-true-enabled-false
  (with-redefs [v/validate-keywords-default-true-enabled? false]
    (testing "header explicitly \"true\" -> true"
      (is (= true (:validate-keywords?
                   (get-validation-options {VALIDATE_KEYWORDS_HEADER "true"})))))

    (testing "header explicitly \"false\" -> false"
      (is (= false (:validate-keywords?
                    (get-validation-options {VALIDATE_KEYWORDS_HEADER "false"})))))

    (testing "header missing -> false (defaults off)"
      (is (= false (:validate-keywords?
                    (get-validation-options {})))))

    (testing "header present but garbage value -> false (defaults off)"
      (is (= false (:validate-keywords?
                    (get-validation-options {VALIDATE_KEYWORDS_HEADER "nope"})))))))

;; ---------------------------------------------------------------------
;; :validate-umm?  — defaults to false, only "true" turns it on
;; ---------------------------------------------------------------------
(deftest validate-umm-test
  (testing "header \"true\" -> true"
    (is (= true (:validate-umm?
                 (get-validation-options {ENABLE_UMM_C_VALIDATION_HEADER "true"})))))

  (testing "header missing -> false"
    (is (= false (:validate-umm? (get-validation-options {})))))

  (testing "header \"false\" -> false"
    (is (= false (:validate-umm?
                  (get-validation-options {ENABLE_UMM_C_VALIDATION_HEADER "false"})))))

  (testing "header garbage value -> false"
    (is (= false (:validate-umm?
                  (get-validation-options {ENABLE_UMM_C_VALIDATION_HEADER "yes"}))))))

;; ---------------------------------------------------------------------
;; :test-existing-errors? — defaults to false, only "true" turns it on
;; ---------------------------------------------------------------------
(deftest test-existing-errors-test
  (testing "header \"true\" -> true"
    (is (= true (:test-existing-errors?
                 (get-validation-options {TESTING_EXISTING_ERRORS_HEADER "true"})))))

  (testing "header missing -> false"
    (is (= false (:test-existing-errors? (get-validation-options {})))))

  (testing "header \"false\" -> false"
    (is (= false (:test-existing-errors?
                  (get-validation-options {TESTING_EXISTING_ERRORS_HEADER "false"})))))

  (testing "header garbage value -> false"
    (is (= false (:test-existing-errors?
                  (get-validation-options {TESTING_EXISTING_ERRORS_HEADER "yes"}))))))

;; ---------------------------------------------------------------------
;; :send-metadata-fixer? — defaults to true, only explicit "false" turns it off
;; ---------------------------------------------------------------------
(deftest send-metadata-fixer-test
  (testing "header missing -> true (defaults on)"
    (is (= true (:send-metadata-fixer? (get-validation-options {})))))

  (testing "header \"true\" -> true"
    (is (= true (:send-metadata-fixer?
                 (get-validation-options {SEND_KMS_METADATA_FIXER_HEADER "true"})))))

  (testing "header \"false\" -> false"
    (is (= false (:send-metadata-fixer?
                  (get-validation-options {SEND_KMS_METADATA_FIXER_HEADER "false"})))))

  (testing "header garbage value -> true (anything other than \"false\" is on)"
    (is (= true (:send-metadata-fixer?
                 (get-validation-options {SEND_KMS_METADATA_FIXER_HEADER "nope"}))))))

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
             (get-validation-options {})))))

  (testing "empty headers map, default-true-enabled? false -> keywords off, rest off/on defaults"
    (with-redefs [v/validate-keywords-default-true-enabled? false]
      (is (= {:validate-keywords? false
              :validate-umm? false
              :test-existing-errors? false
              :send-metadata-fixer? true}
             (get-validation-options {})))))

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
               SEND_KMS_METADATA_FIXER_HEADER "false"}))))))
