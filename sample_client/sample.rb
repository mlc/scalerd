#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'

Bundler.require

config = {}

unless ARGV.length == 2
  raise "I need exactly two arguments, the URI and the operations"
end

uri, operations = ARGV

File.open("../scalerd.properties", "r").lines do |line|
  key, value = line.strip.split(/ *= */, 2)
  config[key] = value
end

exchange_name = config["amqp.exchange"]
queue_name = config["amqp.queue"]

EventMachine.run do
  connection = AMQP.connect(config["amqp.uri"])
  AMQP::Channel.new(connection) do |channel|
    exchange = channel.direct(exchange_name, :durable => true, :auto_delete => false)
    channel.queue '', :auto_delete => true do |reply_queue|
      message = MultiJson.dump({:uri => uri, :operations => operations})
      reply_queue.subscribe do |headers, payload|
        $stderr.puts headers.content_type
        $stdout << payload
        EventMachine.stop { exit }
      end
      exchange.publish message, :routing_key => queue_name, :content_type => 'application/json', :reply_to => reply_queue.name
    end
  end
end