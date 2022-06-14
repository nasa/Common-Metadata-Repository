(ns cmr.system-int-test.search.facets.facet-responses
  "Contains vars with large facet response examples used in tests.")

(def expected-v2-facets-apply-links
  "Expected facets to be returned in the facets v2 response. The structure of the v2 facet response
  is documented in https://wiki.earthdata.nasa.gov/display/CMR/Updated+facet+response. This response
  is generated for the search http://localhost:3003/collections.json?page_size=0&include_facets=v2
  without any query parameters selected and with a couple of collections that have science keywords,
  projects, platforms, instruments, organizations, and processing levels in their metadata. This
  tests that the applied parameter is set to false correctly and that the generated links specify a
  a link to add each search parameter to apply that value to a search."
  {:title "Browse Collections",
   :type "group",
   :has_children true,
   :children
   [{:title "Keywords",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "Popular",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?page_size=0&include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Popular"},
       :has_children true}
      {:title "Topic1",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?page_size=0&include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children true}]},
      {:title "Platforms",
       :type "group",
       :applied false,
       :has_children true,
       :children
       [{:title "Space-based Platforms",
         :type "filter",
         :applied false,
         :count 2,
         :links
         {:apply
          "http://localhost:3003/collections.json?page_size=0&include_facets=v2&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
         :has_children true}]},
    {:title "Instruments",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "ATM",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?page_size=0&include_facets=v2&instrument_h%5B%5D=ATM"},
       :has_children false}
      {:title "lVIs",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?page_size=0&include_facets=v2&instrument_h%5B%5D=lVIs"},
       :has_children false}]}
    {:title "Data Format",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "NetCDF",
       :type "filter",
       :applied false,
       :count 1,
       :links
       {:apply
        "http://localhost:3003/collections.json?page_size=0&include_facets=v2&granule_data_format_h%5B%5D=NetCDF"},
       :has_children false}]}
    {:title "Organizations",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "DOI/USGS/CMG/WHSC",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?page_size=0&include_facets=v2&data_center_h%5B%5D=DOI%2FUSGS%2FCMG%2FWHSC"},
       :has_children false}]}
    {:title "Projects",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "proj1",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?page_size=0&include_facets=v2&project_h%5B%5D=proj1"},
       :has_children false}
      {:title "PROJ2",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?page_size=0&include_facets=v2&project_h%5B%5D=PROJ2"},
       :has_children false}]}
    {:title "Processing Levels",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "PL1",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?page_size=0&include_facets=v2&processing_level_id_h%5B%5D=PL1"},
       :has_children false}]}
    {:title "Measurements",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "Measurement1",
       :type "filter",
       :applied false,
       :count 1,
       :links
       {:apply
        "http://localhost:3003/collections.json?page_size=0&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1"},
       :has_children true}
      {:title "Measurement2",
       :type "filter",
       :applied false,
       :count 1,
       :links
       {:apply
        "http://localhost:3003/collections.json?page_size=0&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement2"},
       :has_children true}]},
    {:title "Tiling System",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "MISR",
       :type "filter",
       :applied false,
       :count 1,
       :links
       {:apply
        "http://localhost:3003/collections.json?page_size=0&include_facets=v2&two_d_coordinate_system_name%5B%5D=MISR"},
       :has_children false}]}]})

