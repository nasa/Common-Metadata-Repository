(ns cmr.virtual-product.test.config
  (:require [clojure.test :refer :all]
            [cmr.common.util :as util]
            [clojure.string :as str]
            [cmr.virtual-product.config :as vp-config]))

(defn- assert-src-gran-ur-psa-equals
  "Assert that product specific attribute with the name source-granule-ur in virt-gran has the value
  given by expected-src-gran-ur"
  [virt-gran expected-src-gran-ur]
  (is (= expected-src-gran-ur (-> virt-gran
                                  :product-specific-attributes
                                  ((partial filter #(= (:name %) vp-config/source-granule-ur-additional-attr-name)))
                                  first
                                  :values
                                  first))))

(defn- remove-src-granule-ur-psa
  "Remove product specific attribute with the name source-granule-ur from the given psas."
  [psas]
  (seq (remove #(= (:name %) vp-config/source-granule-ur-additional-attr-name) psas)))


(deftest generate-virtual-granule-test
  (let [ast-l1a "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
        ast-l1a-gran-ur "SC:AST_L1A.003:2006227720"
        omuvbd "OMI/Aura Surface UVB Irradiance and Erythemal Dose Daily L3 Global 1.0x1.0 deg Grid V003"
        omto3d "OMI/Aura TOMS-Like Ozone, Aerosol Index, Cloud Radiance Fraction Daily L3 Global 1.0x1.0 deg V003"
        airx3std "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) V006"
        airx3std-gran-ur "AIRX3STD.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
        airx3stm "Aqua AIRS Level 3 Monthly Standard Physical Retrieval (AIRS+AMSU) V006"
        ;; Generate access url objects from string urls
        gen-access-urls (fn [urls] (map #(hash-map :type "GET DATA" :url %) urls))
        opendap-url "http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5/some-file-name"
        non-opendap-url "http://s4psci.gesdisc.eosdis.nasa.gov/data/s4pa_TS2/some-file-name"]

    (are [provider-id src-entry-title virt-short-name src-gran-attrs expected-virt-gran-attrs]
         (let [src-vp-config (vp-config/source-to-virtual-product-config [provider-id src-entry-title])
               src-short-name (:short-name src-vp-config)
               virt-entry-title (-> src-vp-config
                                    :virtual-collections
                                    ((partial filter #(= virt-short-name (:short-name %))))
                                    first
                                    :entry-title)
               virt-coll {:entry-title virt-entry-title
                          :short-name virt-short-name}
               src-gran (assoc src-gran-attrs
                               :collection-ref {:entry-title src-entry-title})
               generated-virt-gran (vp-config/generate-virtual-granule-umm
                                     provider-id src-short-name src-gran virt-coll)]

           (is (= virt-entry-title (get-in generated-virt-gran [:collection-ref :entry-title])))
           (is (assert-src-gran-ur-psa-equals generated-virt-gran (:granule-ur src-gran)))
           (is (= expected-virt-gran-attrs (-> generated-virt-gran
                                               (dissoc :collection-ref)
                                               (update-in [:product-specific-attributes] remove-src-granule-ur-psa)
                                               util/remove-nil-keys))))

         "LPDAAC_ECS" ast-l1a "AST_05"
         {:granule-ur ast-l1a-gran-ur} {:granule-ur (str/replace ast-l1a-gran-ur "AST_L1A" "AST_05")}

         "LPDAAC_ECS" ast-l1a "AST_07"
         {:granule-ur ast-l1a-gran-ur} {:granule-ur (str/replace ast-l1a-gran-ur "AST_L1A" "AST_07")}

         "LPDAAC_ECS" ast-l1a "AST_07XT"
         {:granule-ur ast-l1a-gran-ur} {:granule-ur (str/replace ast-l1a-gran-ur "AST_L1A" "AST_07XT")}

         "LPDAAC_ECS" ast-l1a "AST_08"
         {:granule-ur ast-l1a-gran-ur} {:granule-ur (str/replace ast-l1a-gran-ur "AST_L1A" "AST_08")}

         "LPDAAC_ECS" ast-l1a "AST_09"
         {:granule-ur ast-l1a-gran-ur} {:granule-ur (str/replace ast-l1a-gran-ur "AST_L1A" "AST_09")}

         "LPDAAC_ECS" ast-l1a "AST_09XT"
         {:granule-ur ast-l1a-gran-ur} {:granule-ur (str/replace ast-l1a-gran-ur "AST_L1A" "AST_09XT")}

         "LPDAAC_ECS" ast-l1a "AST_09T"
         {:granule-ur ast-l1a-gran-ur} {:granule-ur (str/replace ast-l1a-gran-ur "AST_L1A" "AST_09T")}

         "LPDAAC_ECS" ast-l1a "AST14DEM"
         {:granule-ur ast-l1a-gran-ur} {:granule-ur (str/replace ast-l1a-gran-ur "AST_L1A" "AST14DEM")}

         "LPDAAC_ECS" ast-l1a "AST14OTH"
         {:granule-ur ast-l1a-gran-ur} {:granule-ur (str/replace ast-l1a-gran-ur "AST_L1A" "AST14OTH")}

         "LPDAAC_ECS" ast-l1a "AST14DMO"
         {:granule-ur ast-l1a-gran-ur} {:granule-ur (str/replace ast-l1a-gran-ur "AST_L1A" "AST14DMO")}


         "GSFCS4PA" omuvbd "OMUVBd_ErythemalUV"
         {:granule-ur "OMUVBd.003:OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m0105t093001.he5"
          :related-urls (gen-access-urls [opendap-url non-opendap-url])
          :data-granule {:size 40}}
         {:granule-ur "OMUVBd_ErythemalUV.003:OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m0105t093001.he5"
          :related-urls (gen-access-urls [(str opendap-url "?" "ErythemalDailyDose,ErythemalDoseRate,UVindex,lon,lat") non-opendap-url])
          :data-granule {:size nil}}

         "GSFCS4PA" omto3d "OMTO3d_O3_AI"
         {:granule-ur "OMTO3d.003:OMI-Aura_L3-OMTO3d_2004m1001_v003-2012m0405t174138.he5"
          :related-urls (gen-access-urls [opendap-url non-opendap-url])
          :data-granule {:size 40}}
         {:granule-ur "OMTO3d_O3_AI.003:OMI-Aura_L3-OMTO3d_2004m1001_v003-2012m0405t174138.he5"
          :related-urls (gen-access-urls [(str opendap-url "?" "ColumnAmountO3,UVAerosolIndex,lon,lat") non-opendap-url])
          :data-granule {:size nil}}

         "GSFCS4PA" airx3std "AIRX3STD_006_H2O_MMR_Surf"
         {:granule-ur airx3std-gran-ur
          :related-urls (gen-access-urls [opendap-url non-opendap-url])
          :data-granule {:size 40}}
         {:granule-ur (str/replace airx3std-gran-ur "AIRX3STD" "AIRX3STD_006_H2O_MMR_Surf")
          :related-urls (gen-access-urls [(str opendap-url "?" "H2O_MMR_A,H2OPrsLvls_A,Latitude,Longitude") non-opendap-url])
          :data-granule {:size nil}}

         "GSFCS4PA" airx3std "AIRX3STD_006_OLR"
         {:granule-ur airx3std-gran-ur
          :related-urls (gen-access-urls [opendap-url non-opendap-url])
          :data-granule {:size 40}}
         {:granule-ur  (str/replace airx3std-gran-ur "AIRX3STD" "AIRX3STD_006_OLR")
          :related-urls (gen-access-urls [(str opendap-url "?" "OLR_A,OLR_D,Latitude,Longitude") non-opendap-url])
          :data-granule {:size nil}}

         "GSFCS4PA" airx3std "AIRX3STD_006_SurfAirTemp"
         {:granule-ur airx3std-gran-ur
          :related-urls (gen-access-urls [opendap-url non-opendap-url])
          :data-granule {:size 40}}
         {:granule-ur (str/replace airx3std-gran-ur "AIRX3STD" "AIRX3STD_006_SurfAirTemp")
          :related-urls (gen-access-urls [(str opendap-url "?" "SurfAirTemp_A,SurfAirTemp_D,Latitude,Longitude") non-opendap-url])
          :data-granule {:size nil}}

         "GSFCS4PA" airx3std "AIRX3STD_006_SurfSkinTemp"
         {:granule-ur airx3std-gran-ur
          :related-urls (gen-access-urls [opendap-url non-opendap-url])
          :data-granule {:size 40}}
         {:granule-ur (str/replace airx3std-gran-ur "AIRX3STD" "AIRX3STD_006_SurfSkinTemp")
          :related-urls (gen-access-urls [(str opendap-url "?" "SurfSkinTemp_A,SurfSkinTemp_D,Latitude,Longitude") non-opendap-url])
          :data-granule {:size nil}}

         "GSFCS4PA" airx3std "AIRX3STD_006_TotCO"
         {:granule-ur airx3std-gran-ur
          :related-urls (gen-access-urls [opendap-url non-opendap-url])
          :data-granule {:size 40}}
         {:granule-ur (str/replace airx3std-gran-ur "AIRX3STD" "AIRX3STD_006_TotCO")
          :related-urls (gen-access-urls [(str opendap-url "?" "TotCO_A,TotCO_D,Latitude,Longitude") non-opendap-url])
          :data-granule {:size nil}}

         "GSFCS4PA" airx3stm "AIRX3STM_006_ClrOLR"
         {:granule-ur "AIRX3STM.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
          :related-urls (gen-access-urls [opendap-url non-opendap-url])
          :data-granule {:size 40}}
         {:granule-ur "AIRX3STM_006_ClrOLR.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
          :related-urls (gen-access-urls [(str opendap-url "?" "ClrOLR_A,ClrOLR_D,Latitude,Longitude") non-opendap-url])
          :data-granule {:size nil}})))

