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
                  when 'acl', 'acls'
                    "#{cmr_root}/access-control/acls"
                  when 'group', 'groups'
                    "#{cmr_root}/access-control/groups"
                  when 'permission', 'permissions'
                    "#{cmr_root}/access-control/permissions"
                  when 's3-bucket', 's3-buckets'
                    "#{cmr_root}/access-control/s3-buckets"
                  when 'concepts', 'collections', 'granules', 'services', 'variables', 'tools'
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

Given(/^I (set|add) ((the|a) )?(search|query) (param(eter)?|term) "([\w\d\-_+\[\]]+)=(.*)"$/) do |op, _, _, _, key, value|
  @query = if op == 'add'
             append_query(@query, key, value)
           else
             update_query(@query, key, value)
           end
end

Given(/^I (set|add) ((the|a) )?(search|query) (param(eter)?|term) "([\w\d\-_+\[\]]+)" (of|to) "(.*)"$/) do |op, _, _, _, key, _, value|
  @query = if op == 'add'
             append_query(@query, key, value)
           else
             update_query(@query, key, value)
           end
end

Given(/^I (set|add) ((the|a) )?(search|query) (param(eter)?|term) "([\w\d\-_+\[\]]+)" (of|to) environment value "(.*)"$/) do |op, _, _, _, key, _, env_key|
  raise "No environment value or argument passed in for #{env_key}" if ENV[env_key].to_s.empty?

  @query = if op == 'add'
             append_query(@query, key, ENV[env_key])
           else
             update_query(@query, key, ENV[env_key])
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

Then('the response header {string} to be {string}') do |header, value|
  raise 'Blank or empty values not allowed' if value.empty?

  expect(@response.headers[header]).to eq(value)
end

Then('the response header contains an entry for {string}') do |header|
  expect(@response.headers).to have_key(header.downcase)
end

Then('the response body is {string}') do |body|
  expect(@response.body).to eq(body)
end

Then('the response body contains/includes {string}') do |body|
  expect(@response.body).to include(body)
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

Then('saved value {string} is equal to saved value {string}') do |a, b|
  expect(@stashes[a]).to eq(@stashes[b])
end

Then('saved value {string} does not equal saved value {string}') do |a, b|
  expect(@stashes[a]).not_to eq(@stashes[b])
end

When('I save the json response feed entries as {string}') do |key|
  path = JsonPath.new('$..entry')
  data = JSON.parse(@response.body)

  entries = path.on(data)

  @stashes ||= {}
  @stashes = @stashes.merge({ key => entries })
end

Then('the json response entries count is at least {int}') do |length|
  path = JsonPath.new('$..entry')
  data = JSON.parse(@response.body)

  entries = path.on(data)
  expect(entries.length).to be >= length
end
