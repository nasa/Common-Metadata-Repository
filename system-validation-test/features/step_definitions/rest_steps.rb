# frozen_string_literal: true

require 'httparty'
require 'json'
require 'jsonpath'

cmr_root ||= ENV['CMR_ROOT']

# Module to help build CMR queries
module CmrRestfulHelper
  # Appends new values to the query map,
  # if already present the entry will be converted to an array
  def append_query(query, key, value)
    query ||= {}

    # HTTParty supports array of parameters but automatically adds square braces
    key = key[0..-3] if key.match?(/\[\]$/)

    if query.key?(key)
      query[key] = [query[key]] unless query[key].is_a?(Array)
      query[key] << value
    else
      query[key] = value
    end

    query
  end

  # Appends new values to the query map,
  # if already present the entry will be overwritten
  def update_query(query, key, value)
    query ||= {}

    # HTTParty supports array of parameters but automatically adds square braces
    key = key[0..-3] if key.match?(/\[\]$/)
    query[key] = value

    query
  end
end
World CmrRestfulHelper

Given(/^I am (searching|querying|looking) for (an? )?"([\w\d\-_ ]+)"$/) do |_, _, concept_type|
  @resource_url = case concept_type.downcase
                  when /^acls?$/
                    "#{cmr_root}/access-control/acls"
                  when /^groups?$/
                    "#{cmr_root}/access-control/groups"
                  when /^permissions?$/
                    "#{cmr_root}/access-control/permissions"
                  when /^s3-buckets?$/
                    "#{cmr_root}/access-control/s3-buckets"
                  when /^(concept|collection|granule|service|tool|variable)s?$/
                    "#{cmr_root}/search/#{concept_type}"
                  else
                    raise "#{concept_type} searching is not available in CMR"
                  end
end

Given(/^I (use|add) extension "(\.?[\w-]+)"$/) do |_, extension|
  @resource_url += extension
end

Given(/^I (want|ask for|request) (an? )?"(\w+)"( (response|returned))?$/) do |_, _, format, _|
  @url_extension = case format.downcase
                   when 'json', 'xml', 'dif', 'dif10', 'echo10', 'atom', 'native', 'iso', 'iso19115'
                     ".#{format}"
                   when 'umm_json', 'umm'
                     # modern umm
                     '.umm_json'
                   when 'umm-json', 'legacy-umm-json', 'legacy-umm'
                     # legacy umm
                     '.umm-json'
                   else
                     raise "#{format} does not have a mapping to an extension yet"
                   end
end

Given('I reset/clear the query') do
  @query = nil
end

Given(/^I (set|add) (a )?(search|query) (param(eter)?|term) "([\w\d\-_+\[\]]+)=(.*)"$/) do |op, _, _, _, key, value|
  @query = if op == 'add'
             append_query(@query, key, value)
           else
             update_query(@query, key, value)
           end
end

Given(/^I (set|add) (a )?(search|query) (param(eter)?|term) "([\w\d\-_+\[\]]+)" (of|to) "(.*)"$/) do |op, _, _, _, key, _, value|
  @query = if op == 'add'
             append_query(@query, key, value)
           else
             update_query(@query, key, value)
           end
end

Given(/^I (set|add) (a )?(search|query) (param(eter)?|term) "([\w\d\-_+\[\]]+)" using saved value "(.*)"$/) do |op, _, _, _, key, saved_value_key|
  @query = if op == 'add'
             append_query(@query, key, @stashes[saved_value_key])
           else
             update_query(@query, key, @stashes[saved_value_key])
           end
end

When(/^I submit (a|another) "(\w+)" request$/) do |_, method|
  resource_uri = URI("#{@resource_url}#{@url_extension}")

  case method.upcase
  when 'GET'
    options = { query: @query }
    @response = HTTParty.get(resource_uri, options)
  else
    raise "#{method} is not supported yet"
  end
end

Then(/^the response (status( code)?) is (\d+)$/) do |_, status_code|
  expect(@response.code).to eq(status_code)
end

Then('the response Content-Type is {string}') do |content_type|
  actual_type = @response.headers['Content-Type'].split(';')
  expect(actual_type[0]).to eq(content_type)
end

When(/^I save the response (as|into) "(\w[\w\d\-_ ]+)"$/) do |_, name|
  @stashes ||= {}
  @stashes = @stashes.merge({ name => @response })
end

When(/^I save the response body (as|into) "(\w[\w\d\-_ ]+)"$/) do |_, name|
  @stashes ||= {}
  @stashes = @stashes.merge({ name => @response.body })
end

When(/^I save the response headers (as|into) "(\w[\w\d\-_ ]+)"$/) do |_, name|
  @stashes ||= {}
  @stashes = @stashes.merge({ name => @response.headers })
end

When(/^I save the response header "(\w+)" (as|into) "(\w[\w\d\-_ ]+)"$/) do |header, _, name|
  @stashes ||= {}
  @stashes = @stashes.merge({ name => @response.headers[header] })
end
