(ns cmr.system-int-test.search.keyword-endpoint-test
  "Integration test for CMR search endpoint returning GCMD Keywords"
  (:require [clojure.test :refer :all]
            [cmr.common.util :as util]
            [cmr.system-int-test.utils.search-util :as search]))

(def expected-hierarchy
  "Maps the keyword scheme to the expected hierarchy for that scheme."
  {:archive-centers {"level-0"
                     [{"value" "ACADEMIC",
                       "subfields" ["short-name"],
                       "short-name"
                       [{"value" "AARHUS-HYDRO",
                         "subfields" ["long-name"],
                         "long-name"
                         [{"value"
                           "Hydrogeophysics Group, Aarhus University ",
                           "uuid" "bd197c6d-8612-42c2-a818-1975c4911e45"}]}]}
                      {"value" "CONSORTIA/INSTITUTIONS",
                       "subfields" ["short-name"],
                       "short-name"
                       [{"value" "ESA/ED",
                         "subfields" ["long-name"],
                         "long-name"
                         [{"value"
                           "Educational Office, Ecological Society of America",
                           "uuid" "2112a825-73c6-4b75-b33c-cc6e705a39ce"}]}]}
                      {"value" "GOVERNMENT AGENCIES-U.S. FEDERAL AGENCIES",
                       "subfields" ["level-1"],
                       "level-1"
                       [{"value" "NSF",
                         "subfields" ["short-name"],
                         "short-name"
                         [{"value" "UCAR/NCAR/EOL/CEOPDM",
                           "subfields" ["long-name"],
                           "long-name"
                           [{"value"
                             "CEOP Data Management, Earth Observing Laboratory, National Center for Atmospheric Research, University Corporation for Atmospheric Research",
                             "uuid"
                             "180b59c3-31c1-4129-8d74-09a9557ebc79"}]}]}
                        {"value" "DOI",
                         "subfields" ["level-2"],
                         "level-2"
                         [{"value" "USGS",
                           "subfields" ["level-3"],
                           "level-3"
                           [{"value" "Added level 3 value",
                             "subfields" ["short-name"],
                             "short-name"
                             [{"value" "DOI/USGS/CMG/WHSC",
                               "subfields" ["long-name"],
                               "long-name"
                               [{"value"
                                 "Woods Hole Science Center, Coastal and Marine Geology, U.S. Geological Survey, U.S. Department of the Interior",
                                 "uuid"
                                 "69db99c6-54d6-40b9-9f72-47eab9c34869"}]}]}]}]}]}]}
   :science-keywords {"category"
                      [{"value" "EARTH SCIENCE",
                        "uuid" "e9f67a66-e9fc-435c-b720-ae32a2c3d8f5"}
                       {"value" "EARTH SCIENCE SERVICES",
                        "uuid" "894f9116-ae3c-40b6-981d-5113de961710",
                        "subfields" ["topic"],
                        "topic"
                        [{"value" "DATA ANALYSIS AND VISUALIZATION",
                          "uuid" "41adc080-c182-4753-9666-435f8b1c913f",
                          "subfields" ["term"],
                          "term"
                          [{"value" "CALIBRATION/VALIDATION",
                            "uuid" "4f938731-d686-4d89-b72b-ff60474bb1f0"}
                           {"value" "GEOGRAPHIC INFORMATION SYSTEMS",
                            "uuid" "794e3c3b-791f-44de-9ff3-358d8ed74733",
                            "subfields" ["variable-level-1"],
                            "variable-level-1"
                            [{"value" "DESKTOP GEOGRAPHIC INFORMATION SYSTEMS",
                              "uuid" "565cb301-44de-446c-8fe3-4b5cce428315"}
                             {"value" "MOBILE GEOGRAPHIC INFORMATION SYSTEMS",
                              "uuid" "0dd83b2a-e83f-4a0c-a1ff-2fbdbbcce62d"}
                             {"value" "WEB-BASED GEOGRAPHIC INFORMATION SYSTEMS",
                              "uuid"
                              "037f42a2-cdda-4b72-b49c-bdec74d03e0a"}]}]}]}]}
  :platforms {"category"
              [{"value" "Aircraft",
                "subfields" ["short-name"],
                "short-name"
                [{"value" "A340-600",
                  "subfields" ["long-name"],
                  "long-name"
                  [{"value" "Airbus A340-600",
                    "uuid" "bab77f95-aa34-42aa-9a12-922d1c9fae63"}]}]}
               {"value" "Earth Observation Satellites",
                "subfields" ["series-entity"],
                "series-entity"
                [{"value"
                  "DMSP (Defense Meteorological Satellite Program)",
                  "subfields" ["short-name"],
                  "short-name"
                  [{"value" "DMSP 5B/F3",
                    "subfields" ["long-name"],
                    "long-name"
                    [{"value"
                      "Defense Meteorological Satellite Program-F3",
                      "uuid"
                      "7ed12e98-95b1-406c-a58a-f4bbfa405269"}]}]}
                 {"value" "DIADEM",
                  "subfields" ["short-name"],
                  "short-name"
                  [{"value" "DIADEM-1D",
                    "uuid" "143a5181-7601-4cc7-96d1-2b1a04b08fa7"}]}
                 {"value" "NASA Decadal Survey",
                  "subfields" ["short-name"],
                  "short-name"
                  [{"value" "SMAP",
                    "subfields" ["long-name"],
                    "long-name"
                    [{"value"
                      "Soil Moisture Active and Passive Observatory",
                      "uuid"
                      "7ee03239-24ff-433e-ab7e-8be8b9b2636b"}]}]}]}]}
  :instruments {"category"
                [{"value" "Earth Remote Sensing Instruments",
                  "subfields" ["class"],
                  "class"
                  [{"value" "Active Remote Sensing",
                    "subfields" ["type"],
                    "type"
                    [{"value" "Altimeters",
                      "subfields" ["subtype"],
                      "subtype"
                      [{"value" "Lidar/Laser Altimeters",
                        "subfields" ["short-name"],
                        "short-name"
                        [{"value" "ATM",
                          "subfields" ["long-name"],
                          "long-name"
                          [{"value" "Airborne Topographic Mapper",
                            "uuid"
                            "c2428a35-a87c-4ec7-aefd-13ff410b3271"}]}
                         {"value" "LVIS",
                          "subfields" ["long-name"],
                          "long-name"
                          [{"value" "Land, Vegetation, and Ice Sensor",
                            "uuid"
                            "aa338429-35e6-4ee2-821f-0eac81802689"}]}]}]}]}
                   {"value" "Passive Remote Sensing",
                    "subfields" ["type"],
                    "type"
                    [{"value" "Spectrometers/Radiometers",
                      "subfields" ["subtype"],
                      "subtype"
                      [{"value" "Imaging Spectrometers/Radiometers",
                        "subfields" ["short-name"],
                        "short-name"
                        [{"value" "SMAP L-BAND RADIOMETER",
                          "subfields" ["long-name"],
                          "long-name"
                          [{"value" "SMAP L-Band Radiometer",
                            "uuid"
                            "fee5e9e1-10f1-4f14-94bc-c287f8e2c209"}]}]}]}]}]}
                 {"value" "In Situ/Laboratory Instruments",
                  "subfields" ["class"],
                  "class"
                  [{"value" "Chemical Meters/Analyzers",
                    "subfields" ["short-name"],
                    "short-name"
                    [{"value" "ADS",
                      "subfields" ["long-name"],
                      "long-name"
                      [{"value" "Automated DNA Sequencer",
                        "uuid"
                        "554a3c73-3b48-43ea-bf5b-8b98bc2b11bc"}]}]}]}]}})

(deftest get-keywords-test
  (util/are2
    [keyword-scheme expected-keywords]
    (= {:status 200 :results expected-keywords}
       (search/get-keywords-by-keyword-scheme keyword-scheme))

    "Testing correct keyword hierarchy returned for science keywords."
    :science_keywords (:science-keywords expected-hierarchy)

    "Testing correct keyword hierarchy returned for archive centers."
    :archive_centers (:archive-centers expected-hierarchy)

    "Testing providers is an alias for archive centers."
    :providers (:archive-centers expected-hierarchy)

    "Testing correct keyword hierarchy returned for platforms."
    :platforms (:platforms expected-hierarchy)

    "Testing correct keyword hierarchy returned for instruments."
    :instruments (:instruments expected-hierarchy)))

(deftest invalid-keywords-test
  (testing "Invalid keyword scheme returns 400 error"
    (is (= {:status 400
            :errors [(str "The keyword scheme [foo] is not supported. Valid schemes are: "
                          "science_keywords, archive_centers, platforms, providers, and "
                          "instruments.")]}
           (search/get-keywords-by-keyword-scheme :foo)))))


