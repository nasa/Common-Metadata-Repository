# encoding: utf-8
#From https://gist.github.com/ato/5935594

require 'erb'
require 'ostruct'
require 'java'
require 'set'
require 'active_support/all'
# require 'active_support'
require 'action_view'
load 'collection_preview/drafts_helper.rb'
load 'collection_preview/collections_helper.rb'

include ActionView::Helpers

include DraftsHelper
include CollectionsHelper

####################################################################################################
## Rendering

# Renders an ERB template against a hashmap of variables.
# template should be a Java InputStream
def java_render(template, variables)
  context = OpenStruct.new(variables).instance_eval do
    variables.each do |k, v|
      instance_variable_set(k, v) if k[0] == '@'
    end

    def partial(partial_name, options={})
      new_variables = marshal_dump.merge(options[:locals] || {})
      Java::Pavo::ERB.render(partial_name, new_variables)
    end

    binding
  end
  ERB.new(template.to_io.read).result(context);
end

def partial_path_to_resource_path(partial_path)
  parts = partial_path.split("/")
  file_name = "_#{parts.last}.html.erb"
  parts = parts[0..-2] + [file_name]
  parts.join("/")
end

# Renders an ERB template against a hashmap of variables.
def render(args)
  begin
    partial_path = args[:partial]
    if partial_path.respond_to? :call
      partial_path = partial_path()
    end

    resource_path = partial_path_to_resource_path(partial_path)
    class_loader = Java::Java::lang::Thread.currentThread.getContextClassLoader
    input_stream = class_loader.getResourceAsStream(resource_path)

    locals = args[:locals]
    puts "Rendering #{resource_path} with #{locals.inspect}"
    java_render(input_stream, locals)
  rescue Exception => e
    puts e.message
    puts e.backtrace
    raise e
   end
end

# Renders an ERB template wrapped in an ERB layout.
# template and layout should both be Java InputStreams
def render_with_layout(template, layout, variables)
  render(layout, variables) do
    render(template, variables)
  end
end
