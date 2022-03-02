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
   :platforms {"basis"
               [{"value" "Air-based Platforms",
                 "subfields" ["category"],
                 "category"
                 [{"value" "Aircraft",
                   "subfields" ["short_name"],
                   "short_name"
                   [{"value" "AIRCRAFT",
                     "uuid" "8bce0691-74e9-4363-8d1f-d453a318c62b"}]}
                  {"value" "Uncrewed Aerial Vehicles",
                   "subfields" ["short_name"],
                   "short_name"
                   [{"value" "ALTUS",
                     "uuid" "46392889-f6e2-4b06-8f79-87f2ff9d4349"}]}
                  {"value" "Jet",
                   "subfields" ["short_name"],
                   "short_name"
                   [{"value" "NASA S-3B VIKING",
                     "uuid" "7f1568aa-e87e-4b83-a622-e8f8a03f75bd"}
                    {"value" "A340-600",
                     "subfields" ["long_name"],
                     "long_name"
                     [{"value" "Airbus A340-600",
                       "uuid" "bab77f95-aa34-42aa-9a12-922d1c9fae63"}]}]}
                  {"value" "Propeller",
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
                     "uuid" "80374e6d-fef6-4b11-bcc4-53568a3db220"}]}]}
                {"value" "Space-based Platforms",
                 "subfields" ["category"],
                 "category"
                 [{"value" "Space Stations/Crewed Spacecraft",
                   "subfields" ["sub_category"],
                   "sub_category"
                   [{"value" "Space Station",
                     "subfields" ["short_name"],
                     "short_name"
                     [{"value" "ISS",
                       "subfields" ["long_name"],
                       "long_name"
                       [{"value" "International Space Station",
                         "uuid"
                         "93c5d18c-be62-46c4-9545-42f73a854d85"}]}]}]}
                  {"value" "Earth Observation Satellites",
                   "subfields" ["short_name" "sub_category"],
                   "short_name"
                   [{"value" "Terra",
                     "subfields" ["long_name"],
                     "long_name"
                     [{"value" "Earth Observing System, Terra (AM-1)",
                       "uuid" "80eca755-c564-4616-b910-a4c4387b7c54"}]}
                    {"value" "Aqua",
                     "subfields" ["long_name"],
                     "long_name"
                     [{"value" "Earth Observing System, Aqua",
                       "uuid" "ea7fd15d-190d-43f3-bdd3-75f5d88dc3f8"}]}],
                   "sub_category"
                   [{"value" "DIADEM",
                     "subfields" ["short_name"],
                     "short_name"
                     [{"value" "DIADEM-1D",
                       "uuid" "143a5181-7601-4cc7-96d1-2b1a04b08fa7"}]}
                    {"value" "SMAP-like",
                     "subfields" ["short_name"],
                     "short_name"
                     [{"value" "SMAP",
                       "subfields" ["long_name"],
                       "long_name"
                       [{"value"
                         "Soil Moisture Active and Passive Observatory",
                         "uuid"
                         "7ee03239-24ff-433e-ab7e-8be8b9b2636b"}]}]}
                    {"value"
                     "Defense Meteorological Satellite Program(DMSP)",
                     "subfields" ["short_name"],
                     "short_name"
                     [{"value" "DMSP 5B/F3",
                       "subfields" ["long_name"],
                       "long_name"
                       [{"value"
                         "Defense Meteorological Satellite Program-F3",
                         "uuid"
                         "7ed12e98-95b1-406c-a58a-f4bbfa405269"}]}]}
                    {"value" "Aeros",
                     "subfields" ["short_name"],
                     "short_name"
                     [{"value" "AEROS-1",
                       "uuid"
                       "6164d877-53a0-4ba2-b73a-9dfb363474c9"}]}]}]}]}
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
             {"value" "Diurnal",
              "uuid" "99ef187e-6940-4c10-8d65-00d4426d493b"}
             {"value" "Decadal",
              "uuid" "3d97e993-dc6a-41ff-8a49-3e837c1fc2b1"}
             {"value" "Subannual",
              "uuid" "7afdb8ba-a504-45b6-b301-730e3c69d23a"}
             {"value" "Annual Climatology",
              "uuid" "af931dca-9a7d-4ba9-b40f-2a21e31f2d5b"}
             {"value" "Seasonal",
              "uuid" "7c5420a6-94e2-40ca-9dff-20309090d327"}
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
   :related-urls {"url_content_type"
            [{"value" "DataCenterURL",
              "uuid" "b2df0d8e-d236-4fd2-a4f6-12951b3bb17a",
              "subfields" ["type"],
              "type"
              [{"value" "HOME PAGE",
                "uuid" "05c685ab-8ce0-4b8a-8eba-b15fc6bbddfa"}]}
             {"value" "PublicationURL",
              "uuid" "894edd57-afb3-4bb3-878f-fc245d8b6e82",
              "subfields" ["type"],
              "type"
              [{"value" "VIEW RELATED INFORMATION",
                "uuid" "5ec1bb9d-0efc-4099-9b31-ec791bbd8145",
                "subfields" ["subtype"],
                "subtype"
                [{"value" "PI DOCUMENTATION",
                  "uuid" "367f8b8a-e57e-4c49-b971-0b5c6a484186"}
                 {"value" "USER'S GUIDE",
                  "uuid" "d1996d91-e824-4b24-b94e-3aae4543b63b"}
                 {"value" "PRODUCTION HISTORY",
                  "uuid" "0b597285-eaac-4cbd-94cc-d87ae8046681"}
                 {"value" "PRODUCT USAGE",
                  "uuid" "1132a0fc-888b-4332-ad0a-dc5c6e615afa"}
                 {"value" "DELIVERABLES CHECKLIST",
                  "uuid" "be0460d8-ca8e-45c8-b637-8fb4ce5a5e97"}
                 {"value" "ANOMALIES",
                  "uuid" "914cbb7e-5b20-4bcd-86e3-ffcfa26f0a73"}
                 {"value" "MICRO ARTICLE",
                  "uuid" "4f3c0b04-1fe6-4e11-994a-9cc4afd09ce0"}
                 {"value" "DATA QUALITY",
                  "uuid" "0eba3253-8eb7-4e43-9627-9cff48775e27"}
                 {"value" "SCIENCE DATA PRODUCT VALIDATION",
                  "uuid" "15b0a4c4-b39d-48f5-92d2-905e45e6dc6a"}
                 {"value"
                  "ALGORITHM THEORETICAL BASIS DOCUMENT (ATBD)",
                  "uuid" "fd01f7ec-fdf6-4440-b974-75f12fb4ec5f"}
                 {"value" "DATA RECIPE",
                  "uuid" "547600e9-b60a-44eb-b14b-5c6e1f2c094e"}
                 {"value" "CASE STUDY",
                  "uuid" "3112d474-b44f-4af1-8266-c3dd6d28220f"}
                 {"value" "READ-ME",
                  "uuid" "aa3cea98-b20a-4de8-8f22-7a8b30784625"}
                 {"value" "HOW-TO",
                  "uuid" "7ebd73e5-b0aa-4cf2-ace5-1d3890c2c3ce"}
                 {"value" "PRODUCT QUALITY ASSESSMENT",
                  "uuid" "b7ed88ce-3f04-40ea-863e-ac58bd048ff3"}
                 {"value"
                  "INSTRUMENT/SENSOR CALIBRATION DOCUMENTATION",
                  "uuid" "fc3c1abb-92c1-49c2-90d4-161c70cff44a"}
                 {"value"
                  "SCIENCE DATA PRODUCT SOFTWARE DOCUMENTATION",
                  "uuid" "e8e6e972-832f-4501-a721-4108f33332d6"}
                 {"value" "REQUIREMENTS AND DESIGN",
                  "uuid" "86b8b121-d710-4c5b-84b0-7b40717f6c76"}
                 {"value" "USER FEEDBACK PAGE",
                  "uuid" "ab2fce71-e5f9-4ba6-bfb1-bc428a8b7dd8"}
                 {"value" "IMPORTANT NOTICE",
                  "uuid" "2af2cfc4-9390-43da-8fa8-1f272e8ee0b0"}
                 {"value" "PUBLICATIONS",
                  "uuid" "13a4deec-bd22-4864-9804-77fac181f484"}
                 {"value" "GENERAL DOCUMENTATION",
                  "uuid" "aebf20eb-39c7-4f4f-aecf-a628f703867b"}
                 {"value" "PRODUCT HISTORY",
                  "uuid" "b292f51f-d2b4-4e65-84a9-e50306238989"}
                 {"value" "DATA PRODUCT SPECIFICATION",
                  "uuid" "415cfe86-4d71-4100-8f35-6404caec1c91"}
                 {"value" "PROCESSING HISTORY",
                  "uuid" "7cfa5214-7f69-4355-b259-286be88f25d1"}
                 {"value" "ALGORITHM DOCUMENTATION",
                  "uuid" "fcc9411c-a1c9-415d-a16c-75c42f2cec45"}
                 {"value" "DATA CITATION POLICY",
                  "uuid" "40cf5001-15ec-4d9a-913c-bb323f2974fc"}]}]}
             {"value" "DataContactURL",
              "uuid" "65373de8-3fb3-4882-a8ca-cfe23a4ff58e",
              "subfields" ["type"],
              "type"
              [{"value" "HOME PAGE",
                "uuid" "e5803df8-c802-4f3f-96f5-53e534835887"}]}
             {"value" "VisualizationURL",
              "uuid" "731f4e5c-d200-4c56-9daa-e6fad17415ef",
              "subfields" ["type"],
              "type"
              [{"value" "GET RELATED VISUALIZATION",
                "uuid" "dd2adc64-c7bd-4dbf-976b-f0496966817c",
                "subfields" ["subtype"],
                "subtype"
                [{"value" "WORLDVIEW",
                  "uuid" "eeff646c-6faf-468e-a0ab-ff78fc6f86f9"}
                 {"value" "MAP",
                  "uuid" "e6f9524a-e4bc-460a-bdf3-a5e8f0e921a9"}
                 {"value" "GIOVANNI",
                  "uuid" "690210ef-4cf8-4645-b68d-921466bba6a2"}
                 {"value" "SOTO",
                  "uuid" "389ab1cf-fbf4-49ee-bf22-e40643fa00f6"}]}]}
             {"value" "DistributionURL",
              "uuid" "d25982b9-92e9-4ec0-ab44-48e79ecbe137",
              "subfields" ["type"],
              "type"
              [{"value" "GET CAPABILITIES",
                "uuid" "2892b502-2c66-42d5-af3d-bcddb57d9195",
                "subfields" ["subtype"],
                "subtype"
                [{"value" "GIBS",
                  "uuid" "ca5440d6-a9e4-416b-8e35-c4769f664b95"}
                 {"value" "OpenSearch",
                  "uuid" "09c6e3ea-f8e0-4052-8b41-c3b1269799ed"}]}
               {"value" "GET DATA VIA DIRECT ACCESS",
                "uuid" "172cd72d-30d3-4795-8660-dc38820faba0"}
               {"value" "USE SERVICE API",
                "uuid" "d117cf5c-8d23-4662-be62-7b883cecb219",
                "subfields" ["subtype"],
                "subtype"
                [{"value" "OpenSearch",
                  "uuid" "89b80cbd-027f-4eab-823f-ae00c268f5bf"}
                 {"value" "WEB MAP TILE SERVICE (WMTS)",
                  "uuid" "7aac9f91-20c4-4234-9153-e850c8ace8a9"}
                 {"value" "SERVICE CHAINING",
                  "uuid" "411d2781-822c-4c48-8d5b-4b51b100ce0a"}
                 {"value" "WEB COVERAGE SERVICE (WCS)",
                  "uuid" "029540bb-7f5c-44ba-8578-61e2f858be60"}
                 {"value" "GRADS DATA SERVER (GDS)",
                  "uuid" "5c0cd574-0255-4202-9b5b-3da8711b7ed7"}
                 {"value" "MAP SERVICE",
                  "uuid" "0c3aa5c6-f1f9-4c16-aa96-30672028d26c"}
                 {"value" "OPENDAP DATA",
                  "uuid" "eae7a041-b004-48df-8d4e-d758969e3185"}
                 {"value" "WEB FEATURE SERVICE (WFS)",
                  "uuid" "c4d406e6-7a34-42aa-bd79-f7f9265cc7bd"}
                 {"value" "THREDDS DATA",
                  "uuid" "77cae7cb-4676-4c69-a88b-d78971496f97"}
                 {"value" "WEB MAP SERVICE (WMS)",
                  "uuid" "b0e2089c-3c1d-4c12-b833-e07365a4038e"}
                 {"value" "TABULAR DATA STREAM (TDS)",
                  "uuid" "7b664934-70c4-4694-b8c1-416e7c91afb9"}]}
               {"value" "DOWNLOAD SOFTWARE",
                "uuid" "ca8b62c9-5f31-40bd-92a9-8d30081309e2",
                "subfields" ["subtype"],
                "subtype"
                [{"value" "MOBILE APP",
                  "uuid" "5fedeefd-2609-488c-a897-fe168cae34dd"}]}
               {"value" "GOTO WEB TOOL",
                "uuid" "ffccf1c0-f25d-4747-ac4a-f09444383031",
                "subfields" ["subtype"],
                "subtype"
                [{"value" "LIVE ACCESS SERVER (LAS)",
                  "uuid" "20ab6d52-f5a7-439c-a044-6ef2452a2838"}
                 {"value" "SUBSETTER",
                  "uuid" "bf37a20c-8e99-4187-b91b-3ea254f006f9"}
                 {"value" "HITIDE",
                  "uuid" "a7225578-b398-4222-a7a0-8f5175338ddf"}
                 {"value" "MAP VIEWER",
                  "uuid" "c1c61697-b4bd-467c-9db4-5bd0115545a3"}
                 {"value" "SIMPLE SUBSET WIZARD (SSW)",
                  "uuid" "6ffc54ea-001a-4c03-afff-5086b2da8f59"}]}
               {"value" "GET DATA",
                "uuid" "750f6c61-0f15-4185-94d8-c029dec04bc5",
                "subfields" ["subtype"],
                "subtype"
                [{"value" "GIOVANNI",
                  "uuid" "2e869d22-88fe-43dc-852d-9f50c911ad02"}
                 {"value" "NOAA CLASS",
                  "uuid" "a36f1716-a310-41f5-b4d8-6c6a5fc933d9"}
                 {"value" "MIRADOR",
                  "uuid" "9b05d2a3-9a5a-425c-b6ed-59e0e56814fa"}
                 {"value" "USGS EARTH EXPLORER",
                  "uuid" "4485a5b6-d84c-4c98-980e-164863ca518f"}
                 {"value" "Subscribe",
                  "uuid" "38219044-ad26-4a32-98c0-dca8ad3cd29a"}
                 {"value" "CERES Ordering Tool",
                  "uuid" "93bc7186-2634-49ae-a8da-312d893ef15e"}
                 {"value" "Earthdata Search",
                  "uuid" "5b8013bb-0b15-4811-8aa3-bfc108c3a041"}
                 {"value" "Sub-Orbital Order Tool",
                  "uuid" "459decfe-53ee-41ce-b608-d7578b04ef7b"}
                 {"value" "DATA TREE",
                  "uuid" "3c2a68a6-d8c2-4f14-8208-e57a4446ad71"}
                 {"value" "Order",
                  "uuid" "bd91340d-a8b3-4c01-b262-71e50fe69c83"}
                 {"value" "LAADS",
                  "uuid" "0b50c12d-a6ae-4d63-b42b-d99bf7aa2da0"}
                 {"value" "DATACAST URL",
                  "uuid" "2fc3797c-71b5-4d01-8ae1-d5634ec625ce"}
                 {"value" "LANCE",
                  "uuid" "aa11ac15-3042-4634-b47e-acc368f608bd"}
                 {"value" "VIRTUAL COLLECTION",
                  "uuid" "78d28911-a87c-40a0-ada2-c14f7cfb0834"}
                 {"value" "MODAPS",
                  "uuid" "26431afd-cb37-4772-9e97-3a36f6dff32d"}
                 {"value" "PORTAL",
                  "uuid" "49be0345-a6af-4608-98d8-9b2343e60077"}
                 {"value" "IceBridge Portal",
                  "uuid" "3c60609c-d48d-47c4-b069-43951fa0aea3"}
                 {"value" "APPEEARS",
                  "uuid" "6b8f0bfc-d9a4-4af1-9d94-6dcfade03bda"}
                 {"value" "DATA COLLECTION BUNDLE",
                  "uuid" "444f03b4-e588-42da-aee6-73028f3c45be"}
                 {"value" "NOMADS",
                  "uuid" "b434314f-949f-4c26-be57-2ea4c7f03643"}
                 {"value" "VERTEX",
                  "uuid" "5520d1de-f7f5-4798-9ebc-698885805489"}
                 {"value" "DIRECT DOWNLOAD",
                  "uuid" "8e33a2dd-df13-4079-8636-391abb5344c6"}
                 {"value" "GoLIVE Portal",
                  "uuid" "9bfb0f20-189e-411b-a678-768bf3fa256e"}
                 {"value" "EOSDIS DATA POOL",
                  "uuid" "3779ec72-c1e0-4a0f-aff8-8e2a2a7af486"}]}]}
             {"value" "CollectionURL",
              "uuid" "c7bbd6c7-8b0a-46ed-a428-a2f0453ed69e",
              "subfields" ["type"],
              "type"
              [{"value" "EXTENDED METADATA",
                "uuid" "3c9d4493-22fd-48a8-9af5-bf0d16b7ede5",
                "subfields" ["subtype"],
                "subtype"
                [{"value" "DMR++ MISSING DATA",
                  "uuid" "4cc17021-b9cc-4b3f-a4f1-f05f7c1aeb2d"}
                 {"value" "DMR++",
                  "uuid" "f02b0c6a-7fd9-473d-a1cb-a6482e8daa61"}]}
               {"value" "PROJECT HOME PAGE",
                "uuid" "6e72d128-7d28-4bd0-bac0-8c5ffd8b31f1"}
               {"value" "DATA SET LANDING PAGE",
                "uuid" "8826912b-c89e-4810-b446-39b98b5d937c"}
               {"value" "PROFESSIONAL HOME PAGE",
                "uuid" "f00cf885-8fc5-42ca-a70e-1689530f00cf"}]}]}
   :spatial-keywords {"category"
                      [{"value" "GEOGRAPHIC REGION",
                        "uuid" "204270d9-8039-4768-851e-63635af5fb65",
                        "subfields" ["type"],
                        "type"
                        [{"value" "ARCTIC",
                          "uuid" "d40d9651-aa19-4b2c-9764-7371bb64b9a7"}]}
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
                              "9b0a194d-d617-4fed-9625-df176319892d"}]}]}]}
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
                            [{"value" "MEDITERRANEAN SEA",
                              "subfields" ["subregion_3"],
                              "subregion_3"
                              [{"value" "ADRIATIC SEA",
                                "uuid" "7b93c892-2fc4-417b-a4da-5c8a2fca361b"}]}
                             {"value" "BALTIC SEA",
                              "uuid"
                              "41cd228c-4677-4900-9507-70144d8b50bc"}]}]}]}]}
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
   :granule-data-format {"short_name"
                         [{"value" "PNG"
                           "uuid" [{"value" "4c406abc-104d-4517-96b8-dbbcf515f00f"
                                    "uuid" "4c406abc-104d-4517-96b8-dbbcf515f00f"}]}
                          {"value" "ASCII"
                           "uuid" [{"uuid" "8e128326-b9cb-44c7-9e6b-4bd950a08753"
                                    "value" "8e128326-b9cb-44c7-9e6b-4bd950a08753"}]}
                          {"value" "NETCDF-CF"
                           "uuid" [{"uuid" "3c406abc-104d-4517-96b8-dbbcf515f00f"
                                    "value" "3c406abc-104d-4517-96b8-dbbcf515f00f"}]}
                          {"value" "HDF5"
                           "uuid" [{"uuid" "1c406abc-104d-4517-96b8-dbbcf515f00f"
                                    "value" "1c406abc-104d-4517-96b8-dbbcf515f00f"}]}
                          {"value" "CSV"
                           "uuid" [{"value" "465809cc-e76c-4630-8594-bb8bd7a1a380"
                                    "uuid" "465809cc-e76c-4630-8594-bb8bd7a1a380"}]}
                          {"value" "HDF4"
                           "uuid" [{"value" "e5c126f8-0435-4cef-880f-72a1d2d792f2"
                                    "uuid" "e5c126f8-0435-4cef-880f-72a1d2d792f2"}]}
                          {"value" "JPEG"
                           "uuid" [{"value" "7443bb2d-1dbb-44d1-bd29-0241d30fbc57"
                                    "uuid" "7443bb2d-1dbb-44d1-bd29-0241d30fbc57"}]}
                          {"value" "HTML"
                           "uuid" [{"value" "2c406abc-104d-4517-96b8-dbbcf515f00f"
                                    "uuid" "2c406abc-104d-4517-96b8-dbbcf515f00f"}]}
                          {"value" "ZIP"
                           "uuid" [{"uuid" "5c406abc-104d-4517-96b8-dbbcf515f00f"
                                    "value" "5c406abc-104d-4517-96b8-dbbcf515f00f"}]}
                          {"value" "netCDF-4"
                           "uuid" [{"value" "30ea4e9a-4741-42c9-ad8f-f10930b35294"
                                    "uuid" "30ea4e9a-4741-42c9-ad8f-f10930b35294"}]}]}
    :mime-type {"mime_type"
                [{"value" "text/css"
                  "uuid"
                  [{"value" "3195dfce-51db-4b40-aadb-808b43573743"
                    "uuid" "3195dfce-51db-4b40-aadb-808b43573743"}]}
                  {"value" "application/x-vnd.iso.19139-2+xml"
                   "uuid"
                   [{"value" "c1a8dbb7-312d-4481-998e-58d126b32080"
                     "uuid" "c1a8dbb7-312d-4481-998e-58d126b32080"}]}
                  {"value" "application/xml"
                   "uuid"
                   [{"value" "dd6c5cea-4100-4973-9ba9-659fdd7fd608"
                     "uuid" "dd6c5cea-4100-4973-9ba9-659fdd7fd608"}]}
                  {"value" "image/jpeg"
                   "uuid"
                   [{"value" "3f697f52-6a1c-4e2c-bd4b-13aaaf45f2e6"
                     "uuid" "3f697f52-6a1c-4e2c-bd4b-13aaaf45f2e6"}]}
                  {"value" "application/x-netcdf"
                   "uuid"
                   [{"value" "2b192915-32a8-4b68-a720-8ca8a84f04ca"
                     "uuid" "2b192915-32a8-4b68-a720-8ca8a84f04ca"}]}
                  {"value" "application/zip"
                   "uuid"
                   [{"value" "4e5db77b-bc1d-4f7c-9f13-e3e54e0b2e3b"
                     "uuid" "4e5db77b-bc1d-4f7c-9f13-e3e54e0b2e3b"}]}
                  {"value" "application/tar"
                   "uuid"
                   [{"value" "84ef762f-e348-42a6-981c-563822a47806"
                     "uuid" "84ef762f-e348-42a6-981c-563822a47806"}]}
                  {"value" "application/vnd.ms-excel"
                   "uuid"
                   [{"value" "7c99ff72-5239-424d-a0bf-9712c33ea76d"
                     "uuid" "7c99ff72-5239-424d-a0bf-9712c33ea76d"}]}
                  {"value" "application/msword"
                   "uuid"
                   [{"value" "c79a0e11-2774-4cf3-a194-45b9e58a93fd"
                     "uuid" "c79a0e11-2774-4cf3-a194-45b9e58a93fd"}]}
                  {"value" "text/javascript"
                   "uuid"
                   [{"value" "40cb0bdd-67f4-43c8-aa57-2bdc260e6950"
                     "uuid" "40cb0bdd-67f4-43c8-aa57-2bdc260e6950"}]}
                  {"value" "application/tar+zip"
                   "uuid"
                   [{"value" "17e82b7c-498d-4d69-993c-fd691aa25ce8"
                     "uuid" "17e82b7c-498d-4d69-993c-fd691aa25ce8"}]}
                  {"value" "application/x-hdf5"
                   "uuid"
                   [{"value" "4e80047b-c50b-4805-ac68-789dbc38803f"
                     "uuid" "4e80047b-c50b-4805-ac68-789dbc38803f"}]}
                  {"value" "application/gzip"
                   "uuid"
                   [{"value" "a8ee535a-8bc8-46fd-8b97-917bd7ea7666"
                     "uuid" "a8ee535a-8bc8-46fd-8b97-917bd7ea7666"}]}
                  {"value" "application/octet-stream"
                   "uuid"
                   [{"value" "b77e64ef-ce80-4dab-b552-c6062990a6e0"
                     "uuid" "b77e64ef-ce80-4dab-b552-c6062990a6e0"}]}
                  {"value" "text/markdown"
                   "uuid"
                   [{"value" "b403039f-a107-4a84-88a1-29e4d1b30b0b"
                     "uuid" "b403039f-a107-4a84-88a1-29e4d1b30b0b"}]}
                  {"value" "text/csv"
                   "uuid"
                   [{"value" "2065aabb-9beb-4c84-8ad7-0e16cfed17cf"
                     "uuid" "2065aabb-9beb-4c84-8ad7-0e16cfed17cf"}]}
                  {"value" "application/vnd.google-earth.kml+xml"
                   "uuid"
                   [{"value" "80045dcb-18ee-463a-8baf-ffcabed510ea"
                     "uuid" "80045dcb-18ee-463a-8baf-ffcabed510ea"}]}
                  {"value" "image/bmp"
                   "uuid"
                   [{"value" "b7687b8f-7a24-4150-bd9d-28e0d53f7554"
                     "uuid" "b7687b8f-7a24-4150-bd9d-28e0d53f7554"}]}
                  {"value" "application/x-bufr"
                   "uuid"
                   [{"value" "e384b8a8-8cec-4230-9ebe-4db76bbef706"
                     "uuid" "e384b8a8-8cec-4230-9ebe-4db76bbef706"}]}
                  {"value" "application/tar+gzip"
                   "uuid"
                   [{"value" "43ca8ee0-04a5-4020-b0ec-998ec0e0f30e"
                     "uuid" "43ca8ee0-04a5-4020-b0ec-998ec0e0f30e"}]}
                  {"value" "application/pdf"
                   "uuid"
                   [{"value" "627269ae-ba93-492e-8c31-cc4de1d69810"
                     "uuid" "627269ae-ba93-492e-8c31-cc4de1d69810"}]}
                  {"value" "application/opensearchdescription+xml"
                   "uuid"
                   [{"value" "07bcc60e-1551-44d9-b87e-7c260d230ecb"
                     "uuid" "07bcc60e-1551-44d9-b87e-7c260d230ecb"}]}
                  {"value" "application/x-hdfeos"
                   "uuid"
                   [{"value" "b1eac265-2b00-4c39-a429-797c13a2c640"
                     "uuid" "b1eac265-2b00-4c39-a429-797c13a2c640"}]}
                  {"value" "text/html"
                   "uuid"
                   [{"value" "415a10b5-7286-4195-a88e-00c7b995b7d0"
                     "uuid" "415a10b5-7286-4195-a88e-00c7b995b7d0"}]}
                  {"value" "application/vnd.opendap.dap4.dmrpp+xml"
                   "uuid"
                   [{"value" "b26761fa-8d8e-4bd8-a8ba-db6575554ad7"
                     "uuid" "b26761fa-8d8e-4bd8-a8ba-db6575554ad7"}]}
                  {"value" "application/gml+xml"
                   "uuid"
                   [{"value" "40bdf6e5-780c-43e2-ab8e-e5dfae4bd779"
                     "uuid" "40bdf6e5-780c-43e2-ab8e-e5dfae4bd779"}]}
                  {"value" "image/png"
                   "uuid"
                   [{"value" "edb9e800-ec31-4d5c-848d-c548fd151db2"
                     "uuid" "edb9e800-ec31-4d5c-848d-c548fd151db2"}]}
                  {"value" "application/x-hdf"
                   "uuid"
                   [{"value" "b0a3e733-4d1b-486f-b56c-c405a5e4367b"
                     "uuid" "b0a3e733-4d1b-486f-b56c-c405a5e4367b"}]}
                  {"value" "text/plain"
                   "uuid"
                   [{"value" "fea4e0a7-d794-481d-9915-52f1be226714"
                     "uuid" "fea4e0a7-d794-481d-9915-52f1be226714"}]}
                  {"value" "application/json"
                   "uuid"
                   [{"value" "8542dd4a-a11b-475d-8d46-cad785a7f510"
                     "uuid" "8542dd4a-a11b-475d-8d46-cad785a7f510"}]}
                  {"value" "image/vnd.collada+xml"
                   "uuid"
                   [{"value" "d3ef6fe7-b6cd-45b4-9a27-d42fa3289116"
                     "uuid" "d3ef6fe7-b6cd-45b4-9a27-d42fa3289116"}]}
                  {"value" "image/tiff"
                   "uuid"
                   [{"value" "3e048f9e-8f93-4f0c-9f0b-20bafb909c68"
                     "uuid" "3e048f9e-8f93-4f0c-9f0b-20bafb909c68"}]}
                  {"value" "application/vnd.google-earth.kmz"
                   "uuid"
                   [{"value" "f7328bf5-8ef2-4f95-a4e0-6fb16d122237"
                     "uuid" "f7328bf5-8ef2-4f95-a4e0-6fb16d122237"}]}
                  {"value" "text/xml"
                   "uuid"
                   [{"value" "091b6afc-ab75-4790-8e71-3b32ae8bd0a4"
                     "uuid" "091b6afc-ab75-4790-8e71-3b32ae8bd0a4"}]}
                  {"value" "application/x-tar-gz"
                   "uuid"
                   [{"value" "5e70beda-396e-4cc8-bdd5-70dfc8a1142e"
                     "uuid" "5e70beda-396e-4cc8-bdd5-70dfc8a1142e"}]}
                  {"value" "image/gif"
                   "uuid"
                   [{"value" "ad61b259-8131-4e0e-aac8-a800a0a51ca6"
                     "uuid" "ad61b259-8131-4e0e-aac8-a800a0a51ca6"}]}]}})

