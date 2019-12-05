(ns cmr.system-int-test.search.keyword-endpoint-test
  "Integration test for CMR search endpoint returning GCMD Keywords"
  (:require [clojure.test :refer :all]
            [cmr.common.util :as util]
            [cmr.system-int-test.utils.search-util :as search]))

(def expected-hierarchy
  "Maps the keyword scheme to the expected hierarchy for that scheme."
  {:archive-centers {"level_0"
                     [{"value" "CONSORTIA/INSTITUTIONS",
                       "subfields" ["short_name"],
                       "short_name"
                       [{"value" "ESA/ED",
                         "subfields" ["long_name"],
                         "long_name"
                         [{"value"
                           "Educational Office, Ecological Society of America",
                           "subfields" ["url"],
                           "url"
                           [{"value" "http://www.esa.org/education/",
                             "uuid"
                             "2112a825-73c6-4b75-b33c-cc6e705a39ce"}]}]}]}
                      {"value" "GOVERNMENT AGENCIES-U.S. FEDERAL AGENCIES",
                       "subfields" ["level_1"],
                       "level_1"
                       [{"value" "NSF",
                         "subfields" ["short_name"],
                         "short_name"
                         [{"value" "UCAR/NCAR/EOL/CEOPDM",
                           "subfields" ["long_name"],
                           "long_name"
                           [{"value"
                             "CEOP Data Management, Earth Observing Laboratory, National Center for Atmospheric Research, University Corporation for Atmospheric Research",
                             "subfields" ["url"],
                             "url"
                             [{"value"
                               "http://www.eol.ucar.edu/projects/ceop/dm/",
                               "uuid"
                               "180b59c3-31c1-4129-8d74-09a9557ebc79"}]}]}]}
                        {"value" "DOI",
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
                                 "subfields" ["url"],
                                 "url"
                                 [{"value" "http://woodshole.er.usgs.gov/",
                                   "uuid"
                                   "69db99c6-54d6-40b9-9f72-47eab9c34869"}]}]}]}]}]}]}
                      {"value" "ACADEMIC",
                       "subfields" ["short_name" "level_1"],
                       "level_1"
                       [{"value" "OR-STATE/EOARC",
                         "subfields" ["short_name"],
                         "short_name"
                         [{"value" "OR-STATE/EOARC",
                           "subfields" ["long_name"],
                           "long_name"
                           [{"value"
                             "Eastern Oregon Agriculture Research Center, Oregon State University",
                             "subfields" ["url"],
                             "url"
                             [{"value"
                               "http://oregonstate.edu/dept/eoarcunion/",
                               "uuid"
                               "44a93a03-29c0-4800-a5a8-67d2c2c2caa7"}]}]}]}],
                       "short_name"
                       [{
                         "value" "AARHUS-HYDRO",
                         "subfields" ["long_name"],
                         "long_name"
                         [{"value" "Hydrogeophysics Group, Aarhus University ",
                           "uuid" "bd197c6d-8612-42c2-a818-1975c4911e45"}]}]}]}
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
               [{"value" "Aircraft",
                 "subfields" ["short_name"],
                 "short_name"
                 [{"value" "B-200",
                   "subfields" ["long_name"],
                   "long_name"
                   [{"value" "Beechcraft King Air B-200",
                     "uuid" "d6aa2406-0323-43c1-b890-3509ee22784e"}]}
                  {"value" "DHC-3",
                   "subfields" ["long_name"],
                   "long_name"
                   [{"value" "DeHavilland DHC-3 Otter",
                     "uuid" "aef364b1-6a71-49c0-b248-6dc1ecd4eaa3"}]}
                  {"value" "CESSNA 188",
                   "uuid" "80374e6d-fef6-4b11-bcc4-53568a3db220"}
                  {"value" "AIRCRAFT",
                   "uuid" "8bce0691-74e9-4363-8d1f-d453a318c62b"}
                  {"value" "ALTUS",
                   "uuid" "46392889-f6e2-4b06-8f79-87f2ff9d4349"}
                  {"value" "A340-600",
                   "subfields" ["long_name"],
                   "long_name"
                   [{"value" "Airbus A340-600",
                     "uuid" "bab77f95-aa34-42aa-9a12-922d1c9fae63"}]}]}
                {"value" "Earth Observation Satellites",
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
                       "7ee03239-24ff-433e-ab7e-8be8b9b2636b"}]}]}]}]}
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
   :temporal-keywords {"temporal_resolution_range"
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
                      [{"value" "GEOGRAPHIC REGION",
                        "uuid" "204270d9-8039-4768-851e-63635af5fb65",
                        "subfields" ["type"],
                        "type"
                        [{"value" "ARCTIC",
                          "uuid" "d40d9651-aa19-4b2c-9764-7371bb64b9a7"}]}
                       {"value" "OCEAN",
                        "uuid" "ff03e9fc-9882-4a5e-ad0b-830d8f1186cb",
                        "subfields" ["type"],
                        "type"
                        [{"value" "ATLANTIC OCEAN",
                          "uuid" "cf249a36-2e82-4d32-84cd-23a4f40bb393",
                          "subfields" ["subregion_1"],
                          "subregion_1"
                          [{"value" "NORTH ATLANTIC OCEAN",
                            "uuid" "a4202721-0cba-4fa1-853f-890f146b04f9",
                            "subfields" ["subregion_2"],
                            "subregion_2"
                            [{"value" "BALTIC SEA",
                              "uuid"
                              "41cd228c-4677-4900-9507-70144d8b50bc"}]}]}]}
                       {"value" "CONTINENT",
                        "uuid" "0a672f19-dad5-4114-819a-2eb55bdbb56a",
                        "subfields" ["type"],
                        "type"
                        [{"value" "ASIA",
                          "subfields" ["subregion_1"],
                          "subregion_1"
                          [{"value" "WESTERN ASIA",
                            "subfields" ["subregion_2"],
                            "subregion_2"
                            [{"value" "MIDDLE EAST",
                              "subfields" ["subregion_3"],
                              "subregion_3"
                              [{"value" "GAZA STRIP",
                                "uuid"
                                "302ab5f2-5fa2-482d-9d22-8a7a1546a62d"}]}]}]}
                         {"value" "AFRICA",
                          "uuid" "2ca1b865-5555-4375-aa81-72811335b695",
                          "subfields" ["subregion_1"],
                          "subregion_1"
                          [{"value" "CENTRAL AFRICA",
                            "uuid" "f2ffbe58-8792-413b-805b-3e1c8de1c6ff",
                            "subfields" ["subregion_2"],
                            "subregion_2"
                            [{"value" "ANGOLA",
                              "uuid"
                              "9b0a194d-d617-4fed-9625-df176319892d"}]}]}]}]},
   :location-keywords {"category"
                       [{"value" "GEOGRAPHIC REGION",
                         "uuid" "204270d9-8039-4768-851e-63635af5fb65",
                         "subfields" ["type"],
                         "type"
                         [{"value" "ARCTIC",
                           "uuid" "d40d9651-aa19-4b2c-9764-7371bb64b9a7"}]}
                        {"value" "OCEAN",
                         "uuid" "ff03e9fc-9882-4a5e-ad0b-830d8f1186cb",
                         "subfields" ["type"],
                         "type"
                         [{"value" "ATLANTIC OCEAN",
                           "uuid" "cf249a36-2e82-4d32-84cd-23a4f40bb393",
                           "subfields" ["subregion_1"],
                           "subregion_1"
                           [{"value" "NORTH ATLANTIC OCEAN",
                             "uuid" "a4202721-0cba-4fa1-853f-890f146b04f9",
                             "subfields" ["subregion_2"],
                             "subregion_2"
                             [{"value" "BALTIC SEA",
                               "uuid"
                               "41cd228c-4677-4900-9507-70144d8b50bc"}]}]}]}
                        {"value" "CONTINENT",
                         "uuid" "0a672f19-dad5-4114-819a-2eb55bdbb56a",
                         "subfields" ["type"],
                         "type"
                         [{"value" "ASIA",
                           "subfields" ["subregion_1"],
                           "subregion_1"
                           [{"value" "WESTERN ASIA",
                             "subfields" ["subregion_2"],
                             "subregion_2"
                             [{"value" "MIDDLE EAST",
                               "subfields" ["subregion_3"],
                               "subregion_3"
                               [{"value" "GAZA STRIP",
                                 "uuid"
                                 "302ab5f2-5fa2-482d-9d22-8a7a1546a62d"}]}]}]}
                          {"value" "AFRICA",
                           "uuid" "2ca1b865-5555-4375-aa81-72811335b695",
                           "subfields" ["subregion_1"],
                           "subregion_1"
                           [{"value" "CENTRAL AFRICA",
                             "uuid" "f2ffbe58-8792-413b-805b-3e1c8de1c6ff",
                             "subfields" ["subregion_2"],
                             "subregion_2"
                             [{"value" "ANGOLA",
                               "uuid"
                               "9b0a194d-d617-4fed-9625-df176319892d"}]}]}]}]}
   :granule-data-format {"pref_label"
                         [{"value" "ESRI Geodatabase",
                           "uuid"
                           [{"value" "59d4ac47-d1d0-4c7c-bb2b-dc37e6273943",
                             "uuid" "59d4ac47-d1d0-4c7c-bb2b-dc37e6273943"}]}
                          {"value" "NetCDF Classic",
                           "uuid"
                           [{"value" "0fd9652e-2b6e-4dba-8c4d-63021f34bd53",
                             "uuid" "0fd9652e-2b6e-4dba-8c4d-63021f34bd53"}]}
                          {"value" "UF",
                           "uuid"
                           [{"value" "f8c1fd07-20b6-47fe-a420-f01679c523d1",
                             "uuid" "f8c1fd07-20b6-47fe-a420-f01679c523d1"}]}
                          {"value" "TIFF",
                           "uuid"
                           [{"value" "0225ee3e-c3b1-4a5d-bd22-b36462330b00",
                             "uuid" "0225ee3e-c3b1-4a5d-bd22-b36462330b00"}]}
                          {"value" "SLC",
                           "uuid"
                           [{"value" "bc5c5996-c046-4d70-aeea-4ba4de2c4f96",
                             "uuid" "bc5c5996-c046-4d70-aeea-4ba4de2c4f96"}]}
                          {
                           "value" "PNG",
                           "uuid"
                           [{"value" "131c1f06-d827-4b0f-b2cf-d0585f221be1",
                             "uuid" "131c1f06-d827-4b0f-b2cf-d0585f221be1"}]}
                          {"value" "ESRI Shapefile",
                           "uuid"
                           [{"value" "7534e14c-0847-48d2-879a-6fa64ff05dd0",
                             "uuid" "7534e14c-0847-48d2-879a-6fa64ff05dd0"}]}
                          {"value" "SQLite",
                           "uuid"
                           [{"value" "37adee23-d239-4e1d-8ac8-1c7e26f36dc6",
                             "uuid" "37adee23-d239-4e1d-8ac8-1c7e26f36dc6"}]}
                          {"value" "Excel Workbook",
                           "uuid"
                           [{"value" "e807acba-457e-4f7e-be44-da5f93f4118b",
                             "uuid" "e807acba-457e-4f7e-be44-da5f93f4118b"}]}
                          {"value" "OGC KML",
                           "uuid"
                           [{"value" "cfcd3481-9ddc-49d2-a827-76cecd1746d5",
                             "uuid" "cfcd3481-9ddc-49d2-a827-76cecd1746d5"}]}
                          {"value" "NetCDF-CF/Radial",
                           "uuid"

                           [{"value" "3ca1eef4-a930-4566-a92e-29a1bcfe3690",
                             "uuid" "3ca1eef4-a930-4566-a92e-29a1bcfe3690"}]}
                          {"value" "DTA",
                           "uuid"
                           [{"value" "61225045-76c9-439f-a44b-b5c1a26635f1",
                             "uuid" "61225045-76c9-439f-a44b-b5c1a26635f1"}]}
                          {"value" "ASCII",
                           "uuid"
                           [{"value" "8e128326-b9cb-44c7-9e6b-4bd950a08753",
                             "uuid" "8e128326-b9cb-44c7-9e6b-4bd950a08753"}]}
                          {"value" "HDF-EOS5",
                           "uuid"
                           [{"value" "0e1b63cf-966f-4f42-9575-e6b362de9aaa",
                             "uuid" "0e1b63cf-966f-4f42-9575-e6b362de9aaa"}]}
                          {"value" "SeaBASS",
                           "uuid"
                           [{"value" "be0d9b66-445d-4109-8784-63f1ea80e729",
                             "uuid" "be0d9b66-445d-4109-8784-63f1ea80e729"}]}
                          {"value" "CCSDS",
                           "uuid"
                           [{"value" "2da9aa88-c3d4-4307-a86f-e048b6297899",
                             "uuid" "2da9aa88-c3d4-4307-a86f-e048b6297899"}]}
                          {"value" "IONEX",
                           "uuid"
                           [{"value" "133c2aee-50f9-4260-b792-6d6694399da3",
                             "uuid" "133c2aee-50f9-4260-b792-6d6694399da3"}]}
                          {"value" "AREA",
                           "uuid"
                           [{"value" "ebe6f3c4-a78f-4152-977c-296d42e4e9e8",
                             "uuid" "ebe6f3c4-a78f-4152-977c-296d42e4e9e8"}]}
                          {"value" "ESRI Grid",
                           "uuid"
                           [{"value" "bbf2a46d-7de1-4e70-a039-4db2f3251776",
                             "uuid" "bbf2a46d-7de1-4e70-a039-4db2f3251776"}]}
                          {"value" "NetCDF-3",
                           "uuid"
                           [{"value" "868fc5dc-21fa-4356-a3de-f4c3c9559a21",
                             "uuid" "868fc5dc-21fa-4356-a3de-f4c3c9559a21"}]}
                          {"value" "KML",
                           "uuid"
                           [{"value" "809da52c-3147-403c-8d4e-e06119ef89f9",
                             "uuid" "809da52c-3147-403c-8d4e-e06119ef89f9"}]}
                          {"value" "SAS Transport Files",
                           "uuid"
                           [{"value" "6231402a-7e4c-42d9-802d-7184eb812f46",
                             "uuid" "6231402a-7e4c-42d9-802d-7184eb812f46"}]}
                          {"value" "BIL",
                           "uuid"
                           [{"value" "4dc86020-d8e0-49d2-b217-a48c18111b51",
                             "uuid" "4dc86020-d8e0-49d2-b217-a48c18111b51"}]}
                          {"value" "Slope",
                           "uuid"
                           [{"value" "28d8f538-1934-460c-94ea-cb06ce1535cd",
                             "uuid" "28d8f538-1934-460c-94ea-cb06ce1535cd"}]}
                          {"value" "BIP",
                           "uuid"
                           [{"value" "58fdfa0e-ca66-4534-948e-0cc0dbc219dd",
                             "uuid" "58fdfa0e-ca66-4534-948e-0cc0dbc219dd"}]}
                          {"value" "JPEG-2000",
                           "uuid"
                           [{"value" "e1544d27-a3b8-4b14-95aa-e25b21bcce1f",
                             "uuid" "e1544d27-a3b8-4b14-95aa-e25b21bcce1f"}]}
                          {"value" "Atlas GIS",
                           "uuid"
                           [{"value" "3465ad3b-63eb-4718-a347-8d2844ae54dc",
                             "uuid" "3465ad3b-63eb-4718-a347-8d2844ae54dc"}]}
                          {"value" "HDF5",
                           "uuid"
                           [{"value" "1c406abc-104d-4517-96b8-dbbcf515f00f",
                             "uuid" "1c406abc-104d-4517-96b8-dbbcf515f00f"}]}
                          {"value" "ENVI",
                           "uuid"
                           [{"value" "5782afb9-caa8-44c4-8dec-e793d990b75d",
                             "uuid" "5782afb9-caa8-44c4-8dec-e793d990b75d"}]}
                          {"value" "IWRF Time Series Format",
                           "uuid"
                           [{"value" "e1c88c22-0bf2-4798-89fd-fa7d875e4b6c",
                             "uuid" "e1c88c22-0bf2-4798-89fd-fa7d875e4b6c"}]}
                          {"value" "GIF",
                           "uuid"
                           [{"value" "3be6e181-085f-4a53-b7ed-851f1980dc71",
                             "uuid" "3be6e181-085f-4a53-b7ed-851f1980dc71"}]}
                          {"value" "Georeferenced TIFF",
                           "uuid"
                           [{"value" "2544f989-47b8-40dd-ae10-c4abee8198b8",

                             "uuid" "2544f989-47b8-40dd-ae10-c4abee8198b8"}]}
                          {"value" "Incidence Angle File",
                           "uuid"
                           [{"value" "bf085809-cf9f-4c42-b17d-45de69183cf7",
                             "uuid" "bf085809-cf9f-4c42-b17d-45de69183cf7"}]}
                          {"value" "BSQ",
                           "uuid"
                           [{"value" "bd2ced35-e9f5-4ab6-a85d-0f45fac62c00",
                             "uuid" "bd2ced35-e9f5-4ab6-a85d-0f45fac62c00"}]}
                          {"value" "Binary",
                           "uuid"
                           [{"value" "3a3d2a90-5cf6-4ddd-a3c4-c88fa0c6941d",
                             "uuid" "3a3d2a90-5cf6-4ddd-a3c4-c88fa0c6941d"}]}
                          {"value" "MLC",
                           "uuid"
                           [{"value" "93e6035a-9f46-4ece-b365-10e1507b116e",
                             "uuid" "93e6035a-9f46-4ece-b365-10e1507b116e"}]}
                          {"value" "DV",
                           "uuid"
                           [{"value" "b12a5f68-4344-43b4-8e44-8c34e2a638e1",
                             "uuid" "b12a5f68-4344-43b4-8e44-8c34e2a638e1"}]}
                          {"value" "NetCDF-4",
                           "uuid"
                           [{"value" "30ea4e9a-4741-42c9-ad8f-f10930b35294",
                             "uuid" "30ea4e9a-4741-42c9-ad8f-f10930b35294"}]}
                          {"value" "Microsoft Word",
                           "uuid"
                           [{"value" "b20dfc5c-e40d-473b-9e31-789dc6e1ed2e",
                             "uuid" "b20dfc5c-e40d-473b-9e31-789dc6e1ed2e"}]}
                          {"value" "ICARTT",
                           "uuid"
                           [{"value" "23ccdce5-cd51-424e-b82a-67e076a86994",
                             "uuid" "23ccdce5-cd51-424e-b82a-67e076a86994"}]}
                          {"value" "MAT",
                           "uuid"
                           [{"value" "10de1987-5896-42d6-be7c-506fd7ba1f21",
                             "uuid" "10de1987-5896-42d6-be7c-506fd7ba1f21"}]}
                          {"value" "HDF",
                           "uuid"
                           [{"value" "d0314652-e5e1-493b-945b-d712b4d30df1",
                             "uuid" "d0314652-e5e1-493b-945b-d712b4d30df1"}]}
                          {"value" "BigTIFF",
                           "uuid"
                           [{"value" "832bfe62-e5c3-4c89-bee9-4c1b6e80dc9f",
                             "uuid" "832bfe62-e5c3-4c89-bee9-4c1b6e80dc9f"}]}
                          {"value" "CSV",
                           "uuid"
                           [{"value" "465809cc-e76c-4630-8594-bb8bd7a1a380",
                             "uuid" "465809cc-e76c-4630-8594-bb8bd7a1a380"}]}
                          {"value" "NetCDF-CF",
                           "uuid"
                           [{"value" "fa52494f-c855-4d6c-a4dc-46b3090cc6e3",
                             "uuid" "fa52494f-c855-4d6c-a4dc-46b3090cc6e3"}]}
                          {"value" "CRD",
                           "uuid"
                           [{"value" "49028622-39d1-46b1-b89f-0fc2b4923882",
                             "uuid" "49028622-39d1-46b1-b89f-0fc2b4923882"}]}
                          {"value" "ESRI ArcInfo Interchange File (E00)",
                           "uuid"
                           [{"value" "2d0ca5a9-1627-4f0e-bd55-bb3235fd4f5c",
                             "uuid" "2d0ca5a9-1627-4f0e-bd55-bb3235fd4f5c"}]}
                          {"value" "JPEG",
                           "uuid"
                           [{"value" "7443bb2d-1dbb-44d1-bd29-0241d30fbc57",

                             "uuid" "7443bb2d-1dbb-44d1-bd29-0241d30fbc57"}]}
                          {"value" "HDF-EOS",
                           "uuid"
                           [{"value" "d806dc1f-63cc-431e-b1c0-222caa1da54e",
                             "uuid" "d806dc1f-63cc-431e-b1c0-222caa1da54e"}]}
                          {"value" "BUFR",
                           "uuid"
                           [{"value" "d40b49ce-c201-431c-9fdf-4db38f9b97b2",
                             "uuid" "d40b49ce-c201-431c-9fdf-4db38f9b97b2"}]}
                          {"value" "GRIB",
                           "uuid"
                           [{"value" "f4930ca9-6b68-4bb8-adb0-81a571e20a53",
                             "uuid" "f4930ca9-6b68-4bb8-adb0-81a571e20a53"}]}
                          {"value" "ISO Image",
                           "uuid"
                           [{"value" "ab1e1949-c4d0-4af2-9f34-1c7489e30ae6",
                             "uuid" "ab1e1949-c4d0-4af2-9f34-1c7489e30ae6"}]}
                          {"value" "Text File",
                           "uuid"
                           [{"value" "7de32ba8-eb3a-4e02-add3-3d828e46bd57",
                             "uuid" "7de32ba8-eb3a-4e02-add3-3d828e46bd57"}]}
                          {"value" "RINEX",
                           "uuid"
                           [{"value" "48571017-0cc3-4ac7-b9ab-d0df8ed99a6c",
                             "uuid" "48571017-0cc3-4ac7-b9ab-d0df8ed99a6c"}]}
                          {"value" "HGT",
                           "uuid"
                           [{"value" "2c37a52f-c159-4d16-a5c1-36ca833863e1",
                             "uuid" "2c37a52f-c159-4d16-a5c1-36ca833863e1"}]}
                          {"value" "ESRI ASCII Raster",
                           "uuid"
                           [{"value" "9145565a-0f8b-4806-b4a8-296f021be5b8",
                             "uuid" "9145565a-0f8b-4806-b4a8-296f021be5b8"}]}
                          {"value" "DEM",
                           "uuid"
                           [{"value" "3bd030e2-a015-473b-987e-25632cbbd386",
                             "uuid" "3bd030e2-a015-473b-987e-25632cbbd386"}]}
                          {"value" "Georeferenced JPEG",
                           "uuid"
                           [{"value" "de1542d1-3d67-4044-a4b4-c8fb57636213",
                             "uuid" "de1542d1-3d67-4044-a4b4-c8fb57636213"}]}
                          {"value" "LAS",
                           "uuid"
                           [{"value"
                             "181d354f-af90-4aaf-9167-ff3db9f6cb13",
                             "uuid" "181d354f-af90-4aaf-9167-ff3db9f6cb13"}]}
                          {"value" "GeoTIFF",
                           "uuid"
                           [{"value" "668db73b-2a1c-4e92-8e0e-fda3131b4aac",
                             "uuid" "668db73b-2a1c-4e92-8e0e-fda3131b4aac"}]}
                          {"value" "PDF",
                           "uuid"
                           [{"value" "ac392872-1571-4bfd-94dd-81f93d9f1fd0",
                             "uuid" "ac392872-1571-4bfd-94dd-81f93d9f1fd0"}]}
                          {"value" "GRD",
                           "uuid"
                           [{"value" "bb6184eb-1ced-44fb-9668-d57cf1baa2e3",
                             "uuid" "bb6184eb-1ced-44fb-9668-d57cf1baa2e3"}]}]}})

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

    "Testing correct keyword hierarchy returned for temporal keywords."
    :temporal-keywords (:temporal-keywords expected-hierarchy)

    "Testing correct keyword hierarchy returned for spatial keywords."
    :spatial-keywords (:spatial-keywords expected-hierarchy)

    "Testing correct keyword heirarchy returned for granule data format"
    :granule-data-format (:granule-data-format expected-hierarchy)))

(deftest invalid-keywords-test
  (testing "Invalid keyword scheme returns 400 error"
    (is (= {:status 400
            :errors [(str "The keyword scheme [foo] is not supported. Valid schemes are:"
                          " providers, spatial_keywords, granule_data_format, related_urls, iso_topic_categories,"
                          " instruments, science_keywords, concepts, temporal_keywords, platforms,"
                          " archive_centers, data_centers, location_keywords, and projects.")]}
           (search/get-keywords-by-keyword-scheme :foo)))))
