(ns cmr.virtual-product.test.data.source-to-virtual-mapping
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [cmr.common.util :as util]
            [cmr.common.mime-types :as mt]
            [cmr.umm.umm-granule :as umm-g]
            [cmr.umm.echo10.granule :as g]
            [cmr.virtual-product.data.source-to-virtual-mapping :as svm]))

(def airx3std-measured-parameters
  "Defines the AIRX3STD measured parameters parsed from a sample AIRX3STD granule."
  (-> "data/AIRX3STD/granules/airx3std_granule.xml"
      io/resource
      slurp
      g/parse-granule
      :measured-parameters))

(defn- assert-src-gran-ur-psa-equals
  "Assert that product specific attribute with the name source_granule_ur in virt-gran has the value
  given by expected-src-gran-ur"
  [virt-gran expected-src-gran-ur]
  (is (= expected-src-gran-ur (->> virt-gran
                                   :product-specific-attributes
                                   (filter #(= (:name %) svm/source-granule-ur-additional-attr-name))
                                   first
                                   :values
                                   first))))

(defn- remove-src-granule-ur-psa
  "Remove product specific attribute with the name source_granule_ur from the given psas."
  [psas]
  (seq (remove #(= (:name %) svm/source-granule-ur-additional-attr-name) psas)))

(deftest generate-virtual-granule-test
  (let [ast-l1a "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
        omuvbd "OMI/Aura Surface UVB Irradiance and Erythemal Dose Daily L3 Global 1.0x1.0 deg Grid V003 (OMUVBd) at GES DISC"
        airx3std "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) V006 (AIRX3STD) at GES DISC"
        airx3stm "Aqua AIRS Level 3 Monthly Standard Physical Retrieval (AIRS+AMSU) V006 (AIRX3STM) at GES DISC"
        gldas_noah10_3h "GLDAS Noah Land Surface Model L4 3 hourly 1.0 x 1.0 degree V2.0 (GLDAS_NOAH10_3H) at GES DISC"
        gldas_noah10_m "GLDAS Noah Land Surface Model L4 Monthly 1.0 x 1.0 degree V2.0 (GLDAS_NOAH10_M) at GES DISC"
        ast-l1t "ASTER Level 1 precision terrain corrected registered at-sensor radiance V003"
        ;; Generate access url objects from string urls
        gen-access-urls (fn [urls] (map #(hash-map :type "GET DATA" :url %) urls))
        gen-resource-urls (fn [urls] (map #(hash-map :type "USE SERVICE API" :sub-type "OPENDAP DATA" :url %) urls))
        gen-browse-urls (fn [urls] (map #(hash-map :type "GET RELATED VISUALIZATION" :url %) urls))
        gen-qa-urls (fn [urls] (map #(hash-map :type "Quality Assurance"
                                               :mime-type "text/plain"
                                               :url %) urls))
        gen-measured-parameter (fn [{:keys [parameter-name qa-stats qa-flags]}]
                                 (umm-g/map->MeasuredParameter
                                   {:parameter-name parameter-name
                                    :qa-stats (when (seq qa-stats) (umm-g/map->QAStats qa-stats))
                                    :qa-flags (when (seq qa-flags) (umm-g/map->QAFlags qa-flags))}))
        opendap-url "http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5/some-file-name"
        non-opendap-url "http://s4psci.gesdisc.eosdis.nasa.gov/data/s4pa_TS2/some-file-name"
        frbt-data-pool-url "http://f5eil01v.edn.ecs.nasa.gov/FS1/ASTT/AST_L1T.003/2014.04.27/AST_L1T_00304272014172403_20140428144310_12345_T.tif"
        frbt-egi-url "http://f5eil01v.edn.ecs.nasa.gov:22500/egi_DEV07/request?REQUEST_MODE=STREAM&amp;SUBAGENT_ID=FRI&amp;FORMAT=TIR&amp;FILE_IDS=94306"
        frbv-data-pool-url "ftp://f5eil01v.edn.ecs.nasa.gov/FS1/ASTT/AST_L1T.003/2014.04.27/AST_L1T_00304272014172403_20140428144310_12345_V.tif"
        frbv-egi-url "http://f5eil01v.edn.ecs.nasa.gov:22500/egi_DEV07/request?REQUEST_MODE=STREAM&amp;SUBAGENT_ID=FRI&amp;FORMAT=VNIR&amp;FILE_IDS=94306"
        frbt-browse-url "ftp://f5eil01v.edn.ecs.nasa.gov/FS1/BRWS/Browse.001/2015.09.18/AST_L1T_00304122000183005_20150409110105_78507_BR.3.TIR.jpg"
        frbv-browse-url "ftp://f5eil01v.edn.ecs.nasa.gov/FS1/BRWS/Browse.001/2015.09.18/AST_L1T_00304122000183005_20150409110105_78507_BR.2.VNIR.jpg"
        ast-l1t-qa-url "ftp://f5eil01v.edn.ecs.nasa.gov/FS1/OTHR/QA.001/2015.09.18/AST_L1T_00304122000183005_20150409110105_78507_QA.txt"
        ast-l1t-qa-browse-url "ftp://f5eil01v.edn.ecs.nasa.gov/FS1/BRWS/Browse.001/2015.09.18/AST_L1T_00304122000183005_20150409110105_78507_BR.4.QA.jpg"
        random-url "http://www.foo.com"]

    (are [provider-id src-entry-title virt-short-name virt-version-id
          src-gran-attrs expected-virt-gran-attrs]
         (let [src-vp-config (svm/source-to-virtual-product-mapping [provider-id src-entry-title])
               src-short-name (:short-name src-vp-config)
               virt-coll {:short-name virt-short-name
                          :version-id virt-version-id}
               src-gran (assoc src-gran-attrs
                               :collection-ref {:entry-title src-entry-title})
               generated-virt-gran (svm/generate-virtual-granule-umm
                                     provider-id src-short-name src-gran virt-coll)]

           (is (= virt-short-name (get-in generated-virt-gran [:collection-ref :short-name])))
           (is (= virt-version-id (get-in generated-virt-gran [:collection-ref :version-id])))
           (assert-src-gran-ur-psa-equals generated-virt-gran (:granule-ur src-gran))
           (is (= expected-virt-gran-attrs (-> generated-virt-gran
                                               (dissoc :collection-ref)
                                               (update-in [:product-specific-attributes] remove-src-granule-ur-psa)
                                               (update-in [:related-urls] #(seq (map util/remove-nil-keys %)))
                                               util/remove-nil-keys))))

         ;; AST_L1A
         "LPDAAC_ECS" ast-l1a "AST_05" "003"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST_05.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST_07" "003"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST_07.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST_07XT" "003"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST_07XT.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST_08" "003"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST_08.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST_09" "003"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST_09.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST_09XT" "003"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST_09XT.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST_09T" "003"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST_09T.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST14DEM" "003"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST14DEM.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST14OTH" "003"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST14OTH.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST14DMO" "003"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST14DMO.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST_L1B" "003"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST_L1B.003:2006227720"}

         "LPDAAC_ECS" ast-l1a "AST_L1T" "031"
         {:granule-ur "SC:AST_L1A.003:2006227720"} {:granule-ur "SC:AST_L1T.031:2006227720"}

         ;; OMUVBD
         "GES_DISC" omuvbd "OMUVBd_ErythemalUV" "003"
         {:granule-ur "OMUVBd.003:OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m0105t093001.he5"
          :related-urls (concat (gen-resource-urls [opendap-url]) (gen-access-urls [non-opendap-url]))
          :data-granule {:size 40}}
         {:granule-ur "OMUVBd_ErythemalUV.003:OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m0105t093001.he5"
          :related-urls (gen-resource-urls [(str opendap-url ".nc?" "ErythemalDailyDose,ErythemalDoseRate,UVindex,lon,lat")])
          :data-granule {:size nil}}

         ;; AIRX3STD
         "GES_DISC" airx3std "AIRX3STD_H2O_MMR_Surf" "006"
         {:granule-ur "AIRX3STD.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-resource-urls [opendap-url])
          :measured-parameters airx3std-measured-parameters
          :data-granule {:size 40}}
         {:granule-ur "AIRX3STD_H2O_MMR_Surf.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-resource-urls [(str opendap-url ".nc?" "H2O_MMR_A,H2O_MMR_D,Latitude,Longitude")])
          :data-granule {:size nil}}

         "GES_DISC" airx3std "AIRX3STD_OLR" "006"
         {:granule-ur "AIRX3STD.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-resource-urls [opendap-url])
          :measured-parameters airx3std-measured-parameters
          :data-granule {:size 40}}
         {:granule-ur  "AIRX3STD_OLR.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-resource-urls [(str opendap-url ".nc?" "OLR_A,OLR_D,Latitude,Longitude")])
          :data-granule {:size nil}}

         "GES_DISC" airx3std "AIRX3STD_SurfAirTemp" "006"
         {:granule-ur "AIRX3STD.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-resource-urls [opendap-url])
          :measured-parameters airx3std-measured-parameters
          :data-granule {:size 40}}
         {:granule-ur "AIRX3STD_SurfAirTemp.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-resource-urls [(str opendap-url ".nc?" "SurfAirTemp_A,SurfAirTemp_D,Latitude,Longitude")])
          :data-granule {:size nil}}

         "GES_DISC" airx3std "AIRX3STD_SurfSkinTemp" "006"
         {:granule-ur "AIRX3STD.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-resource-urls [opendap-url])
          :measured-parameters airx3std-measured-parameters
          :data-granule {:size 40}}
         {:granule-ur "AIRX3STD_SurfSkinTemp.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-resource-urls [(str opendap-url ".nc?" "SurfSkinTemp_A,SurfSkinTemp_D,Latitude,Longitude")])
          :data-granule {:size nil}}

         "GES_DISC" airx3std "AIRX3STD_TotCO" "006"
         {:granule-ur "AIRX3STD.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-resource-urls [opendap-url])
          :measured-parameters airx3std-measured-parameters
          :data-granule {:size 40}}
         {:granule-ur "AIRX3STD_TotCO.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-resource-urls [(str opendap-url ".nc?" "TotCO_A,TotCO_D,Latitude,Longitude")])
          :data-granule {:size nil}}

         "GES_DISC" airx3std "AIRX3STD_ClrOLR" "006"
         {:granule-ur "AIRX3STD.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-resource-urls [opendap-url])
          :measured-parameters airx3std-measured-parameters
          :data-granule {:size 40}}
         {:granule-ur "AIRX3STD_ClrOLR.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-resource-urls [(str opendap-url ".nc?" "ClrOLR_A,ClrOLR_D,Latitude,Longitude")])
          :data-granule {:size nil}}

         "GES_DISC" airx3std "AIRX3STD_TotCH4" "006"
         {:granule-ur "AIRX3STD.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-resource-urls [opendap-url])
          :measured-parameters airx3std-measured-parameters
          :data-granule {:size 40}}
         {:granule-ur "AIRX3STD_TotCH4.006:AIRS.2002.08.31.L3.RetStd001.v6.0.9.0.G13208034313.hdf"
          :related-urls (gen-resource-urls [(str opendap-url ".nc?" "TotCH4_A,TotCH4_D,Latitude,Longitude")])
          :data-granule {:size nil}}

         ;; AIRX3STM
         "GES_DISC" airx3stm "AIRX3STM_ClrOLR" "006"
         {:granule-ur "AIRX3STM.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
          :related-urls (gen-resource-urls [opendap-url])
          :data-granule {:size 40}}
         {:granule-ur "AIRX3STM_ClrOLR.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
          :related-urls (gen-resource-urls [(str opendap-url ".nc?" "ClrOLR_A,ClrOLR_D,Latitude,Longitude")])
          :data-granule {:size nil}}

         "GES_DISC" airx3stm "AIRX3STM_H2O_MMR_Surf" "006"
         {:granule-ur "AIRX3STM.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
          :related-urls (gen-resource-urls [opendap-url])
          :data-granule {:size 40}}
         {:granule-ur "AIRX3STM_H2O_MMR_Surf.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
          :related-urls (gen-resource-urls [(str opendap-url ".nc?" "H2O_MMR_A,H2O_MMR_D,Latitude,Longitude")])
          :data-granule {:size nil}}

         "GES_DISC" airx3stm "AIRX3STM_OLR" "006"
         {:granule-ur "AIRX3STM.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
          :related-urls (gen-resource-urls [opendap-url])
          :data-granule {:size 40}}
         {:granule-ur "AIRX3STM_OLR.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
          :related-urls (gen-resource-urls [(str opendap-url ".nc?" "OLR_A,OLR_D,Latitude,Longitude")])
          :data-granule {:size nil}}

         "GES_DISC" airx3stm "AIRX3STM_SurfAirTemp" "006"
         {:granule-ur "AIRX3STM.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
          :related-urls (gen-resource-urls [opendap-url])
          :data-granule {:size 40}}
         {:granule-ur "AIRX3STM_SurfAirTemp.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
          :related-urls (gen-resource-urls [(str opendap-url ".nc?" "SurfAirTemp_A,SurfAirTemp_D,Latitude,Longitude")])
          :data-granule {:size nil}}

         "GES_DISC" airx3stm "AIRX3STM_SurfSkinTemp" "006"
         {:granule-ur "AIRX3STM.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
          :related-urls (gen-resource-urls [opendap-url])
          :data-granule {:size 40}}
         {:granule-ur "AIRX3STM_SurfSkinTemp.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
          :related-urls (gen-resource-urls [(str opendap-url ".nc?" "SurfSkinTemp_A,SurfSkinTemp_D,Latitude,Longitude")])
          :data-granule {:size nil}}

         "GES_DISC" airx3stm "AIRX3STM_TotCO" "006"
         {:granule-ur "AIRX3STM.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
          :related-urls (gen-resource-urls [opendap-url])
          :data-granule {:size 40}}
         {:granule-ur "AIRX3STM_TotCO.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
          :related-urls (gen-resource-urls [(str opendap-url ".nc?" "TotCO_A,TotCO_D,Latitude,Longitude")])
          :data-granule {:size nil}}

         "GES_DISC" airx3stm "AIRX3STM_TotCH4" "006"
         {:granule-ur "AIRX3STM.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
          :related-urls (gen-resource-urls [opendap-url])
          :data-granule {:size 40}}
         {:granule-ur "AIRX3STM_TotCH4.006:AIRS.2002.09.01.L3.RetStd030.v6.0.9.0.G13208054216.hdf"
          :related-urls (gen-resource-urls [(str opendap-url ".nc?" "TotCH4_A,TotCH4_D,Latitude,Longitude")])
          :data-granule {:size nil}}

         ; GLDAS_NOAH10_3H
         "GES_DISC" gldas_noah10_3h "GLDAS_NOAH10_3Hourly" "2.0"
         {:granule-ur "GLDAS_NOAH10_3H.2.0:GLDAS_NOAH10_3H.A19480101.0300.020.nc4"
          :related-urls (gen-resource-urls [opendap-url])
          :data-granule {:size 40}}
         {:granule-ur "GLDAS_NOAH10_3Hourly.2.0:GLDAS_NOAH10_3H.A19480101.0300.020.nc4"
          :related-urls (gen-resource-urls [(str opendap-url ".nc?" "Rainf_tavg,AvgSurfT_inst,SoilMoi0_10cm_inst,time,lat,lon")])
          :data-granule {:size nil}}

         ;; GLDAS_NOAH10_M
         "GES_DISC" gldas_noah10_m "GLDAS_NOAH10_Monthly" "2.0"
         {:granule-ur "GLDAS_NOAH10_M.2.0:GLDAS_NOAH10_M.A194801.020.nc4"
          :related-urls (gen-resource-urls [opendap-url])
          :data-granule {:size 40}}
         {:granule-ur "GLDAS_NOAH10_Monthly.2.0:GLDAS_NOAH10_M.A194801.020.nc4"
          :related-urls (gen-resource-urls [(str opendap-url ".nc?" "Rainf_tavg,AvgSurfT_inst,SoilMoi0_10cm_inst,time,lat,lon")])
          :data-granule {:size nil}}

         ; AST_FRBT
         "LPDAAC_ECS" ast-l1t "AST_FRBT" "003"
         {:granule-ur "SC:AST_L1T.003:2148809731"
          :related-urls (concat [{:type "GET RELATED VISUALIZATION" :url random-url}]
                                (gen-access-urls [frbt-data-pool-url frbt-egi-url random-url])
                                (gen-browse-urls [frbt-browse-url frbv-browse-url ast-l1t-qa-browse-url random-url])
                                (gen-qa-urls [ast-l1t-qa-url random-url]))
          :product-specific-attributes [{:name "FullResolutionThermalBrowseAvailable" :values ["YES"]}
                                        {:name "identifier_product_doi_authority" :values ["authority"]}]}
         {:granule-ur "SC:AST_FRBT.003:2148809731"
          :related-urls (concat (gen-access-urls [frbt-data-pool-url frbt-egi-url])
                                (gen-browse-urls [frbt-browse-url ast-l1t-qa-browse-url])
                                (gen-qa-urls [ast-l1t-qa-url]))}

         "LPDAAC_ECS" ast-l1t "AST_FRBT" "003"
         {:granule-ur "SC:AST_L1T.003:2148809731"
          :related-urls (concat [{:type "GET RELATED VISUALIZATION" :url random-url}]
                                (gen-access-urls [frbt-data-pool-url frbt-egi-url random-url])
                                (gen-browse-urls [frbt-browse-url frbv-browse-url ast-l1t-qa-browse-url random-url])
                                (gen-qa-urls [ast-l1t-qa-url random-url]))
          :product-specific-attributes [{:name "FullResolutionThermalBrowseAvailable" :values ["NO"]}]}
         {:granule-ur "SC:AST_FRBT.003:2148809731"
          :related-urls (concat (gen-browse-urls [ast-l1t-qa-browse-url])
                                (gen-qa-urls [ast-l1t-qa-url]))}

         ;; AST_FRBV
         "LPDAAC_ECS" ast-l1t "AST_FRBV" "003"
         {:granule-ur "SC:AST_L1T.003:2148809731"
          :related-urls (concat [{:type "GET RELATED VISUALIZATION" :url random-url}]
                                (gen-access-urls [frbt-data-pool-url frbt-egi-url frbv-data-pool-url frbv-egi-url random-url])
                                (gen-browse-urls [frbt-browse-url frbv-browse-url ast-l1t-qa-browse-url random-url])
                                (gen-qa-urls [ast-l1t-qa-url random-url]))
          :product-specific-attributes [{:name "FullResolutionVisibleBrowseAvailable" :values ["YES"]}
                                        {:name "identifier_product_doi" :values ["doi"]}
                                        {:name "some other psa" :values ["psa-val"]}]}
         {:granule-ur "SC:AST_FRBV.003:2148809731"
          :related-urls (concat (gen-access-urls [frbv-data-pool-url frbv-egi-url])
                                (gen-browse-urls [frbv-browse-url ast-l1t-qa-browse-url])
                                (gen-qa-urls [ast-l1t-qa-url]))
          :product-specific-attributes [{:name "some other psa" :values ["psa-val"]}]}

         "LPDAAC_ECS" ast-l1t "AST_FRBV" "003"
         {:granule-ur "SC:AST_L1T.003:2148809731"
          :related-urls (concat [{:type "GET RELATED VISUALIZATION" :url random-url}]
                                (gen-access-urls [frbt-data-pool-url frbt-egi-url frbv-data-pool-url frbv-egi-url random-url])
                                (gen-browse-urls [frbt-browse-url frbv-browse-url ast-l1t-qa-browse-url random-url])
                                (gen-qa-urls [ast-l1t-qa-url random-url]))}
         {:granule-ur "SC:AST_FRBV.003:2148809731"
          :related-urls (concat (gen-browse-urls [ast-l1t-qa-browse-url])
                                (gen-qa-urls [ast-l1t-qa-url]))})))
