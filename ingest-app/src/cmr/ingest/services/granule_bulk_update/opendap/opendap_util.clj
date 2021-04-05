(ns cmr.ingest.services.granule-bulk-update.opendap.opendap-util
  "Contains functions to facilitate OPeNDAP url granule bulk update."
  (:require
   [clojure.string :as string]
   [cmr.common.services.errors :as errors]))

(def ^:private cloud-pattern
  "Defines the Hyrax-in-the-cloud pattern for OPeNDAP url"
  (re-pattern "https://opendap.*\\.earthdata\\.nasa\\.gov/.*"))

(defn url->opendap-type
  "Returns the OPeNDAP type of the given url. It would be either :cloud or :on-prem for valid urls."
  [url]
  (when url
    (if (re-matches cloud-pattern url)
      :cloud
      :on-prem)))

(defn validate-url
  "Validate the given OPeNDAP url for granule bulk update. It can be no more than two urls
  separated by comma, and no more than one url matches the pattern
  https://opendap.*.earthdata.nasa.gov/* and no more than one that does not match the pattern.
  Returns the parsed urls in a map under keys :cloud and :on-prem with the corresponding urls."
  [url]
  (let [urls (map string/trim (string/split url #","))]
    (doseq [url urls]
      (when (string/starts-with? url "s3://")
        (errors/throw-service-errors
         :invalid-data
         [(str "OPeNDAP URL value cannot start with s3://, but was " url)])))
    (if (> (count urls) 2)
      (errors/throw-service-errors
       :invalid-data [(str "Invalid URL value, no more than two urls can be provided: " url)])
      (let [grouped-urls (group-by url->opendap-type urls)]
        (when (> (count (:cloud grouped-urls)) 1)
          (errors/throw-service-errors
           :invalid-data
           [(str "Invalid URL value, no more than one Hyrax-in-the-cloud OPeNDAP url can be provided: "
                 url)]))
        (when (> (count (:on-prem grouped-urls)) 1)
          (errors/throw-service-errors
           :invalid-data
           [(str "Invalid URL value, no more than one on-prem OPeNDAP url can be provided: "
                 url)]))
        grouped-urls))))
