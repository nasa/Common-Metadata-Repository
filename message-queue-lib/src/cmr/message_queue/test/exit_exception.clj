(ns cmr.message-queue.test.ExitException
  "Defines an exception class that is used in tests
  to force queue worker threads to exit."
  (:gen-class :extends java.lang.Exception))