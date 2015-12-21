(ns cmr.umm-spec.url
  "URL utilities for UMM spec"
  (:import java.net.URL
           java.net.MalformedURLException))

(defn url
  [x]
  (if (isa? x URL)
    x
    (try
      (URL. x)
      (catch MalformedURLException _
        nil))))

(defn protocol
  [x]
  (or
   (some-> x url .getProtocol)
   "http"))
