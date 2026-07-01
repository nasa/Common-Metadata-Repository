(ns cmr.umm-spec.test.umm-to-xml-mappings.iso-shared.quality
  "Tests to verify that quality iso is generated correctly."
  (:require
   [clojure.test :refer [deftest is testing]]
   [cmr.umm-spec.umm-to-xml-mappings.iso-shared.quality :as quality]))

(deftest generate-quality-test
  (testing "1. Full record with all fields generates correctly formatted nested hiccup vectors"
    (let [input {:Quality {:Summary "Overview text."
                           :QualityContentDetails {:Strengths "High accuracy."
                                                   :Limitations "Cloud cover."
                                                   :KnownIssues "Sensor drift."
                                                   :Other "Flags included."}}}
          expected-text "Summary: Overview text. Strengths: High accuracy. Limitations: Cloud cover. KnownIssues: Sensor drift. Other: Flags included."
          result (quality/generate-quality input)
          accuracy-block (second result)
          eval-method (nth accuracy-block 1)]
      (is (= [:gco:CharacterString expected-text] (second eval-method)))))

  (testing "2. Returns nil cleanly when the root :Quality map is entirely missing"
    (let [input {:EntryId "NASA-METADATA-1"}]
      (is (nil? (quality/generate-quality input)))))

  (testing "3. Outputs only Summary when QualityContentDetails is empty or nil"
    (let [input {:Quality {:Summary "Only basic summary metadata."
                           :QualityContentDetails {}}}
          expected-text "Summary: Only basic summary metadata."
          result (quality/generate-quality input)
          ;; Drops straight into :gmd:evaluationMethodDescription
          eval-method (get-in result [1 1])]
      (is (= [:gco:CharacterString expected-text] (second eval-method)))))

  (testing "4. Safely skips over missing, empty string, or nil inner content details"
    (let [input {:Quality {:Summary "Overview text."
                           :QualityContentDetails {:Strengths "Solid calibration."
                                                   :Limitations ""
                                                   :KnownIssues nil
                                                   :Other "Check codes."}}}
          expected-text "Summary: Overview text. Strengths: Solid calibration. Other: Check codes."
          result (quality/generate-quality input)
          eval-method (get-in result [1 1])]
      (is (= [:gco:CharacterString expected-text] (second eval-method)))))

  (testing "5. Verifies explicit schema ordering within the details map block"
    ;; Verifies that the vector layout ordering forces Strengths to appear before Limitations 
    ;; even if the input map key ordering changes.
    (let [input {:Quality {:Summary "Overview."
                           :QualityContentDetails {:Limitations "B text."
                                                   :Strengths "A text."}}}
          expected-text "Summary: Overview. Strengths: A text. Limitations: B text."
          result (quality/generate-quality input)
          eval-method (get-in result [1 1])]
      (is (= [:gco:CharacterString expected-text] (second eval-method))))))
