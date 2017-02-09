require 'java'

# The benchmark module doesn't quite do the job for us, since we may want to
# test across multiple specs

module Metrics
  def self.times
    @times ||= {}
  end

  def self.time(key)
    times[key] ||= 0.0
    start = java.lang.System.nanoTime
    result = yield
    times[key] += java.lang.System.nanoTime - start
    result
  end
end

Kernel.at_exit do
  Metrics.times.each do |key, time|
    puts "%.3fs #{key}" % (time / 1000000000.0)
  end
end


