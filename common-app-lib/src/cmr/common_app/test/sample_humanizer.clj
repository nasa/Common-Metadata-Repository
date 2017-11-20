(ns cmr.common-app.test.sample-humanizer
  "Contains sample humanizer helper function for testing"
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]))

(def sample-humanizers
  "A sample humanizer for testing. It is referenced in system-int-test and search-app."
  (remove string? (json/decode (slurp (io/resource "test-humanizers.json")) true)))
