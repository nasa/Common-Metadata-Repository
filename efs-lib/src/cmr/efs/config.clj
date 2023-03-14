(ns cmr.efs.config
  "Contains functions for retrieving import EFS details from environment variables"
  (:require [cmr.common.config :as cfg :refer [defconfig]]))
 
 (defconfig efs-directory
   "Directory EFS is mounted to"
   {:default "/metadata"})
 
 (defconfig efs-toggle
   "Three-way toggle for EFS functionality. 'efs-off' uses only Oracle, 'efs-on' uses both Oracle and EFS, and 'efs-only' uses only EFS"
   {:default "efs-off"})
 