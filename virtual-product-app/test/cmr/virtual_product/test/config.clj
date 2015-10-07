(ns cmr.virtual-product.test.config
  (:require [clojure.test :refer :all]
            [cmr.common.util :as util]
            [clojure.string :as str]
            [cmr.virtual-product.config :as vp-config]))

(defn- assert-src-gran-ur-psa-equals
  "Assert that product specific attribute with the name source-granule-ur in virt-gran has the value
  given by expected-src-gran-ur"
  [virt-gran expected-src-gran-ur]
  (is (= expected-src-gran-ur (->> virt-gran
                                   :product-specific-attributes
                                   (filter #(= (:name %) vp-config/source-granule-ur-additional-attr-name))
                                   first
                                   :values
                                   first))))

(defn- remove-src-granule-ur-psa
  "Remove product specific attribute with the name source-granule-ur from the given psas."
  [psas]
  (seq (remove #(= (:name %) vp-config/source-granule-ur-additional-attr-name) psas)))


(deftest generate-virtual-granule-test
  (let [ast-l1a "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
        omuvbd "OMI/Aura Surface UVB Irradiance and Erythemal Dose Daily L3 Global 1.0x1.0 deg Grid V003"
        omto3d "OMI/Aura TOMS-Like Ozone, Aerosol Index, Cloud Radiance Fraction Daily L3 Global 1.0x1.0 deg V003"
        airx3std "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) V006"
        airx3stm "Aqua AIRS Level 3 Monthly Standard Physical Retrieval (AIRS+AMSU) V006"
        ast-l1t "ASTER Level 1 precision terrain corrected registered at-sensor radiance V003"
        ;; Generate access url objects from string urls
        gen-access-urls (fn [urls] (map #(hash-map :type "GET DATA" :url %) urls))
        opendap-url "http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5/some-file-name"
        non-opendap-url "http://s4psci.gesdisc.eosdis.nasa.gov/data/s4pa_TS2/some-file-name"
        frbt-data-pool-url "http://f5eil01v.edn.ecs.nasa.gov/FS1/ASTT/AST_L1T.003/2014.04.27/AST_L1T_00304272014172403_20140428144310_12345_T.tif"
        frbt-egi-url "http://f5eil01v.edn.ecs.nasa.gov:22500/egi_DEV07/request?REQUEST_MODE=STREAM&amp;SUBAGENT_ID=FRI&amp;FORMAT=TIR&amp;FILE_IDS=94306"
        frbv-data-pool-url "ftp://f5eil01v.edn.ecs.nasa.gov/FS1/ASTT/AST_L1T.003/2014.04.27/AST_L1T_00304272014172403_20140428144310_12345_V.tif"
        frbv-egi-url "http://f5eil01v.edn.ecs.nasa.gov:22500/egi_DEV07/request?REQUEST_MODE=STREAM&amp;SUBAGENT_ID=FRI&amp;FORMAT=VNIR&amp;FILE_IDS=94306"
        random-url "http://www.foo.com"]

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
           (assert-src-gran-ur-psa-equals generated-virt-gran (:granule-ur src-gran))
           (is (= expected-virt-gran-attrs (-> generated-virt-gran
                                               (dissoc :collection-ref)
                                               (update-in [:product-specific-attributes] remove-src-granule-ur-psa)
                                               util/remove-nil-keys))))

         ;; AST_L1A
         "LPDAAC_ECS" ast-l1a "AST_05"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST_05.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST_07"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST_07.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST_07XT"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST_07XT.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST_08"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST_08.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST_09"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST_09.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST_09XT"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST_09XT.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST_09T"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST_09T.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST14DEM"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST14DEM.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST14OTH"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST14OTH.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST14DMO"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST14DMO.003:2006227720"}


         ;; OMUVBD
         "GSFCS4PA" omuvbd "OMUVBd_ErythemalUV"
         {:granule-ur "OMUVBd.003:OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m0105t093001.he5"
          :related-urls (gen-access-urls [opendap-url non-opendap-url])
          :data-granule {:size 40}}
         {:granule-ur "OMUVBd_ErythemalUV.003:OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m0105t093001.he5"
          :related-urls (gen-access-urls [(str opendap-url "?" "ErythemalDailyDose,ErythemalDoseRate,UVindex,lon,lat") non-opendap-url])
          :data-granule {:size nil}}

         ;; OMTO3D
         "GSFCS4PA" omto3d "OMTO3d_O3_AI"
         {:granule-ur "OMTO3d.003:OMI-Aura_L3-OMTO3d_2004m1001_v003-2012m0405t174138.he5"
          :related-urls (gen-access-urls [opendap-url non-opendap-url])
          :data-granule {:size 40}}
         {:granule-ur "OMTO3d_O3_AI.003:OMI-Aura_L3-OMTO3d_2004m1001_v003-2012m0405t174138.he5"
          :related-urls (gen-access-urls [(str opendap-url "?" "ColumnAmountO3,UVAerosolIndex,lon,lat") non-opendap-url])
          :data-granule {:size nil}}

         ;; AIRX3STD
         "GSFCS4PA" airx3std "AIRX3STD_006_H2O_MMR_Surf"
         {:granule-ur "AIRX3STD.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-access-urls [opendap-url non-opendap-url])
          :data-granule {:size 40}}
         {:granule-ur "AIRX3STD_006_H2O_MMR_Surf.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-access-urls [(str opendap-url "?" "H2O_MMR_A,H2OPrsLvls_A,Latitude,Longitude") non-opendap-url])
          :data-granule {:size nil}}

         "GSFCS4PA" airx3std "AIRX3STD_006_OLR"
         {:granule-ur "AIRX3STD.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-access-urls [opendap-url non-opendap-url])
          :data-granule {:size 40}}
         {:granule-ur  "AIRX3STD_006_OLR.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-access-urls [(str opendap-url "?" "OLR_A,OLR_D,Latitude,Longitude") non-opendap-url])
          :data-granule {:size nil}}

         "GSFCS4PA" airx3std "AIRX3STD_006_SurfAirTemp"
         {:granule-ur "AIRX3STD.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-access-urls [opendap-url non-opendap-url])
          :data-granule {:size 40}}
         {:granule-ur "AIRX3STD_006_SurfAirTemp.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-access-urls [(str opendap-url "?" "SurfAirTemp_A,SurfAirTemp_D,Latitude,Longitude") non-opendap-url])
          :data-granule {:size nil}}

         "GSFCS4PA" airx3std "AIRX3STD_006_SurfSkinTemp"
         {:granule-ur "AIRX3STD.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-access-urls [opendap-url non-opendap-url])
          :data-granule {:size 40}}
         {:granule-ur "AIRX3STD_006_SurfSkinTemp.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-access-urls [(str opendap-url "?" "SurfSkinTemp_A,SurfSkinTemp_D,Latitude,Longitude") non-opendap-url])
          :data-granule {:size nil}}

         "GSFCS4PA" airx3std "AIRX3STD_006_TotCO"
         {:granule-ur "AIRX3STD.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-access-urls [opendap-url non-opendap-url])
          :data-granule {:size 40}}
         {:granule-ur "AIRX3STD_006_TotCO.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-access-urls [(str opendap-url "?" "TotCO_A,TotCO_D,Latitude,Longitude") non-opendap-url])
          :data-granule {:size nil}}

         ;; AIRX3STM
         "GSFCS4PA" airx3stm "AIRX3STM_006_ClrOLR"
         {:granule-ur "AIRX3STM.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
          :related-urls (gen-access-urls [opendap-url non-opendap-url])
          :data-granule {:size 40}}
         {:granule-ur "AIRX3STM_006_ClrOLR.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
          :related-urls (gen-access-urls [(str opendap-url "?" "ClrOLR_A,ClrOLR_D,Latitude,Longitude") non-opendap-url])
          :data-granule {:size nil}}

         "LPDAAC_ECS" ast-l1t "AST_FRBT"
         {:granule-ur "SC:AST_L1T.003:2148809731"
          :related-urls (concat [{:type "GET RELATED VISUALIZATION" :url random-url}]
                                (gen-access-urls [frbt-data-pool-url frbt-egi-url random-url]))
          :product-specific-attributes [{:name "FullResolutionThermalBrowseAvailable" :values ["YES"]}]}
         {:granule-ur "SC:AST_FRBT.003:2148809731"
          :related-urls (gen-access-urls [frbt-data-pool-url frbt-egi-url])}

         "LPDAAC_ECS" ast-l1t "AST_FRBT"
         {:granule-ur "SC:AST_L1T.003:2148809731"
          :related-urls (gen-access-urls [frbt-data-pool-url frbt-egi-url random-url])
          :product-specific-attributes [{:name "FullResolutionThermalBrowseAvailable" :values ["NO"]}]}
         {:granule-ur "SC:AST_FRBT.003:2148809731"}

         "LPDAAC_ECS" ast-l1t "AST_FRBV"
         {:granule-ur "SC:AST_L1T.003:2148809731"
          :related-urls (concat [{:type "GET RELATED VISUALIZATION" :url random-url}]
                                (gen-access-urls [frbv-data-pool-url frbv-egi-url random-url]))
          :product-specific-attributes [{:name "FullResolutionVisibleBrowseAvailable" :values ["YES"]}]}
         {:granule-ur "SC:AST_FRBV.003:2148809731"
          :related-urls (gen-access-urls [frbv-data-pool-url frbv-egi-url])}

         "LPDAAC_ECS" ast-l1t "AST_FRBV"
         {:granule-ur "SC:AST_L1T.003:2148809731"
          :related-urls (gen-access-urls [frbv-data-pool-url frbv-egi-url random-url])}
         {:granule-ur "SC:AST_FRBV.003:2148809731"})))

