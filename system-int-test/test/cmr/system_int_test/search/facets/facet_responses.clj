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
        "http://localhost:3003/collections.json?page_size=0&include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Popular"}
       :has_children true}
      {:title "Topic1",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?page_size=0&include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children true}]}
    {:title "Platforms",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "diadem-1D",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?page_size=0&include_facets=v2&platform_h%5B%5D=diadem-1D"},
       :has_children false}
      {:title "DMSP 5B/F3",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?page_size=0&include_facets=v2&platform_h%5B%5D=DMSP+5B%2FF3"},
       :has_children false}]}
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
    {:title "Processing levels",
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
       :count 2,
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
       :has_children true}]}]})

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
   [{:title "Keywords",
     :type "group",
     :applied true,
     :has_children true,
     :children
     [{:title "Popular",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&science_keywords_h%5B1%5D%5Btopic%5D=Popular&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=DIADEM-1D&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children true}
      {:title "Topic1",
       :type "filter",
       :applied true,
       :count 2,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&project_h=proj1&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&platform_h=DIADEM-1D&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1"},
       :has_children true,
       :children
       [{:title "Term1",
         :type "filter",
         :applied true,
         :count 2,
         :links
         {:remove
          "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&project_h=proj1&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&platform_h=DIADEM-1D&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
         :has_children true,
         :children
         [{:title "Level1-1",
           :type "filter",
           :applied true,
           :count 2,
           :links
           {:remove
            "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&platform_h=DIADEM-1D&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
           :has_children true,
           :children
           [{:title "Level1-2",
             :type "filter",
             :applied true,
             :count 2,
             :links
             {:remove
              "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&platform_h=DIADEM-1D&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
             :has_children true,
             :children
             [{:title "Level1-3",
               :type "filter",
               :applied true,
               :count 2,
               :links
               {:remove
                "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=DIADEM-1D&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
               :has_children false}]}]}]}]}]}
    {:title "Platforms",
     :type "group",
     :applied true,
     :has_children true,
     :children
     [{:title "diadem-1D",
       :type "filter",
       :applied true,
       :count 2,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}
      {:title "DMSP 5B/F3",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=DIADEM-1D&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&platform_h%5B%5D=DMSP+5B%2FF3&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}]}
    {:title "Instruments",
     :type "group",
     :applied true,
     :has_children true,
     :children
     [{:title "ATM",
       :type "filter",
       :applied true,
       :count 2,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&variables_h%5B0%5D%5Bvariable%5D=Variable1&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=DIADEM-1D&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}
      {:title "lVIs",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&instrument_h%5B%5D=lVIs&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=DIADEM-1D&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}]}
    {:title "Organizations",
     :type "group",
     :applied true,
     :has_children true,
     :children
     [{:title "DOI/USGS/CMG/WHSC",
       :type "filter",
       :applied true,
       :count 2,
       :links
       {:remove
        "http://localhost:3003/collections.json?science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=DIADEM-1D&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}]}
    {:title "Projects",
     :type "group",
     :applied true,
     :has_children true,
     :children
     [{:title "proj1",
       :type "filter",
       :applied true,
       :count 2,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=DIADEM-1D&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}
      {:title "PROJ2",
       :type "filter",
       :applied false,
       :count 2,
       :links
       {:apply
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&project_h%5B%5D=PROJ2&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=DIADEM-1D&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}]}
    {:title "Processing levels",
     :type "group",
     :applied true,
     :has_children true,
     :children
     [{:title "PL1",
       :type "filter",
       :applied true,
       :count 2,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=DIADEM-1D&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}]}
    {:title "Measurements",
     :type "group",
     :applied true,
     :has_children true,
     :children
     [{:title "Measurement1",
       :type "filter",
       :applied true,
       :count 2,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=DIADEM-1D&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children true,
       :children
       [{:title "Variable1",
         :type "filter",
         :applied true,
         :count 2,
         :links
         {:remove
          "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=DIADEM-1D&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
         :has_children false}]}
      {:title "Measurement2",
       :type "filter",
       :applied false,
       :count 1,
       :links
       {:apply
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&variables_h%5B1%5D%5Bmeasurement%5D=Measurement2&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&variables_h%5B0%5D%5Bvariable%5D=Variable1&instrument_h=ATM&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=DIADEM-1D&include_facets=v2&variables_h%5B0%5D%5Bmeasurement%5D=Measurement1&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children true}]}]})

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
    {:title "Processing levels",
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
   [{:title "Keywords",
     :applied true,
     :children
     [{:title "Topic1",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&instrument_h=ATM&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=ASTER-p0&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1"},
       :has_children false}
      {:title "Term1",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&project_h=proj1&instrument_h=ATM&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=ASTER-p0&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}
      {:title "Level1-1",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&instrument_h=ATM&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=ASTER-p0&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}
      {:title "Level1-2",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&instrument_h=ATM&keyword=MODIS&page_size=0&platform_h=ASTER-p0&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1",}
       :has_children false}
      {:title "Level1-3",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&instrument_h=ATM&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=ASTER-p0&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}]}
    {:title "Platforms",
     :type "group",
     :applied true,
     :has_children true,
     :children
     [{:title "ASTER-p0",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&instrument_h=ATM&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
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
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=ASTER-p0&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}]}
    {:title "Organizations",
     :type "group",
     :applied true,
     :has_children true,
     :children
     [{:title "DOI/USGS/CMG/WHSC",
       :type "filter"
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&instrument_h=ATM&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=ASTER-p0&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
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
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&instrument_h=ATM&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=ASTER-p0&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&processing_level_id_h=PL1&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}]}
    {:title "Processing levels",
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
        "http://localhost:3003/collections.json?data_center_h=DOI%2FUSGS%2FCMG%2FWHSC&science_keywords_h%5B0%5D%5Bvariable_level_3%5D=Level1-3&science_keywords_h%5B0%5D%5Bvariable_level_1%5D=Level1-1&science_keywords_h%5B0%5D%5Bterm%5D=Term1&project_h=proj1&instrument_h=ATM&keyword=MODIS&page_size=0&science_keywords_h%5B0%5D%5Bvariable_level_2%5D=Level1-2&platform_h=ASTER-p0&include_facets=v2&science_keywords_h%5B0%5D%5Bcategory%5D=Earth+Science&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children false}]}]})

(def expected-facets-modis-and-aster-no-results-found
  "Expected facet response when searching for MODIS keyword and MODIS or ASTER platform and no
  collections are found. If no collections are matched the values searched in the query should be
  present as remove links."
  {:title "Browse Collections",
   :type "group",
   :has_children true,
   :children
   [{:title "Keywords",
     :type "group",
     :applied false,
     :has_children true,
     :children
     [{:title "Topic1",
       :type "filter",
       :applied false,
       :count 1,
       :links
       {:apply
        "http://localhost:3003/collections.json?platform_h=moDIS-p0&platform_h=ASTER-p0&keyword=MODIS&page_size=0&include_facets=v2&science_keywords_h%5B0%5D%5Btopic%5D=Topic1"},
       :has_children true}]}
    {:title "Platforms",
     :type "group",
     :applied true,
     :has_children true,
     :children
     [{:title "ASTER-p0",
       :type "filter",
       :applied true,
       :count 0,
       :links
       {:remove
        "http://localhost:3003/collections.json?platform_h=moDIS-p0&keyword=MODIS&page_size=0&include_facets=v2"},
       :has_children false}
      {:title "MODIS-p0",
       :type "filter",
       :applied true,
       :count 1,
       :links
       {:remove
        "http://localhost:3003/collections.json?platform_h=ASTER-p0&keyword=MODIS&page_size=0&include_facets=v2"},
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
   {:field "data_centers",
    :subfields ["level_0"],
    :level_0
    [{:value "GOVERNMENT AGENCIES-U.S. FEDERAL AGENCIES",
      :count 2,
      :subfields ["level_1"],
      :level_1
      [{:value "DOI",
        :count 2,
        :subfields ["level_2"],
        :level_2
        [{:value "USGS",
          :count 2,
          :subfields ["level_3"],
          :level_3
          [{:value "Added level 3 value",
            :count 2,
            :subfields ["short_name"],
            :short_name
            [{:value "DOI/USGS/CMG/WHSC",
              :count 2,
              :subfields ["long_name"],
              :long_name
              [{:value
                "Woods Hole Science Center, Coastal and Marine Geology, U.S. Geological Survey, U.S. Department of the Interior",
                :count 2}]}]}]}]}]}]}
   {:field "archive_centers",
    :subfields ["level_0"],
    :level_0
    [{:value "GOVERNMENT AGENCIES-U.S. FEDERAL AGENCIES",
      :count 2,
      :subfields ["level_1"],
      :level_1
      [{:value "DOI",
        :count 2,
        :subfields ["level_2"],
        :level_2
        [{:value "USGS",
          :count 2,
          :subfields ["level_3"],
          :level_3
          [{:value "Added level 3 value",
            :count 2,
            :subfields ["short_name"],
            :short_name
            [{:value "DOI/USGS/CMG/WHSC",
              :count 2,
              :subfields ["long_name"],
              :long_name
              [{:value
                "Woods Hole Science Center, Coastal and Marine Geology, U.S. Geological Survey, U.S. Department of the Interior",
                :count 2}]}]}]}]}]}]}
   {:field "platforms",
    :subfields ["category"],
    :category
    [{:value "Earth Observation Satellites",
      :count 2,
      :subfields ["series_entity"],
      :series_entity
      [{:value "DIADEM",
        :count 2,
        :subfields ["short_name"],
        :short_name
        [{:value "DIADEM-1D",
          :count 2,
          :subfields ["long_name"],
          :long_name [{:value "Not Provided", :count 2}]}]}
       {:value
        "DMSP (Defense Meteorological Satellite Program)",
        :count 2,
        :subfields ["short_name"],
        :short_name
        [{:value "DMSP 5B/F3",
          :count 2,
          :subfields ["long_name"],
          :long_name
          [{:value
            "Defense Meteorological Satellite Program-F3",
            :count 2}]}]}]}]}
   {:field "instruments",
    :subfields ["category"],
    :category
    [{:value "Earth Remote Sensing Instruments",
      :count 2,
      :subfields ["class"],
      :class
      [{:value "Active Remote Sensing",
        :count 2,
        :subfields ["type"],
        :type
        [{:value "Altimeters",
          :count 2,
          :subfields ["subtype"],
          :subtype
          [{:value "Lidar/Laser Altimeters",
            :count 2,
            :subfields ["short_name"],
            :short_name
            [{:value "ATM",
              :count 2,
              :subfields ["long_name"],
              :long_name
              [{:value "Airborne Topographic Mapper",
                :count 2}]}
             {:value "LVIS",
              :count 2,
              :subfields ["long_name"],
              :long_name
              [{:value "Land, Vegetation, and Ice Sensor",
                :count 2}]}]}]}]}]}
     {:value "Not Provided", ; Instruments now include sensors as child instruments
      :count 2,
      :subfields ["class"],
      :class
        [{:value "Not Provided",
          :count 2,
          :subfields ["type"],
          :type
          [{:value "Not Provided",
            :count 2,
            :subfields ["subtype"],
            :subtype
            [{:value "Not Provided",
              :count 2,
              :subfields ["short_name"],
              :short_name
              [{:value "FROM_KMS-p0-i0-s0",
                :count 2,
                :subfields ["long_name"],
                :long_name [{:count 2, :value "Not Provided"}]}
               {:value "FROM_KMS-p0-i1-s0",
                :count 2,
                :subfields ["long_name"],
                :long_name [{:count 2, :value "Not Provided"}]}
               {:value "FROM_KMS-p1-i0-s0",
                :count 2,
                :subfields ["long_name"],
                :long_name [{:count 2, :value "Not Provided"}]}
               {:value "FROM_KMS-p1-i1-s0",
                :count 2,
                :subfields ["long_name"],
                :long_name [{:count 2, :value "Not Provided"}]}]}]}]}]}]}
   {:field "science_keywords",
    :subfields ["category"],
    :category
    [{:value "HURRICANE",
      :count 2,
      :subfields ["topic"],
      :topic
      [{:value "POPULAR",
        :count 2,
        :subfields ["term"],
        :term
        [{:value "EXTREME",
          :count 2,
          :subfields ["variable_level_1"],
          :variable_level_1
          [{:value "LEVEL2-1",
            :count 2,
            :subfields ["variable_level_2"],
            :variable_level_2
            [{:value "LEVEL2-2",
              :count 2,
              :subfields ["variable_level_3"],
              :variable_level_3
              [{:value "LEVEL2-3", :count 2}]}]}]}
         {:value "UNIVERSAL", :count 2}]}
       {:value "COOL",
        :count 2,
        :subfields ["term"],
        :term
        [{:value "TERM4",
          :count 2,
          :subfields ["variable_level_1"],
          :variable_level_1
          [{:value "UNIVERSAL", :count 2}]}]}]}
     {:value "UPCASE",
      :count 2,
      :subfields ["topic"],
      :topic
      [{:value "COOL",
        :count 2,
        :subfields ["term"],
        :term [{:value "MILD", :count 2}]}
       {:value "POPULAR",
        :count 2,
        :subfields ["term"],
        :term [{:value "MILD", :count 2}]}]}
     {:value "CAT1",
      :count 2,
      :subfields ["topic"],
      :topic
      [{:value "TOPIC1",
        :count 2,
        :subfields ["term"],
        :term
        [{:value "TERM1",
          :count 2,
          :subfields ["variable_level_1"],
          :variable_level_1
          [{:value "LEVEL1-1",
            :count 2,
            :subfields ["variable_level_2"],
            :variable_level_2
            [{:value "LEVEL1-2",
              :count 2,
              :subfields ["variable_level_3"],
              :variable_level_3
              [{:value "LEVEL1-3", :count 2}]}]}]}]}]}
     {:value "TORNADO",
      :count 2,
      :subfields ["topic"],
      :topic
      [{:value "POPULAR",
        :count 2,
        :subfields ["term"],
        :term [{:value "EXTREME", :count 2}]}]}]}
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
            :subregion_3
            [{:count 1, :value "GAZA STRIP"}],
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
