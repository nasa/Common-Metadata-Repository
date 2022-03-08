# frozen_string_literal: true

require 'json'
require 'jsonpath'

Then('the response header {string} is {string}') do |header, value|
  raise 'Blank or empty values not allowed' if value.empty?

  expect(@response.headers[header]).to eq(value)
end

Then('the response header {string} is not {string}') do |header, value|
  raise 'Blank or empty values not allowed' if value.empty?

  expect(@response.headers[header]).not_to eq(value)
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

Then('the response body is empty') do
  pending('Need to set response type to json') unless @response_type == 'json'
  json = JSON.parse(@response.body)
  expect(json['feed']['entry'].length.zero?)
end

Then('saved value {string} is equal to saved value {string}') do |a, b|
  expect(@stashes[a]).to eq(@stashes[b])
end

Then('the response {string} count is at least {int}') do |json_key, length|
  path = JsonPath.new("$..#{json_key}")
  data = JSON.parse(@response.body)
  values = path.on(data)
  expect(values.length).to be >= length
end

Then(/^saved value "([\w\d\-_ ]+)" (does not equal|is not equal to) saved value "([\w\d\-_ ]+)"$/) do |a, _, b|
  expect(@stashes[a]).not_to eq(@stashes[b])
end

Then('the response body contains one of {string}') do |value|
  terms = value.split(',').map(&:strip)

  expect(@response.body).to satisfy do |body|
    terms.reduce(false) { |found, term| found || body.include?(term) }
  end
end
