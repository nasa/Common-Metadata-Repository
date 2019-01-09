(ns cmr.metadata.proxy.tests.unit.concepts.granule
  "Unit tests for the granule concepts."
  (:require
   [clojure.test :refer :all]
   [cmr.metadata.proxy.concepts.granule :as granule]))

(def sample-granule-entry
  "Sample granule entry used in the tests."
  {:producer_granule_id "MOD13Q1.A2000049.h23v09.006.2015136104649.hdf",
   :time_start "2000-02-18T00:00:00.000Z",
   :cloud_cover "79.0",
   :updated "2017-01-23T14:11:51.777Z",
   :dataset_id "MODIS/Terra Vegetation Indices 16-Day L3 Global 250m SIN Grid V006",
   :data_center "DEMO_PROV",
   :title "SC:MOD13Q1.006:2160434507",
   :coordinate_system "GEODETIC",
   :day_night_flag "DAY",
   :time_end "2000-03-04T23:59:59.000Z",
   :id "G1200241220-DEMO_PROV",
   :original_format "ECHO10",
   :granule_size "5.10831",
   :browse_flag true,
   :polygons [["-9.9999025 60.9317071 0.0053588 60.013813 -0.0001006 49.8129527 -10.004658 50.5752222 -9.9999025 60.9317071"]],
   :collection_concept_id "C1200241219-DEMO_PROV",
   :online_access_flag true,
   :links [{:rel "http://esipfed.org/ns/fedsearch/1.1/data#",
            :type "application/x-hdfeos",
            :hreflang "en-US",
            :href "https://e4ftl01.cr.usgs.gov//DP106/MOLT/MOD13Q1.006/2000.02.18/MOD13Q1.A2000049.h23v09.006.2015136104649.hdf"}
           {:rel "http://esipfed.org/ns/fedsearch/1.1/metadata#",
            :type "application/x-hdfeos",
            :hreflang "en-US",
            :title "(GET DATA : OPENDAP DATA (DODS))",
            :href "https://opendap.cr.usgs.gov/opendap/hyrax//DP106/MOLT/MOD13Q1.006/2000.02.18/MOD13Q1.A2000049.h23v09.006.2015136104649.hdf"}
           {:rel "http://esipfed.org/ns/fedsearch/1.1/browse#",
            :type "image/jpeg",
            :hreflang "en-US",
            :title "(BROWSE)",
            :href "https://e4ftl01.cr.usgs.gov//WORKING/BRWS/Browse.001/2015.05.16/BROWSE.MOD13Q1.A2000049.h23v09.006.2015136104649.1.jpg"}
           {:rel "http://esipfed.org/ns/fedsearch/1.1/browse#",
            :type "image/jpeg",
            :hreflang "en-US",
            :title "(BROWSE)",
            :href "https://e4ftl01.cr.usgs.gov//WORKING/BRWS/Browse.001/2015.05.16/BROWSE.MOD13Q1.A2000049.h23v09.006.2015136104649.2.jpg"}
           {:rel "http://esipfed.org/ns/fedsearch/1.1/metadata#",
            :type "text/xml",
            :hreflang "en-US",
            :title "(METADATA)",
            :href "https://e4ftl01.cr.usgs.gov//DP106/MOLT/MOD13Q1.006/2000.02.18/MOD13Q1.A2000049.h23v09.006.2015136104649.hdf.xml"}
           {:rel "http://esipfed.org/ns/fedsearch/1.1/metadata#",
            :hreflang "en-US",
            :inherited true,
            :href "https://dx.doi.org/10.5067/MODIS/MOD13Q1.006"}
           {:rel "http://esipfed.org/ns/fedsearch/1.1/data#",
            :hreflang "en-US",
            :inherited true,
            :href "https://e4ftl01.cr.usgs.gov/MOLT/MOD13Q1.006/"}
           {:rel "http://esipfed.org/ns/fedsearch/1.1/data#",
            :hreflang "en-US",
            :inherited true,
            :href "http://earthexplorer.usgs.gov/"}
           {:rel "http://esipfed.org/ns/fedsearch/1.1/metadata#",
            :hreflang "en-US",
            :inherited true,
            :href "https://lpdaac.usgs.gov/"}]})