(deftest get-keywords-test
  (util/are3
    [keyword-scheme expected-keywords]
    (is (= {:status 200 :results expected-keywords}
           (search/get-keywords-by-keyword-scheme keyword-scheme)))

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

    "Testing correct keyword hierarchy of 3 levels is returned for related URLs."
    :related-urls (:related-urls expected-hierarchy)

    "Testing correct keyword hierarchy returned for temporal keywords."
    :temporal-keywords (:temporal-keywords expected-hierarchy)

    "Testing correct keyword hierarchy returned for spatial keywords."
    :spatial-keywords (:spatial-keywords expected-hierarchy)

    "Testing correct keyword hierarchy returned for mimetype"
    :mime-type (:mime-type expected-hierarchy)

    "Testing correct keyword heirarchy returned for granule data format"
    :granule-data-format (:granule-data-format expected-hierarchy)))

(deftest search-parameter-filter-not-supported
  (testing "Adding search parameter filter returns 400 error"
    (is (= {:status 400
            :errors [ "Search parameter filters are not supported: [{:platform \"TRIMM\"}]" ]}
           (search/get-keywords-by-keyword-scheme :instruments "?platform=TRIMM&pretty=true")))))

(deftest invalid-keywords-test
  (testing "Invalid keyword scheme returns 400 error"
    (is (= {:status 400
            :errors [(str "The keyword scheme [foo] is not supported. Valid schemes are:"
                          " providers, measurement_name, spatial_keywords, spatial_keywords_old, granule_data_format,"
                          " mime_type, related_urls, iso_topic_categories,"
                          " instruments, science_keywords, concepts, temporal_keywords, platforms,"
                          " archive_centers, data_centers, location_keywords, and projects.")]}
           (search/get-keywords-by-keyword-scheme :foo)))))
