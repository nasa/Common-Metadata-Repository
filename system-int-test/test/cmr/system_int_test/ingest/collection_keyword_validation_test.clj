(ns cmr.system-int-test.ingest.collection-keyword-validation-test
  "CMR Ingest keyword validation integration tests"
  (:require
    [clojure.test :refer :all]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.data2.collection :as dc]
    [cmr.system-int-test.data2.core :as d]
    [cmr.common.util :refer [are2]]
    [cmr.ingest.services.messages :as msg]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; The following tests are included in this file
;; See individual deftest for detailed test info.
;;
;; 1. collection-keyword-validation-test
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn assert-invalid
  ([coll-attributes field-path errors]
   (assert-invalid coll-attributes field-path errors nil))
  ([coll-attributes field-path errors options]
   (let [response (d/ingest "PROV1" (dc/collection coll-attributes)
                            (merge {:allow-failure? true} options))]
     (is (= {:status 422
             :errors [{:path field-path
                       :errors errors}]}
            (select-keys response [:status :errors]))))))

(defn assert-valid
  ([coll-attributes]
   (assert-valid coll-attributes nil))
  ([coll-attributes options]
   (let [collection (assoc (dc/collection coll-attributes) :native-id (:native-id coll-attributes))
         provider-id (get coll-attributes :provider-id "PROV1")
         response (d/ingest provider-id collection options)]
     (is (#{{:status 200} {:status 201}} (select-keys response [:status :errors]))))))

(defn assert-invalid-keywords
  [coll-attributes field-path errors]
  (assert-invalid coll-attributes field-path errors {:validate-keywords true}))

(defn assert-valid-keywords
  [coll-attributes]
  (assert-valid coll-attributes {:validate-keywords true}))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest collection-keyword-validation-test
  ;; For a list of the valid keywords during testing see dev-system/resources/kms_examples
  (testing "Keyword validation using validation endpoint"
    (let [concept (dc/collection-concept {:platforms [(dc/platform {:short-name "foo"
                                                                    :long-name "Airbus A340-600"})]})
          response (ingest/validate-concept concept {:validate-keywords true})]
      (is (= {:status 422
              :errors [{:path ["Platforms" 0]
                        :errors [(str "Platform short name [foo] and long name [Airbus A340-600] "
                                      "was not a valid keyword combination.")]}]}
             response))))

  (testing "Project keyword validation"
    (are2 [short-name long-name]
          (assert-invalid-keywords
            {:projects [(assoc (dc/project short-name "") :long-name long-name)]}
            ["Projects" 0]
            [(format (str "Project short name [%s] and long name [%s]"
                          " was not a valid keyword combination.")
                     short-name long-name)])

          "Invalid short name"
          "foo" "European Digital Archive of Soil Maps"

          "Invalid with nil long name"
          "foo" nil

          "Invalid long name"
          "EUDASM" "foo"

          "Long name was nil in KMS"
          "EUCREX-94" "foo"

          "Invalid combination"
          "SEDAC/GISS CROP-CLIM DBQ" "European Digital Archive of Soil Maps")

    (are2 [short-name long-name]
          (assert-valid-keywords
            {:projects [(assoc (dc/project short-name "") :long-name long-name)]})
          "Exact match"
          "EUDASM" "European Digital Archive of Soil Maps"

          "Nil long name in project and in KMS"
          "EUCREX-94" nil

          "Case Insensitive"
          "EUDaSM" "European DIgItal ArchIve of SoIl MAps"))

  (testing "Platform keyword validation"
    (are2 [short-name long-name]
          (assert-invalid-keywords
            {:platforms [(dc/platform {:short-name short-name :long-name long-name})]}
            ["Platforms" 0]
            [(format (str "Platform short name [%s] and long name [%s]"
                          " was not a valid keyword combination.")
                     short-name long-name)])

          "Invalid short name"
          "foo" "Airbus A340-600"

          "Long name is nil in KMS"
          "AIRCRAFT" "Airbus A340-600"

          "Invalid long name"
          "DMSP 5B/F3" "foo"

          "Invalid combination"
          "DMSP 5B/F3" "Airbus A340-600")

    (are2 [short-name long-name]
          (assert-valid-keywords
            {:platforms [(dc/platform {:short-name short-name :long-name long-name})]})
          "Exact match"
          "A340-600" "Airbus A340-600"

          "Case Insensitive"
          "a340-600" "aiRBus A340-600"))

  (testing "Instrument keyword validation"
    (are2 [short-name long-name]
          (assert-invalid-keywords
            {:platforms
             [(dc/platform
                {:short-name "A340-600"
                 :long-name "Airbus A340-600"
                 :instruments [(dc/instrument {:short-name short-name
                                               :long-name long-name})]})]}
            ["Platforms" 0 "Instruments" 0]
            [(format (str "Instrument short name [%s] and long name [%s]"
                          " was not a valid keyword combination.")
                     short-name long-name)])
          "Invalid short name"
          "foo" "Airborne Topographic Mapper"

          "Long name is nil in KMS"
          "ACOUSTIC SOUNDERS" "foo"

          "Invalid long name"
          "ATM" "foo"

          "Invalid combination"
          "ATM" "Land, Vegetation, and Ice Sensor")

    (are2 [short-name long-name]
          (assert-valid-keywords
            {:platforms
             [(dc/platform
                {:short-name "A340-600"
                 :long-name "Airbus A340-600"
                 :instruments [(dc/instrument {:short-name short-name
                                               :long-name long-name})]})]})
          "Exact match"
          "ATM" "Airborne Topographic Mapper"

          "Nil long name in project and in KMS"
          "ACOUSTIC SOUNDERS" nil

          "Case Insensitive"
          "Atm" "aIRBORNE Topographic Mapper"))

  (testing "Science Keyword validation"
    (are [attribs]
         (let [sk (dc/science-keyword attribs)]
           (assert-invalid-keywords
             {:science-keywords [sk]}
             ["ScienceKeywords" 0]
             [(msg/science-keyword-not-matches-kms-keywords sk)]))

         {:category "foo"
          :topic "DATA ANALYSIS AND VISUALIZATION"
          :term "GEOGRAPHIC INFORMATION SYSTEMS"}

         {:category "EARTH SCIENCE SERVICES"
          :topic "foo"
          :term "GEOGRAPHIC INFORMATION SYSTEMS"}

         {:category "EARTH SCIENCE SERVICES"
          :topic "DATA ANALYSIS AND VISUALIZATION"
          :term "foo"}

         {:category "EARTH SCIENCE SERVICES"
          :topic "DATA ANALYSIS AND VISUALIZATION"
          :term "GEOGRAPHIC INFORMATION SYSTEMS"
          :variable-level-1 "foo"}

         {:category "EARTH SCIENCE"
          :topic "ATMOSPHERE"
          :term "AEROSOLS"
          :variable-level-1 "AEROSOL OPTICAL DEPTH/THICKNESS"
          :variable-level-2 "foo"}

         {:category "EARTH SCIENCE"
          :topic "ATMOSPHERE"
          :term "ATMOSPHERIC TEMPERATURE"
          :variable-level-1 "SURFACE TEMPERATURE"
          :variable-level-2 "MAXIMUM/MINIMUM TEMPERATURE"
          :variable-level-3 "foo"}

         ;; Invalid combination. Topic is valid but not with these other terms
         {:category "EARTH SCIENCE SERVICES"
          :topic "ATMOSPHERE"
          :term "GEOGRAPHIC INFORMATION SYSTEMS"})

    (are [attribs]
         (assert-valid-keywords {:science-keywords [(dc/science-keyword attribs)]})

         {:category "EARTH SCIENCE SERVICES"
          :topic "DATA ANALYSIS AND VISUALIZATION"
          :term "GEOGRAPHIC INFORMATION SYSTEMS"}

         {:category "EARTH SCIENCE SERVICES"
          :topic "DATA ANALYSIS AND VISUALIZATION"
          :term "GEOGRAPHIC INFORMATION SYSTEMS"
          :variable-level-1 "MOBILE GEOGRAPHIC INFORMATION SYSTEMS"}

         {:category "EARTH SCIENCE"
          :topic "ATMOSPHERE"
          :term "AEROSOLS"
          :variable-level-1 "AEROSOL OPTICAL DEPTH/THICKNESS"
          :variable-level-2 "ANGSTROM EXPONENT"}

         {:category "EARTH SCIENCE"
          :topic "ATMOSPHERE"
          :term "ATMOSPHERIC TEMPERATURE"
          :variable-level-1 "SURFACE TEMPERATURE"
          :variable-level-2 "MAXIMUM/MINIMUM TEMPERATURE"
          :variable-level-3 "24 HOUR MAXIMUM TEMPERATURE"
          :detailed-variable-level "This is ignored"}

         {:category "EARTH SCiENCE"
          :topic "ATMOsPHERE"
          :term "ATMOSpHERIC TEMPERATURE"
          :variable-level-1 "SuRFACE TEMPERATURE"
          :variable-level-2 "MAXiMUM/MiNiMUM TEMPERATURE"
          :variable-level-3 "24 HOUR MAXiMUM TEMPERATURE"})))