(deftest extract-granule-links
  (testing "Both OPeNDAP and data file links."
    (let [expected {:granule-id "G1200241220-DEMO_PROV"
                    :opendap-link {:rel "http://esipfed.org/ns/fedsearch/1.1/metadata#"
                                   :type "application/x-hdfeos"
                                   :hreflang "en-US"
                                   :title "(GET DATA : OPENDAP DATA (DODS))"
                                   :href "https://opendap.cr.usgs.gov/opendap/hyrax//DP106/MOLT/MOD13Q1.006/2000.02.18/MOD13Q1.A2000049.h23v09.006.2015136104649.hdf"}
                    :datafile-link {:rel "http://esipfed.org/ns/fedsearch/1.1/data#"
                                    :type "application/x-hdfeos"
                                    :hreflang "en-US"
                                    :href "https://e4ftl01.cr.usgs.gov//DP106/MOLT/MOD13Q1.006/2000.02.18/MOD13Q1.A2000049.h23v09.006.2015136104649.hdf"}}
          actual (granule/extract-granule-links sample-granule-entry)]
      (is (= expected actual))))
  (testing "Only an OPeNDAP link."
    (let [granule-entry (update sample-granule-entry
                                :links
                                (fn [links]
                                  (remove #(= "https://e4ftl01.cr.usgs.gov//DP106/MOLT/MOD13Q1.006/2000.02.18/MOD13Q1.A2000049.h23v09.006.2015136104649.hdf"
                                              (:href %))
                                          links)))
          expected {:granule-id "G1200241220-DEMO_PROV"
                    :opendap-link {:rel "http://esipfed.org/ns/fedsearch/1.1/metadata#"
                                   :type "application/x-hdfeos"
                                   :hreflang "en-US"
                                   :title "(GET DATA : OPENDAP DATA (DODS))"
                                   :href "https://opendap.cr.usgs.gov/opendap/hyrax//DP106/MOLT/MOD13Q1.006/2000.02.18/MOD13Q1.A2000049.h23v09.006.2015136104649.hdf"}
                    :datafile-link nil}
          actual (granule/extract-granule-links granule-entry)]
      (is (= expected actual))))
  (testing "Only a data file link."
    (let [granule-entry (update sample-granule-entry
                                :links
                                (fn [links]
                                  (remove #(= "(GET DATA : OPENDAP DATA (DODS))"
                                              (:title %))
                                          links)))
          expected {:granule-id "G1200241220-DEMO_PROV"
                    :opendap-link nil
                    :datafile-link {:rel "http://esipfed.org/ns/fedsearch/1.1/data#"
                                    :type "application/x-hdfeos"
                                    :hreflang "en-US"
                                    :href "https://e4ftl01.cr.usgs.gov//DP106/MOLT/MOD13Q1.006/2000.02.18/MOD13Q1.A2000049.h23v09.006.2015136104649.hdf"}}
          actual (granule/extract-granule-links granule-entry)]
      (is (= expected actual))))
  (testing "Multiple OPeNDAP links returns only the first one."
    (let [granule-entry (update sample-granule-entry
                                :links
                                (fn [links]
                                  (cons {:title "Another OPENDAP link."
                                         :href "https://example.com/opendap-link"}
                                        links)))
          expected {:granule-id "G1200241220-DEMO_PROV"
                    :opendap-link {:title "Another OPENDAP link."
                                   :href "https://example.com/opendap-link"}
                    :datafile-link {:rel "http://esipfed.org/ns/fedsearch/1.1/data#"
                                    :type "application/x-hdfeos"
                                    :hreflang "en-US"
                                    :href "https://e4ftl01.cr.usgs.gov//DP106/MOLT/MOD13Q1.006/2000.02.18/MOD13Q1.A2000049.h23v09.006.2015136104649.hdf"}}
          actual (granule/extract-granule-links granule-entry)]
      (is (= expected actual))))
  (testing "No data file or opendap links."
    (let [granule-entry (update sample-granule-entry
                                :links
                                (fn [links]
                                  (remove (fn [link]
                                            (or (= "(GET DATA : OPENDAP DATA (DODS))"
                                                   (:title link))
                                                (= "https://e4ftl01.cr.usgs.gov//DP106/MOLT/MOD13Q1.006/2000.02.18/MOD13Q1.A2000049.h23v09.006.2015136104649.hdf"
                                                   (:href link))))
                                          links)))
          expected {:errors ["There was a problem extracting an OPeNDAP URL or data URL from the granule's metadata file."
                             "Problematic granules: [G1200241220-DEMO_PROV]."]}
          ; expected {:errors ["There was a problem extracting an OPeNDAP URL or data URL from the granule's metadata file."]}
          actual (granule/extract-granule-links granule-entry)]
      (is (= expected actual)))))
