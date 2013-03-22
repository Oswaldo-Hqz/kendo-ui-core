require 'yaml'

version_data = YAML.load(File.read('VERSION'))

now = Time.now

VERSION_YEAR = version_data['year'].to_i

month = now.month + (now.year - VERSION_YEAR) * 12

VERSION_Q = version_data['release'].to_i
VERSION_SERVICE_PACK = version_data['servicePack']

VERSION = ENV['VERSION'] || "#{VERSION_YEAR}.#{VERSION_Q}.#{month * 100 + now.day}"

CURRENT_COMMIT = `git rev-parse HEAD`.strip
BETA = !!ENV['BETA']

def versioned_bundle_name(bundle)
    ('kendoui.' + bundle).sub(/\.[^\.]+$/, ".#{VERSION}\\0")
end

def latest_bundle_name(bundle)
    ('kendoui.' + bundle).sub(/\.[^\.]+$/, ".latest\\0")
end
