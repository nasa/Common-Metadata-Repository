# frozen_string_literal: true

no_cmr_msg = <<-MSG
  Please set the CMR_ROOT variable in the environment or pass it to cucumber as an argument
  e.g. "cucumber CMR_ROOT=https://cmr.my-instance.com"
MSG

raise no_cmr_msg if ENV['CMR_ROOT'].to_s.empty?
