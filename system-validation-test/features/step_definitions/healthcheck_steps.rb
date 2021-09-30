# frozen_string_literal: true

cmr_root ||= ENV['CMR_ROOT']

Given('I am checking the {string} service status endpoint') do |service|
  @resource_url = "#{cmr_root}/#{service}/health"
end
