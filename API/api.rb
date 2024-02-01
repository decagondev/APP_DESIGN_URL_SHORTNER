require 'sinatra'
require 'json'
require 'digest'

# Data structures to store URL mappings and analytics data
url_mappings = {}
analytics_data = {}

# Function to generate a SHA-256 hash
def sha256(input)
  Digest::SHA256.hexdigest(input)[0, 8]
end

# Function to generate a unique short code
def generate_unique_short_code(original_url)
  tries = 0

  loop do
    short_code = sha256(original_url + tries.to_s)
    return short_code unless url_mappings.key?(short_code)

    tries += 1
    raise 'Failed to generate a unique short code' if tries >= 10
  end
end

# Sinatra route handlers
get '/' do
  'Welcome to the URL Shortener Service'
end

post '/shorten' do
  request.body.rewind
  payload = JSON.parse(request.body.read)

  original_url = payload['original_url']

  return [400, 'Invalid request'] if original_url.nil? || original_url.empty?

  short_code = generate_unique_short_code(original_url)
  url_mappings[short_code] = original_url

  short_url = "#{request.base_url}/#{short_code}"
  content_type :json
  { short_url: short_url }.to_json
end

get '/:short_code' do |short_code|
  original_url = url_mappings[short_code]

  if original_url
    # Log analytics data
    timestamp = Time.now.strftime('%Y-%m-%d %H:%M:%S')
    ip_address = request.ip

    analytics_data[short_code] ||= []
    analytics_data[short_code] << { timestamp: timestamp, ip_address: ip_address }

    redirect original_url, 302
  else
    status 404
    'Short URL not found'
  end
end

get '/analytics/:short_code' do |short_code|
  analytics = analytics_data[short_code]

  if analytics
    content_type :json
    analytics.to_json
  else
    status 404
    'No analytics data found for the given short code'
  end
end

# Run the Sinatra application
set :port, 8080
run Sinatra::Application
