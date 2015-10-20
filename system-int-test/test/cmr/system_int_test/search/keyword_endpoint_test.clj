(ns cmr.system-int-test.search.keyword-endpoint-test
  "Integration test for CMR search endpoint returning GCMD Keywords"
  (:require [clojure.test :refer :all]
            [cmr.common.util :as util]
            [cmr.system-int-test.utils.search-util :as search]))

(def expected-hierarchy
  "Maps the keyword scheme to the expected hierarchy for that scheme."
  {:archive-centers {"level_0"
                     [{"value" "GOVERNMENT AGENCIES-U.S. FEDERAL AGENCIES",
                       "subfields" ["level_1"],
                       "level_1"
                       [{"value" "DOI",
                         "subfields" ["level_2"],
                         "level_2"
                         [{"value" "USGS",
                           "subfields" ["level_3"],
                           "level_3"
                           [{"value" "Added level 3 value",
                             "subfields" ["short_name"],
                             "short_name"
                             [{"value" "DOI/USGS/CMG/WHSC",
                               "subfields" ["long_name"],
                               "long_name"
                               [{"value"
                                 "Woods Hole Science Center, Coastal and Marine Geology, U.S. Geological Survey, U.S. Department of the Interior",
                                 "uuid"
                                 "69db99c6-54d6-40b9-9f72-47eab9c34869"}]}]}]}]}
                        {"value" "NSF",
                         "subfields" ["short_name"],
                         "short_name"
                         [{"value" "UCAR/NCAR/EOL/CEOPDM",
                           "subfields" ["long_name"],
                           "long_name"
                           [{"value"
                             "CEOP Data Management, Earth Observing Laboratory, National Center for Atmospheric Research, University Corporation for Atmospheric Research",
                             "uuid"
                             "180b59c3-31c1-4129-8d74-09a9557ebc79"}]}]}]}
                      {"value" "ACADEMIC",
                       "level_1"
                       [{"value" "OR-STATE/EOARC",
                         "subfields" ["short_name"],
                         "short_name"
                         [{"value" "OR-STATE/EOARC",
                           "subfields" ["long_name"],
                           "long_name"
                           [{"value"
                             "Eastern Oregon Agriculture Research Center, Oregon State University",
                             "uuid"
                             "44a93a03-29c0-4800-a5a8-67d2c2c2caa7"}]}]}],
                       "short_name"
                       [{"value" "AARHUS-HYDRO",
                         "subfields" ["long_name"],
                         "long_name"
                         [{"value" "Hydrogeophysics Group, Aarhus University ",
                           "uuid" "bd197c6d-8612-42c2-a818-1975c4911e45"}]}],
                       "subfields" ["short_name" "level_1"]}
                      {"value" "CONSORTIA/INSTITUTIONS",
                       "short_name"
                       [{"value" "ESA/ED",
                         "subfields" ["long_name"],
                         "long_name"
                         [{"value"
                           "Educational Office, Ecological Society of America",
                           "uuid" "2112a825-73c6-4b75-b33c-cc6e705a39ce"}]}],
                       "subfields" ["short_name"]}]}
   :science-keywords {"category"
                      [{"value" "EARTH SCIENCE SERVICES",
                        "subfields" ["topic"],
                        "topic"
                        [{"value" "DATA ANALYSIS AND VISUALIZATION",
                          "subfields" ["term"],
                          "term"
                          [{"value" "CALIBRATION/VALIDATION",
                            "uuid" "4f938731-d686-4d89-b72b-ff60474bb1f0"}
                           {"value" "GEOGRAPHIC INFORMATION SYSTEMS",
                            "uuid" "794e3c3b-791f-44de-9ff3-358d8ed74733",
                            "subfields" ["variable_level_1"],
                            "variable_level_1"
                            [{"value" "MOBILE GEOGRAPHIC INFORMATION SYSTEMS",
                              "uuid" "0dd83b2a-e83f-4a0c-a1ff-2fbdbbcce62d"}
                             {"value" "DESKTOP GEOGRAPHIC INFORMATION SYSTEMS",
                              "uuid" "565cb301-44de-446c-8fe3-4b5cce428315"}]}]}
                         {"value" "DATA MANAGEMENT/DATA HANDLING",
                          "subfields" ["term"],
                          "term"
                          [{"value" "CATALOGING",
                            "uuid" "434d40e2-4e0b-408a-9811-ff878f4f0fb0"}]}]}
                       {"value" "EARTH SCIENCE",
                        "subfields" ["topic"],
                        "topic"
                        [{"value" "ATMOSPHERE",
                          "subfields" ["term"],
                          "term"
                          [{"value" "AEROSOLS",
                            "subfields" ["variable_level_1"],
                            "variable_level_1"
                            [{"value" "AEROSOL OPTICAL DEPTH/THICKNESS",
                              "subfields" ["variable_level_2"],
                              "variable_level_2"
                              [{"value" "ANGSTROM EXPONENT",
                                "uuid"
                                "6e7306a1-79a5-482e-b646-74b75a1eaa48"}]}]}
                           {"value" "ATMOSPHERIC TEMPERATURE",
                            "subfields" ["variable_level_1"],
                            "variable_level_1"
                            [{"value" "SURFACE TEMPERATURE",
                              "subfields" ["variable_level_2"],
                              "variable_level_2"
                              [{"value" "MAXIMUM/MINIMUM TEMPERATURE",
                                "subfields" ["variable_level_3"],
                                "variable_level_3"
                                [{"value" "24 HOUR MAXIMUM TEMPERATURE",
                                  "uuid"
                                  "ce6a6b3a-df4f-4bd7-a931-7ee874ee9efe"}]}]}]}]}
                         {"value" "SOLID EARTH",
                          "subfields" ["term"],
                          "term"
                          [{"value" "ROCKS/MINERALS/CRYSTALS",
                            "subfields" ["variable_level_1"],
                            "variable_level_1"
                            [{"value" "SEDIMENTARY ROCKS",
                              "subfields" ["variable_level_2"],
                              "variable_level_2"
                              [{"value"
                                "SEDIMENTARY ROCK PHYSICAL/OPTICAL PROPERTIES",
                                "subfields" ["variable_level_3"],
                                "variable_level_3"
                                [{"value" "LUMINESCENCE",
                                  "uuid"
                                  "3e705ebc-c58f-460d-b5e7-1da05ee45cc1"}]}]}]}]}]}]}
   :platforms {"category"
               [{"value" "Earth Observation Satellites",
                 "subfields" ["series_entity"],
                 "series_entity"
                 [{"value"
                   "DMSP (Defense Meteorological Satellite Program)",
                   "subfields" ["short_name"],
                   "short_name"
                   [{"value" "DMSP 5B/F3",
                     "subfields" ["long_name"],
                     "long_name"
                     [{"value"
                       "Defense Meteorological Satellite Program-F3",
                       "uuid" "7ed12e98-95b1-406c-a58a-f4bbfa405269"}]}]}
                  {"value" "DIADEM",
                   "subfields" ["short_name"],
                   "short_name"
                   [{"value" "DIADEM-1D",
                     "uuid" "143a5181-7601-4cc7-96d1-2b1a04b08fa7"}]}
                  {"value" "NASA Decadal Survey",
                   "subfields" ["short_name"],
                   "short_name"
                   [{"value" "SMAP",
                     "subfields" ["long_name"],
                     "long_name"
                     [{"value"
                       "Soil Moisture Active and Passive Observatory",
                       "uuid"
                       "7ee03239-24ff-433e-ab7e-8be8b9b2636b"}]}]}]}
                {"value" "Aircraft",
                 "short_name"
                 [{"value" "AIRCRAFT",
                   "uuid" "8bce0691-74e9-4363-8d1f-d453a318c62b"}
                  {"value" "A340-600",
                   "subfields" ["long_name"],
                   "long_name"
                   [{"value" "Airbus A340-600",
                     "uuid" "bab77f95-aa34-42aa-9a12-922d1c9fae63"}]}],
                 "subfields" ["short_name"]}]}
   :instruments {"category"
                 [{"value" "Earth Remote Sensing Instruments",
                   "subfields" ["class"],
                   "class"
                   [{"value" "Active Remote Sensing",
                     "subfields" ["type"],
                     "type"
                     [{"value" "Profilers/Sounders",
                       "subfields" ["subtype"],
                       "subtype"
                       [{"value" "Acoustic Sounders",
                         "subfields" ["short_name"],
                         "short_name"
                         [{"value" "ACOUSTIC SOUNDERS",
                           "uuid"
                           "7ef0c3e6-1012-411a-b166-482fb35bb1dd"}]}]}
                      {"value" "Altimeters",
                       "subfields" ["subtype"],
                       "subtype"
                       [{"value" "Lidar/Laser Altimeters",
                         "subfields" ["short_name"],
                         "short_name"
                         [{"value" "ATM",
                           "subfields" ["long_name"],
                           "long_name"
                           [{"value" "Airborne Topographic Mapper",
                             "uuid"
                             "c2428a35-a87c-4ec7-aefd-13ff410b3271"}]}
                          {"value" "LVIS",
                           "subfields" ["long_name"],
                           "long_name"
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
                         "subfields" ["short_name"],
                         "short_name"
                         [{"value" "SMAP L-BAND RADIOMETER",
                           "subfields" ["long_name"],
                           "long_name"
                           [{"value" "SMAP L-Band Radiometer",
                             "uuid"
                             "fee5e9e1-10f1-4f14-94bc-c287f8e2c209"}]}]}]}]}]}
                  {"value" "In Situ/Laboratory Instruments",
                   "subfields" ["class"],
                   "class"
                   [{"value" "Chemical Meters/Analyzers",
                     "subfields" ["short_name"],
                     "short_name"
                     [{"value" "ADS",
                       "subfields" ["long_name"],
                       "long_name"
                       [{"value" "Automated DNA Sequencer",
                         "uuid"
                         "554a3c73-3b48-43ea-bf5b-8b98bc2b11bc"}]}]}]}]}
   :projects {"short_name"
              [{"value" "EUDASM",
                "subfields" ["long_name"],
                "long_name"
                [{"value" "European Digital Archive of Soil Maps",
                  "uuid" "8497a1a8-5192-4e94-aabd-e8c349f2f79c"}]}
               {"value" "EUCREX-94",
                "uuid" "454a3c42-46e4-4f6b-a83c-b624fe553e0b"}
               {"value" "SEDAC/GW",
                "subfields" ["long_name"],
                "long_name"
                [{"value" "SEDAC Gateway",
                  "uuid" "ba299a14-0b5b-4fbc-a1ce-87936f072210"}]}
               {"value" "AA",
                "subfields" ["long_name"],
                "long_name"
                [{"value" "ARCATLAS",
                  "uuid" "a30ac9a7-82b0-42b6-93db-2764bd7535ca"}]}
               {"value" "SEDAC/GISS CROP-CLIM DBQ",
                "subfields" ["long_name"],
                "long_name"
                [{"value"
                  "SEDAC Goddard Institute for Space Studies Crop-Climate Database Query",
                  "uuid" "8ff7fd0b-caa4-423d-8387-c749e2795c46"}]}]}
   :temporal-resolution-ranges {"temporal_resolution_range"
                                [{"value" "Monthly Climatology",
                                  "uuid" "8c8c70b1-f6c5-4f34-89b5-510049b8c8ab"}
                                 {"value" "Monthly - < Annual",
                                  "uuid" "8900c323-8789-4403-91e9-c399de369935"}
                                 {"value" "Decadal",
                                  "uuid" "3d97e993-dc6a-41ff-8a49-3e837c1fc2b1"}
                                 {"value" "Annual Climatology",
                                  "uuid" "af931dca-9a7d-4ba9-b40f-2a21e31f2d5b"}
                                 {"value" "1 second - < 1 minute",
                                  "uuid" "48ff676f-836c-4cff-bc88-4c4cc06b2e1b"}
                                 {"value" "Daily - < Weekly",
                                  "uuid" "1ac968ef-a90a-4ffc-adbf-ea0c0d69a7f9"}
                                 {"value" "1 minute - < 1 hour",
                                  "uuid" "bca20202-2b06-4657-a425-5b0e416bce0c"}
                                 {"value" "Climate Normal (30-year climatology)",
                                  "uuid" "f308a8db-40ea-4932-a58c-fb0a093959dc"}
                                 {"value" "Weekly - < Monthly",
                                  "uuid" "7b2a303c-3cb7-4961-9851-650548964674"}
                                 {"value" "< 1 second",
                                  "uuid" "42a2f639-d1c3-4e82-a8b8-63f0f4a60ac6"}
                                 {"value" "Annual",
                                  "uuid" "40e09855-fb48-4a7d-9851-d6e809e6c309"}
                                 {"value" "Weekly Climatology",
                                  "uuid" "2de882f0-d84a-471e-8fb5-9f8a1c7913c1"}
                                 {"value" "Hourly - < Daily",
                                  "uuid" "31765761-b153-478a-92b3-1088997fd74b"}
                                 {"value" "Pentad Climatology",
                                  "uuid" "e0040d4b-e398-4b65-bd42-d39434b5cc95"}
                                 {"value" "Hourly Climatology",
                                  "uuid" "027dee16-b361-481e-868d-add966eb5b71"}
                                 {"value" "Daily Climatology",
                                  "uuid" "f86e464a-cf9d-4e15-a39b-501855d1dc5a"}]}
   :spatial-keywords {"category"
                      [{"value" "CONTINENT",
                        "subfields" ["type"],
                        "type"
                        [{"value" "AFRICA",
                          "subfields" ["subregion_1"],
                          "subregion_1"
                          [{"value" "CENTRAL AFRICA",
                            "subfields" ["subregion_2"],
                            "subregion_2"
                            [{"value" "ANGOLA",
                              "uuid"
                              "9b0a194d-d617-4fed-9625-df176319892d"}]}]}]}
                       {"value" "GEOGRAPHIC REGION",
                        "subfields" ["type"],
                        "type"
                        [{"value" "ARCTIC",
                          "uuid" "d40d9651-aa19-4b2c-9764-7371bb64b9a7"}]}
                       {"value" "OCEAN",
                        "subfields" ["type"],
                        "type"
                        [{"value" "ATLANTIC OCEAN",
                          "subfields" ["subregion_1"],
                          "subregion_1"
                          [{"value" "NORTH ATLANTIC OCEAN",
                            "subfields" ["subregion_2"],
                            "subregion_2"
                            [{"value" "BALTIC SEA",
                              "uuid"
                              "41cd228c-4677-4900-9507-70144d8b50bc"}]}]}]}]}})

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
    :instruments (:instruments expected-hierarchy)

    "Testing correct keyword hierarchy returned for projects."
    :projects (:projects expected-hierarchy)

    "Testing correct keyword hierarchy returned for temporal resolution ranges."
    :temporal-resolution-ranges (:temporal-resolution-ranges expected-hierarchy)

    "Testing correct keyword hierarchy returned for spatial keywords."
    :spatial-keywords (:spatial-keywords expected-hierarchy)))

(deftest invalid-keywords-test
  (testing "Invalid keyword scheme returns 400 error"
    (is (= {:status 400
            :errors [(str "The keyword scheme [foo] is not supported. Valid schemes are: "
                          "platforms, instruments, projects, temporal_resolution_ranges, "
                          "spatial_keywords, science_keywords, archive_centers, data_centers, and "
                          "providers.")]}
           (search/get-keywords-by-keyword-scheme :foo)))))


