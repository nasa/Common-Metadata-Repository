def get_env_var(name, required=true)
  value = ENV["bamboo_#{name}"] || ENV[name]
  abort "ENV['bamboo_#{name}'] or ENV['#{name}'] must be set." if (required && value.nil?)
  value
end

set :application, get_env_var('cap_application')

set :scm, :none
set :repository, '.'
set :deploy_via, :copy
set :copy_exclude, [ 'config', 'Gemfile', 'Gemfile.lock', 'Capfile' ]

set :user, get_env_var('cap_user')
set :use_sudo, false
set :deploy_to, get_env_var('cap_deploy_to')

after 'deploy:update', 'deploy:cleanup'

set :normalize_asset_timestamps, false
set :shared_children, []

key = get_env_var('cap_key', false)
ssh_options[:keys] = "/data/bamboo_ssh_keys/#{key}" unless key.nil?

ssh_port = get_env_var('cap_ssh_port', false)
ssh_options[:port] = ssh_port unless ssh_port.nil?

get_env_var('cap_servers').split(',').each { |x| server(x, :app) }

namespace :deploy do
  task :restart do
    run("sudo /sbin/service clj-#{application} restart", :pty => true)
  end
  task :stop do
    run("sudo /sbin/service clj-#{application} stop", :pty => true)
  end
end
after 'deploy:cold', 'deploy:restart'
