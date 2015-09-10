(ns cmr.virtual-product.test.config
  (:require [clojure.test :refer :all]
            [cmr.virtual-product.config :as vp-config]))

(deftest omuvbd-update-online-access-url-test
  (let [src-granule-ur "OMUVBd.003:OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m0105t093001.he5"
        url1 "http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5//Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m0105t093001.he5.nc"
        url2 "http://s4psci.gesdisc.eosdis.nasa.gov/data/s4pa_TS2//Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m0105t093001.he5.nc"
        url3 "http://s4psci.gesdisc.eosdis.nasa.gov/data/s4pa//Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m0105t093001.he5.nc"
        url4 "http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5//Aura_OMI_Level3/OMUVBd.003/2015/OMI-Aura_L3-OMUVBd_2015m0101_v003-2015m0105t093003.he5.nc"
        online-access-urls [url1 url2 url3 url4]
        url-objects (map #(hash-map :type "GET DATA" :url %) online-access-urls)
        expected-translation [(str url1 "?ErythemalDailyDose,ErythemalDoseRate,UVindex,lon,lat")
                              url2 url3 url4]]
    (is (= expected-translation
           (map :url (#'vp-config/update-online-access-url url-objects src-granule-ur))))))