(def expected-v2-facets-remove-links
  "Expected facets to be returned in the facets v2 response for a search that includes all of the
  v2 facet terms: science keywords, projects, platforms, instruments, organizations, and processing
  levels. When running the applicable tests there are a couple of collections which contain these
  fields so that the search parameters are applied. This tests that the applied parameter is set
  to true correctly and that the generated links specify a link to remove each search parameter."
  {:title "Browse Collections",
         :type "group",
         :has_children true,
         :children
         [{:title "Data Format",
           :type "group",
           :applied false,
           :has_children true,
           :children
           [{:title "NetCDF",
             :type "filter",
             :applied false,
             :count 1,
             :links
             {:apply
              "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&granule_data_format_h%5B%5D=NetCDF&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bshort_name%5D=DIADEM-1D&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
             :has_children false}]}
          {:title "Processing Levels",
           :type "group",
           :applied true,
           :has_children true,
           :children
           [{:title "PL1",
             :type "filter",
             :applied true,
             :count 1,
             :links
             {:remove
              "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bshort_name%5D=DIADEM-1D&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
             :has_children false}]}
          {:title "Keywords",
           :type "group",
           :applied true,
           :has_children true,
           :children
           [{:title "Popular",
             :type "filter",
             :applied false,
             :count 1,
             :links
             {:apply
              "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&science_keywords_h%5B1%5D%5Btopic%5D=Popular&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bshort_name%5D=DIADEM-1D&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
             :has_children true}
            {:title "Topic1",
             :type "filter",
             :applied true,
             :count 1,
             :links
             {:remove
              "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bshort_name%5D=DIADEM-1D&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
             :has_children true,
             :children
             [{:title "Term1",
               :type "filter",
               :applied true,
               :count 1,
               :links
               {:remove
                "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bshort_name%5D=DIADEM-1D&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
               :has_children true,
               :children
               [{:title "Level1-1",
                 :type "filter",
                 :applied true,
                 :count 1,
                 :links
                 {:remove
                  "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bshort_name%5D=DIADEM-1D&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
                 :has_children true,
                 :children
                 [{:title "Level1-2",
                   :type "filter",
                   :applied true,
                   :count 1,
                   :links
                   {:remove
                    "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bshort_name%5D=DIADEM-1D&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
                   :has_children true,
                   :children
                   [{:title "Level1-3",
                     :type "filter",
                     :applied true,
                     :count 1,
                     :links
                     {:remove
                      "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bshort_name%5D=DIADEM-1D&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
                     :has_children true,
                     :children
                     [{:title "Detail1",
                       :type "filter",
                       :applied false,
                       :count 1,
                       :links
                       {:apply
                        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&science_keywords_h%5B0%5D%5Bdetailed_variable%5D=Detail1&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bshort_name%5D=DIADEM-1D&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
                       :has_children false}]}]}]}]}]}]}
          {:title "Platforms",
           :type "group",
           :applied true,
           :has_children true,
           :children
           [{:title "Space-based Platforms",
             :type "filter",
             :applied true,
             :count 1,
             :links
             {:remove
              "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
             :has_children true,
             :children
             [{:title "Earth Observation Satellites",
               :type "filter",
               :applied true,
               :count 1,
               :links
               {:remove
                "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
               :has_children true,
               :children
               [{:title
                 "Defense Meteorological Satellite Program(DMSP)",
                 :type "filter",
                 :applied false,
                 :count 1,
                 :links
                 {:apply
                  "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&platforms_h%5B1%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B1%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platforms_h%5B1%5D%5Bsub_category%5D=Defense+Meteorological+Satellite+Program%28DMSP%29&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bshort_name%5D=DIADEM-1D&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
                 :has_children true}
                {:title "DIADEM",
                 :type "filter",
                 :applied true,
                 :count 1,
                 :links
                 {:remove
                  "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
                 :has_children true,
                 :children
                 [{:title "DIADEM-1D",
                   :type "filter",
                   :applied true,
                   :count 1,
                   :links
                   {:remove
                    "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
                   :has_children false}]}]}]}]}
          {:title "Instruments",
           :type "group",
           :applied true,
           :has_children true,
           :children
           [{:title "ATM",
             :type "filter",
             :applied true,
             :count 1,
             :links
             {:remove
              "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&variables_h%5B0%5D%5Bvariable%5D=Variable1&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bshort_name%5D=DIADEM-1D&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
             :has_children false}
            {:title "lVIs",
             :type "filter",
             :applied false,
             :count 1,
             :links
             {:apply
              "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&instrument_h%5B%5D=lVIs&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bshort_name%5D=DIADEM-1D&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
             :has_children false}]}
          {:title "Organizations",
           :type "group",
           :applied true,
           :has_children true,
           :children
           [{:title "DOI/USGS/CMG/WHSC",
             :type "filter",
             :applied true,
             :count 1,
             :links
             {:remove
              "http://localhost:3003/collections.json?science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bshort_name%5D=DIADEM-1D&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
             :has_children false}]}
          {:title "Projects",
           :type "group",
           :applied true,
           :has_children true,
           :children
           [{:title "proj1",
             :type "filter",
             :applied true,
             :count 1,
             :links
             {:remove
              "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bshort_name%5D=DIADEM-1D&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
             :has_children false}
            {:title "PROJ2",
             :type "filter",
             :applied false,
             :count 1,
             :links
             {:apply
              "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&project_h%5B%5D=PROJ2&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bshort_name%5D=DIADEM-1D&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
             :has_children false}]}
          {:title "Measurements",
           :type "group",
           :applied true,
           :has_children true,
           :children
           [{:title "Measurement1",
             :type "filter",
             :applied true,
             :count 1,
             :links
             {:remove
              "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bshort_name%5D=DIADEM-1D&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
             :has_children true,
             :children
             [{:title "Variable1",
               :type "filter",
               :applied true,
               :count 1,
               :links
               {:remove
                "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bshort_name%5D=DIADEM-1D&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
               :has_children false}]}
            {:title "Measurement2",
             :type "filter",
             :applied false,
             :count 1,
             :links
             {:apply
              "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&variables_h%5B1%5D%5Bmeasurement%5D=Measurement2&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bshort_name%5D=DIADEM-1D&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
             :has_children true}]}
          {:title "Tiling System",
           :type "group",
           :applied false,
           :has_children true,
           :children
           [{:title "MISR",
             :type "filter",
             :applied false,
             :count 1,
             :links
             {:apply
              "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&two_d_coordinate_system_name%5B%5D=MISR&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bshort_name%5D=DIADEM-1D&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
             :has_children false}]}]})

(def partial-v2-facets
  "Expected facet results with some facets present and some not included because there were not any
  matching collections for those facets. This tests that the generated facets correctly do not
  include any facets in the response which we do not apply for the search. In this example the
  projects, platforms, instruments, and organizations are omitted from the facet response."
  {:title "Browse Collections",
   :type "group",
   :has_children true,
   :children
   [{:title "Keywords",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "Popular",
       :type "filter",
       :applied false,
       :count 1,
       :links
       {:apply
        "http://localhost:3003/collections.json?page_size=0&include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Popular"},
       :has_children true}]}
    {:title "Processing Levels",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "PL1",
       :type "filter",
       :applied false,
       :count 1,
       :links
       {:apply
        "http://localhost:3003/collections.json?page_size=0&include_facets=v2&processing_level_id_h%5B%5D=PL1"},
       :has_children false}]}]})

(def expected-facets-with-no-matching-collections
  "Facet response when searching against faceted fields which have 0 matching collections. Each of
  the search terms will be included in the facet response along with a remove link so that the user
  can remove that search term from their query."
  {:title "Browse Collections",
   :type "group",
   :has_children true,
   :children
   [{:title "Processing Levels",
     :type "group",
     :applied true,
     :has_children true,
     :children
     [{:title "PL1",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&instrument_h=ATM&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}]}
    {:title "Keywords",
     :applied true,
     :children
     [{:title "Topic1",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&instrument_h=ATM&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
       :has_children false}
      {:title "Term1",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&instrument_h=ATM&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}
      {:title "Level1-1",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&instrument_h=ATM&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}
      {:title "Level1-2",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&instrument_h=ATM&keyword=MODIS&page_size=0&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}
      {:title "Level1-3",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&instrument_h=ATM&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}]}
    {:title "Platforms",
     :applied true,
     :children
     [{:title "Space-based Platforms",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&instrument_h=ATM&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}
      {:title "Earth Observation Satellites",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&instrument_h=ATM&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}
      {:title "DIADEM",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&instrument_h=ATM&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}]}
    {:title "Instruments",
     :type "group",
     :applied true,
     :has_children true,
     :children
     [{:title "ATM",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}]}
    {:title "Organizations",
     :type "group",
     :applied true,
     :has_children true,
     :children
     [{:title "DOI/USGS/CMG/WHSC",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&instrument_h=ATM&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}]}
    {:title "Projects",
     :type "group",
     :applied true,
     :has_children true,
     :children
     [{:title "proj1",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&instrument_h=ATM&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}]}]})

(def expected-facets-modis-and-aster-no-results-found
  "Expected facet response when searching for MODIS keyword and MODIS or ASTER platform and no
  collections are found. If no collections are matched the values searched in the query should be
  present as remove links."
  {:title "Browse Collections",
   :type "group",
   :has_children true,
   :children
   [{:title "Platforms",
     :applied true,
     :children
     [{:title "Space-based Platforms",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?platforms_h%5B1%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=fake&keyword=DMSP+5B%2FF3&page_size=0&platforms_h%5B1%5D%5Bsub_category%5D=SMAP-like&platforms_h%5B1%5D%5Bshort_name%5D=SMAP&include_facets=v2&platforms_h%5B0%5D%5Bshort_name%5D=moDIS-p0"},
       :has_children false}
      {:title "Earth Observation Satellites",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?platforms_h%5B1%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bsub_category%5D=fake&keyword=DMSP+5B%2FF3&page_size=0&platforms_h%5B1%5D%5Bsub_category%5D=SMAP-like&platforms_h%5B1%5D%5Bshort_name%5D=SMAP&include_facets=v2&platforms_h%5B0%5D%5Bshort_name%5D=moDIS-p0&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
       :has_children false}
      {:title "fake",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?platforms_h%5B1%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B1%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&keyword=DMSP+5B%2FF3&page_size=0&platforms_h%5B1%5D%5Bsub_category%5D=SMAP-like&platforms_h%5B1%5D%5Bshort_name%5D=SMAP&include_facets=v2&platforms_h%5B0%5D%5Bshort_name%5D=moDIS-p0&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
       :has_children false}
      {:title "SMAP-like",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?platforms_h%5B1%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B1%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=fake&keyword=DMSP+5B%2FF3&page_size=0&platforms_h%5B1%5D%5Bshort_name%5D=SMAP&include_facets=v2&platforms_h%5B0%5D%5Bshort_name%5D=moDIS-p0&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
       :has_children false}
      {:title "moDIS-p0",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?platforms_h%5B1%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B1%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=fake&keyword=DMSP+5B%2FF3&page_size=0&platforms_h%5B1%5D%5Bsub_category%5D=SMAP-like&platforms_h%5B1%5D%5Bshort_name%5D=SMAP&include_facets=v2&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
       :has_children false}
      {:title "SMAP",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?platforms_h%5B1%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B1%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=fake&keyword=DMSP+5B%2FF3&page_size=0&platforms_h%5B1%5D%5Bsub_category%5D=SMAP-like&include_facets=v2&platforms_h%5B0%5D%5Bshort_name%5D=moDIS-p0&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
       :has_children false}
]}]})

(def expected-facets-when-aqua-search-results-found
  "Expected facet response when searching for Aqua keyword and some collections are found.
  For search terms that can't be matched the values not found in the search query should be
  present as remove links."
  {:title "Browse Collections",
   :type "group",
   :has_children true,
   :children
   [{:title "Platforms",
     :type "group",
     :applied true,
     :has_children true,
     :children
     [{:title "Space-based Platforms",
       :type "filter",
       :applied true,
       :count 1,
       :links
       {:remove
        "http://localhost:3003/collections.json?platforms_h%5B2%5D%5Bshort_name%5D=Aqua&platforms_h%5B0%5D%5Bsub_category%5D=fake&keyword=Aqua&page_size=0&platforms_h%5B1%5D%5Bsub_category%5D=SMAP-like&platforms_h%5B1%5D%5Bshort_name%5D=SMAP&include_facets=v2&platforms_h%5B0%5D%5Bshort_name%5D=moDIS-p0"},
       :has_children true,
       :children
       [{:title "Earth Observation Satellites",
         :type "filter",
         :applied true,
         :count 1,
         :links
         {:remove
          "http://localhost:3003/collections.json?platforms_h%5B1%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bsub_category%5D=fake&keyword=Aqua&page_size=0&platforms_h%5B1%5D%5Bsub_category%5D=SMAP-like&include_facets=v2&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
         :has_children true,
         :children
         [{:title "Aqua",
           :type "filter",
           :applied true,
           :count 1,
           :links
           {:remove
            "http://localhost:3003/collections.json?platforms_h%5B1%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B1%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=fake&keyword=Aqua&page_size=0&platforms_h%5B1%5D%5Bsub_category%5D=SMAP-like&platforms_h%5B1%5D%5Bshort_name%5D=SMAP&include_facets=v2&platforms_h%5B0%5D%5Bshort_name%5D=moDIS-p0&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
           :has_children false}]}]}
      {:title "fake",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?platforms_h%5B2%5D%5Bshort_name%5D=Aqua&platforms_h%5B1%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B1%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&keyword=Aqua&platforms_h%5B2%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B2%5D%5Bbasis%5D=Space-based+Platforms&page_size=0&platforms_h%5B1%5D%5Bsub_category%5D=SMAP-like&platforms_h%5B1%5D%5Bshort_name%5D=SMAP&include_facets=v2&platforms_h%5B0%5D%5Bshort_name%5D=moDIS-p0&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
       :has_children false}
      {:title "SMAP-like",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?platforms_h%5B2%5D%5Bshort_name%5D=Aqua&platforms_h%5B1%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B1%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=fake&keyword=Aqua&platforms_h%5B2%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B2%5D%5Bbasis%5D=Space-based+Platforms&page_size=0&platforms_h%5B1%5D%5Bshort_name%5D=SMAP&include_facets=v2&platforms_h%5B0%5D%5Bshort_name%5D=moDIS-p0&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
       :has_children false}
      {:title "moDIS-p0",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?platforms_h%5B2%5D%5Bshort_name%5D=Aqua&platforms_h%5B1%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B1%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=fake&keyword=Aqua&platforms_h%5B2%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B2%5D%5Bbasis%5D=Space-based+Platforms&page_size=0&platforms_h%5B1%5D%5Bsub_category%5D=SMAP-like&platforms_h%5B1%5D%5Bshort_name%5D=SMAP&include_facets=v2&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
       :has_children false}
      {:title "SMAP",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?platforms_h%5B2%5D%5Bshort_name%5D=Aqua&platforms_h%5B1%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B1%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=fake&keyword=Aqua&platforms_h%5B2%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B2%5D%5Bbasis%5D=Space-based+Platforms&page_size=0&platforms_h%5B1%5D%5Bsub_category%5D=SMAP-like&include_facets=v2&platforms_h%5B0%5D%5Bshort_name%5D=moDIS-p0&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
       :has_children false}]}]})

(def expected-all-hierarchical-facets
  "Expected value for the all-hierarchical-fields-test. This is using the version 1 hierarchical
  facets."
  [{:field "project", :value-counts [["PROJ2" 2] ["proj1" 2]]}
   {:field "sensor",
    :value-counts
    [["FROM_KMS-p0-i0-s0" 2]
     ["FROM_KMS-p0-i1-s0" 2]
     ["FROM_KMS-p1-i0-s0" 2]
     ["FROM_KMS-p1-i1-s0" 2]]}
   {:field "two_d_coordinate_system_name",
    :value-counts [["MISR" 2]]}
   {:field "processing_level_id", :value-counts [["PL1" 2]]}
   {:field "detailed_variable",
    :value-counts [["DETAIL1" 2] ["UNIVERSAL" 2]]}
   {:subfields ["level_0"],
    :level_0
    [{:subfields ["level_1"],
      :level_1
      [{:subfields ["level_2"],
        :level_2
        [{:subfields ["level_3"],
          :level_3
          [{:subfields ["short_name"],
            :short_name
            [{:subfields ["long_name"],
              :long_name
              [{:count 2,
                :value "Woods Hole Science Center, Coastal and Marine Geology, U.S. Geological Survey, U.S. Department of the Interior"}],
              :count 2,
              :value "DOI/USGS/CMG/WHSC"}],
            :count 2,
            :value "Added level 3 value"}],
          :count 2,
          :value "USGS"}],
        :count 2,
        :value "DOI"}],
      :count 2,
      :value "GOVERNMENT AGENCIES-U.S. FEDERAL AGENCIES"}],
    :field "data_centers"}
   {:subfields ["level_0"],
    :level_0
    [{:subfields ["level_1"],
      :level_1
      [{:subfields ["level_2"],
        :level_2
        [{:subfields ["level_3"],
          :level_3
          [{:subfields ["short_name"],
            :short_name
            [{:subfields ["long_name"],
              :long_name
              [{:count 2,
                :value "Woods Hole Science Center, Coastal and Marine Geology, U.S. Geological Survey, U.S. Department of the Interior"}],
              :count 2,
              :value "DOI/USGS/CMG/WHSC"}],
            :count 2,
            :value "Added level 3 value"}],
          :count 2,
          :value "USGS"}],
        :count 2,
        :value "DOI"}],
      :count 2,
      :value "GOVERNMENT AGENCIES-U.S. FEDERAL AGENCIES"}],
    :field "archive_centers"}
   {:subfields ["basis"],
    :basis
    [{:subfields ["category"],
      :category
      [{:subfields ["sub_category"],
        :sub_category
        [{:subfields ["short_name"],
          :short_name [{:count 2, :value "DIADEM-1D"}],
          :count 2,
          :value "DIADEM"}
         {:subfields ["short_name"],
          :short_name
          [{:subfields ["long_name"],
            :long_name
            [{:count 2,
              :value
              "Defense Meteorological Satellite Program-F3"}],
            :count 2,
            :value "DMSP 5B/F3"}],
          :count 2,
          :value
          "Defense Meteorological Satellite Program(DMSP)"}],
        :count 2,
        :value "Earth Observation Satellites"}],
      :count 2,
      :value "Space-based Platforms"}],
    :field "platforms"}
   {:subfields ["category"],
    :category
    [{:subfields ["class"],
      :class
      [{:subfields ["type"],
        :type
        [{:subfields ["subtype"],
          :subtype
          [{:subfields ["short_name"],
            :short_name
            [{:subfields ["long_name"],
              :long_name
              [{:count 2,
                :value "Airborne Topographic Mapper"}],
              :count 2,
              :value "ATM"}
             {:subfields ["long_name"],
              :long_name
              [{:count 2,
                :value "Land, Vegetation, and Ice Sensor"}],
              :count 2,
              :value "LVIS"}],
            :count 2,
            :value "Lidar/Laser Altimeters"}],
          :count 2,
          :value "Altimeters"}],
        :count 2,
        :value "Active Remote Sensing"}],
      :count 2,
      :value "Earth Remote Sensing Instruments"}
     {:subfields ["class"],
      :class
      [{:subfields ["type"],
        :type
        [{:subfields ["subtype"],
          :subtype
          [{:subfields ["short_name"],
            :short_name
            [{:subfields ["long_name"],
              :long_name [{:count 2, :value "Not Provided"}],
              :count 2,
              :value "FROM_KMS-p0-i0-s0"}
             {:subfields ["long_name"],
              :long_name [{:count 2, :value "Not Provided"}],
              :count 2,
              :value "FROM_KMS-p0-i1-s0"}
             {:subfields ["long_name"],
              :long_name [{:count 2, :value "Not Provided"}],
              :count 2,
              :value "FROM_KMS-p1-i0-s0"}
             {:subfields ["long_name"],
              :long_name [{:count 2, :value "Not Provided"}],
              :count 2,
              :value "FROM_KMS-p1-i1-s0"}],
            :count 2,
            :value "Not Provided"}],
          :count 2,
          :value "Not Provided"}],
        :count 2,
        :value "Not Provided"}],
      :count 2,
      :value "Not Provided"}],
    :field "instruments"}
   {:subfields ["category"],
    :category
    [{:subfields ["topic"],
      :topic
      [{:subfields ["term"],
        :term
        [{:subfields ["variable_level_1"],
          :variable_level_1
          [{:subfields ["variable_level_2"],
            :variable_level_2
            [{:subfields ["variable_level_3"],
              :variable_level_3
              [{:subfields ["detailed_variable"],
                :detailed_variable
                [{:count 2, :value "UNIVERSAL"}],
                :count 2,
                :value "LEVEL2-3"}],
              :count 2,
              :value "LEVEL2-2"}],
            :count 2,
            :value "LEVEL2-1"}],
          :count 2,
          :value "EXTREME"}
         {:count 2, :value "UNIVERSAL"}],
        :count 2,
        :value "POPULAR"}
       {:subfields ["term"],
        :term
        [{:subfields ["variable_level_1"],
          :variable_level_1 [{:count 2, :value "UNIVERSAL"}],
          :count 2,
          :value "TERM4"}],
        :count 2,
        :value "COOL"}],
      :count 2,
      :value "HURRICANE"}
     {:subfields ["topic"],
      :topic
      [{:subfields ["term"],
        :term [{:count 2, :value "MILD"}],
        :count 2,
        :value "COOL"}
       {:subfields ["term"],
        :term [{:count 2, :value "MILD"}],
        :count 2,
        :value "POPULAR"}],
      :count 2,
      :value "UPCASE"}
     {:subfields ["topic"],
      :topic
      [{:subfields ["term"],
        :term
        [{:subfields ["variable_level_1"],
          :variable_level_1
          [{:subfields ["variable_level_2"],
            :variable_level_2
            [{:subfields ["variable_level_3"],
              :variable_level_3
              [{:subfields ["detailed_variable"],
                :detailed_variable
                [{:count 2, :value "DETAIL1"}],
                :count 2,
                :value "LEVEL1-3"}],
              :count 2,
              :value "LEVEL1-2"}],
            :count 2,
            :value "LEVEL1-1"}],
          :count 2,
          :value "TERM1"}],
        :count 2,
        :value "TOPIC1"}],
      :count 2,
      :value "CAT1"}
     {:subfields ["topic"],
      :topic
      [{:subfields ["term"],
        :term [{:count 2, :value "EXTREME"}],
        :count 2,
        :value "POPULAR"}],
      :count 2,
      :value "TORNADO"}],
    :field "science_keywords"}
   {:subfields ["category"],
    :category
    [{:subfields ["type"],
      :type
      [{:subfields ["subregion_1"],
        :subregion_1
        [{:subfields ["subregion_2"],
          :subregion_2
          [{:subfields ["subregion_3"],
            :subregion_3 [{:count 2, :value "Not Provided"}],
            :count 2,
            :value "ANGOLA"}],
          :count 2,
          :value "CENTRAL AFRICA"}],
        :count 2,
        :value "AFRICA"}
       {:subfields ["subregion_1"],
        :subregion_1
        [{:subfields ["subregion_2"],
          :subregion_2
          [{:subfields ["subregion_3"],
            :subregion_3 [{:count 1, :value "GAZA STRIP"}],
            :count 1,
            :value "MIDDLE EAST"}],
          :count 1,
          :value "WESTERN ASIA"}],
        :count 1,
        :value "ASIA"}],
      :count 2,
      :value "CONTINENT"}
     {:subfields ["type"],
      :type
      [{:subfields ["subregion_1"],
        :subregion_1
        [{:subfields ["subregion_2"],
          :subregion_2
          [{:subfields ["subregion_3"],
            :subregion_3 [{:count 1, :value "Not Provided"}],
            :count 1,
            :value "Not Provided"}],
          :count 1,
          :value "Not Provided"}],
        :count 1,
        :value "NOT IN KMS"}],
      :count 1,
      :value "OTHER"}],
    :field "location_keywords"}])

(def expected-v2-facets-apply-links-with-facets-size
  "Expected facets to be returned in the facets v2 response. The structure of the v2 facet response
  is documented in https://wiki.earthdata.nasa.gov/display/CMR/Updated+facet+response. This response
  is generated for the search
  http://localhost:3003/collections.json?page_size=0&include_facets=v2&facets_size[platform]=1
  without any query parameters selected and with a couple of collections that have science keywords,
  projects, platforms, instruments, organizations, and processing levels in their metadata. This
  tests that the applied parameter is set to false correctly and that the generated links specify a
  a link to add each search parameter to apply that value to a search."
  {:title "Browse Collections",
           :type "group",
           :has_children true,
           :children
           [{:title "Keywords",
             :type "group",
             :applied false,
             :has_children true,
             :children
             [{:title "Popular",
               :type "filter",
               :applied false,
               :count 2,
               :links
               {:apply
                "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&page_size=0&include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Popular"},
               :has_children true}
              {:title "Topic1",
               :type "filter",
               :applied false,
               :count 2,
               :links
               {:apply
                "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&page_size=0&include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
               :has_children true}]}
            {:title "Platforms",
             :type "group",
             :applied false,
             :has_children true,
             :children
             [{:title "Space-based Platforms",
               :type "filter",
               :applied false,
               :count 2,
               :links
               {:apply
                "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&page_size=0&include_facets=v2&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
                :has_children true}]},
            {:title "Instruments",
             :type "group",
             :applied false,
             :has_children true,
             :children
             [{:title "ATM",
               :type "filter",
               :applied false,
               :count 2,
               :links
               {:apply
                "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&page_size=0&include_facets=v2&instrument_h%5B%5D=ATM"},
               :has_children false}
              {:title "lVIs",
               :type "filter",
               :applied false,
               :count 2,
               :links
               {:apply
                "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&page_size=0&include_facets=v2&instrument_h%5B%5D=lVIs"},
               :has_children false}]}
            {:title "Data Format",
             :type "group",
             :applied false,
             :has_children true,
             :children
             [{:title "NetCDF",
               :type "filter",
               :applied false,
               :count 1,
               :links
               {:apply
                "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&page_size=0&include_facets=v2&granule_data_format_h%5B%5D=NetCDF"},
               :has_children false}]}
            {:title "Organizations",
             :type "group",
             :applied false,
             :has_children true,
             :children
             [{:title "DOI/USGS/CMG/WHSC",
               :type "filter",
               :applied false,
               :count 2,
               :links
               {:apply
                "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&page_size=0&include_facets=v2&data_center_h%5B%5D=DOI%2FUSGS%2FCMG%2FWHSC"},
               :has_children false}]}
            {:title "Projects",
             :type "group",
             :applied false,
             :has_children true,
             :children
             [{:title "proj1",
               :type "filter",
               :applied false,
               :count 2,
               :links
               {:apply
                "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&page_size=0&include_facets=v2&project_h%5B%5D=proj1"},
               :has_children false}
              {:title "PROJ2",
               :type "filter",
               :applied false,
               :count 2,
               :links
               {:apply
                "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&page_size=0&include_facets=v2&project_h%5B%5D=PROJ2"},
               :has_children false}]}
            {:title "Processing Levels",
             :type "group",
             :applied false,
             :has_children true,
             :children
             [{:title "PL1",
               :type "filter",
               :applied false,
               :count 2,
               :links
               {:apply
                "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&page_size=0&include_facets=v2&processing_level_id_h%5B%5D=PL1"},
               :has_children false}]}
            {:title "Measurements",
             :type "group",
             :applied false,
             :has_children true,
             :children
             [{:title "Measurement1",
               :type "filter",
               :applied false,
               :count 1,
               :links
               {:apply
                "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&page_size=0&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1"},
               :has_children true}
              {:title "Measurement2",
               :type "filter",
               :applied false,
               :count 1,
               :links
               {:apply
                "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&page_size=0&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement2"},
               :has_children true}]},
            {:title "Tiling System",
             :type "group",
             :applied false,
             :has_children true,
             :children
             [{:title "MISR",
               :type "filter",
               :applied false,
               :count 1,
               :links
               {:apply "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&page_size=0&include_facets=v2&two_d_coordinate_system_name%5B%5D=MISR"},
               :has_children false}]}]})

(def expected-v2-facets-apply-links-with-selecting-facet-outside-of-facets-size
  "Expected facets to be returned in the facets v2 response. The structure of the v2 facet response
  is documented in https://wiki.earthdata.nasa.gov/display/CMR/Updated+facet+response. This response
  is generated for the search
  http://localhost:3003/collections.json?page_size=0&platform_h[]=diadem-1D&include_facets=v2&facets_size[platform]=1
  without any query parameters selected and with a couple of collections that have science keywords,
  projects, platforms, instruments, organizations, and processing levels in their metadata. This
  tests that the applied parameter is set to false correctly and that the generated links specify a
  a link to add each search parameter to apply that value to a search."
  {:title "Browse Collections",
   :type "group",
   :has_children true,
   :children
   [{:title "Data Format",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "NetCDF",
       :type "filter",
       :applied false,
       :count 1,
       :links
       {:apply
        "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&granule_data_format_h%5B%5D=NetCDF"},
       :has_children false}]}
    {:title "Processing Levels",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "PL1",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&processing_level_id_h%5B%5D=PL1"},
       :has_children false}]}
    {:title "Keywords",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "Popular",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Popular"},
       :has_children true}
      {:title "Topic1",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children true}]}
    {:title "Platforms",
     :type "group",
     :applied true,
     :has_children true,
     :children
     [{:title "Space-based Platforms",
       :type "filter",
       :applied true,
       :count 2,
       :links
       {:remove
        "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&page_size=0&include_facets=v2"},
       :has_children true,
       :children
       [{:title "Earth Observation Satellites",
         :type "filter",
         :applied true,
         :count 2,
         :links
         {:remove
          "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&page_size=0&include_facets=v2"},
         :has_children true,
         :children
         [{:title "DIADEM",
           :type "filter",
           :applied true,
           :count 2,
           :links
           {:remove
            "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&page_size=0&include_facets=v2"},
           :has_children true,
           :children
           [{:title "DIADEM-1D",
             :type "filter",
             :applied true,
             :count 2,
             :links
             {:remove
              "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&page_size=0&include_facets=v2"},
             :has_children false}]}]}]}]}
    {:title "Instruments",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "ATM",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&instrument_h%5B%5D=ATM"},
       :has_children false}
      {:title "lVIs",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&instrument_h%5B%5D=lVIs"},
       :has_children false}]}
    {:title "Organizations",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "DOI/USGS/CMG/WHSC",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&data_center_h%5B%5D=DOI%2FUSGS%2FCMG%2FWHSC"},
       :has_children false}]}
    {:title "Projects",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "proj1",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&project_h%5B%5D=proj1"},
       :has_children false}
      {:title "PROJ2",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&project_h%5B%5D=PROJ2"},
       :has_children false}]}
    {:title "Measurements",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "Measurement1",
       :type "filter",
       :applied false,
       :count 1,
       :links
       {:apply
        "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1"},
       :has_children true}
      {:title "Measurement2",
       :type "filter",
       :applied false,
       :count 1,
       :links
       {:apply
        "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement2"},
       :has_children true}]}
    {:title "Tiling System",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "MISR",
       :type "filter",
       :applied false,
       :count 1,
       :links
       {:apply
        "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&two_d_coordinate_system_name%5B%5D=MISR"},
       :has_children false}]}]})

(def expected-v2-facets-apply-links-with-facets-size-and-non-existing-selecting-facet
  "Expected facets to be returned in the facets v2 response. The structure of the v2 facet response
  is documented in https://wiki.earthdata.nasa.gov/display/CMR/Updated+facet+response. This response
  is generated for the search
  http://localhost:3003/collections.json?page_size=0&platform_h[]=Non-Exist&include_facets=v2&facets_size[platform]=1
  without any query parameters selected and with a couple of collections that have science keywords,
  projects, platforms, instruments, organizations, and processing levels in their metadata. This
  tests that the applied parameter is set to false correctly and that the generated links specify a
  a link to add each search parameter to apply that value to a search."
  {:title "Browse Collections",
   :type "group",
   :has_children true,
   :children
   [{:title "Platforms",
     :type "group",
     :applied true,
     :has_children true,
     :children
     [{:title "Space-based Platforms",
       :type "filter",
       :applied true,
       :count 2,
       :links
       {:remove
        "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bshort_name%5D=Non-Exist&page_size=0&include_facets=v2"},
       :has_children true,
       :children
       [{:title "Earth Observation Satellites",
         :type "filter",
         :applied true,
         :count 2,
         :links
         {:remove
          "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bshort_name%5D=Non-Exist&page_size=0&include_facets=v2"},
         :has_children true,
         :children
         [{:title
           "Defense Meteorological Satellite Program(DMSP)",
           :type "filter",
           :applied false,
           :count 2,
           :links
           {:apply
            "http://localhost:3003/collections.json?platforms_h%5B1%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B1%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&page_size=0&platforms_h%5B1%5D%5Bsub_category%5D=Defense+Meteorological+Satellite+Program%28DMSP%29&include_facets=v2&facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bshort_name%5D=Non-Exist&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
           :has_children true}
          {:title "DIADEM",
           :type "filter",
           :applied true,
           :count 2,
           :links
           {:remove
            "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bshort_name%5D=Non-Exist&page_size=0&include_facets=v2"},
           :has_children true,
           :children
           [{:title "DIADEM-1D",
             :type "filter",
             :applied false,
             :count 2,
             :links
             {:apply
              "http://localhost:3003/collections.json?platforms_h%5B1%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B1%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&page_size=0&platforms_h%5B1%5D%5Bsub_category%5D=DIADEM&platforms_h%5B1%5D%5Bshort_name%5D=DIADEM-1D&include_facets=v2&facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bshort_name%5D=Non-Exist&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
             :has_children false}]}]}]}
      {:title "Non-Exist",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?facets_size%5Bplatforms%5D=1&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&page_size=0&include_facets=v2"},
       :has_children false}]}]})

(def expected-v2-facets-apply-links-with-selecting-facet-without-facets-size
  "Expected facets to be returned in the facets v2 response. The structure of the v2 facet response
  is documented in https://wiki.earthdata.nasa.gov/display/CMR/Updated+facet+response. This response
  is generated for the search
  http://localhost:3003/collections.json?page_size=0&platform_h[]=existingPlat&include_facets=v2
  without any query parameters selected and with a couple of collections that have science keywords,
  projects, platforms, instruments, organizations, and processing levels in their metadata. This
  tests that the applied parameter is set to false correctly and that the generated links specify a
  a link to add each search parameter to apply that value to a search."
  {:title "Browse Collections",
   :type "group",
   :has_children true,
   :children
   [{:title "Data Format",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "NetCDF",
       :type "filter",
       :applied false,
       :count 1,
       :links
       {:apply
        "http://localhost:3003/collections.json?platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&granule_data_format_h%5B%5D=NetCDF"},
       :has_children false}]}
    {:title "Processing Levels",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "PL1",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&processing_level_id_h%5B%5D=PL1"},
       :has_children false}]}
    {:title "Keywords",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "Popular",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Popular"},
       :has_children true}
      {:title "Topic1",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children true}]}
    {:title "Platforms",
     :type "group",
     :applied true,
     :has_children true,
     :children
     [{:title "Space-based Platforms",
       :type "filter",
       :applied true,
       :count 2,
       :links
       {:remove
        "http://localhost:3003/collections.json?page_size=0&include_facets=v2"},
       :has_children true,
       :children
       [{:title "Earth Observation Satellites",
         :type "filter",
         :applied true,
         :count 2,
         :links
         {:remove
          "http://localhost:3003/collections.json?platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&page_size=0&include_facets=v2"},
         :has_children true,
         :children
         [{:title
           "Defense Meteorological Satellite Program(DMSP)",
           :type "filter",
           :applied false,
           :count 2,
           :links
           {:apply
            "http://localhost:3003/collections.json?platforms_h%5B1%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B1%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&page_size=0&platforms_h%5B1%5D%5Bsub_category%5D=Defense+Meteorological+Satellite+Program%28DMSP%29&include_facets=v2&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms"},
           :has_children true}
          {:title "DIADEM",
           :type "filter",
           :applied true,
           :count 2,
           :links
           {:remove
            "http://localhost:3003/collections.json?platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&page_size=0&include_facets=v2"},
           :has_children true,
           :children
           [{:title "DIADEM-1D",
             :type "filter",
             :applied true,
             :count 2,
             :links
             {:remove
              "http://localhost:3003/collections.json?platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&page_size=0&include_facets=v2"},
             :has_children false}]}]}]}]}
    {:title "Instruments",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "ATM",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&instrument_h%5B%5D=ATM"},
       :has_children false}
      {:title "lVIs",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&instrument_h%5B%5D=lVIs"},
       :has_children false}]}
    {:title "Organizations",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "DOI/USGS/CMG/WHSC",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&data_center_h%5B%5D=DOI%2FUSGS%2FCMG%2FWHSC"},
       :has_children false}]}
    {:title "Projects",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "proj1",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&project_h%5B%5D=proj1"},
       :has_children false}
      {:title "PROJ2",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&project_h%5B%5D=PROJ2"},
       :has_children false}]}
    {:title "Measurements",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "Measurement1",
       :type "filter",
       :applied false,
       :count 1,
       :links
       {:apply
        "http://localhost:3003/collections.json?platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1"},
       :has_children true}
      {:title "Measurement2",
       :type "filter",
       :applied false,
       :count 1,
       :links
       {:apply
        "http://localhost:3003/collections.json?platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement2"},
       :has_children true}]}
    {:title "Tiling System",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "MISR",
       :type "filter",
       :applied false,
       :count 1,
       :links
       {:apply
        "http://localhost:3003/collections.json?platforms_h%5B0%5D%5Bbasis%5D=Space-based+Platforms&platforms_h%5B0%5D%5Bcategory%5D=Earth+Observation+Satellites&platforms_h%5B0%5D%5Bsub_category%5D=DIADEM&platforms_h%5B0%5D%5Bshort_name%5D=diadem-1D&page_size=0&include_facets=v2&two_d_coordinate_system_name%5B%5D=MISR"},
       :has_children false}]}]})
