# CocoaPods helper functions for react-native-app-monitor
# Auto-detects working authentication method for private spec repositories

def get_cocoapods_spec_repo_url
  require 'timeout'
  https_url = 'https://github.com/nammayatri/ny-cocoapods-specs.git'
  ssh_url = 'git@github.com:nammayatri/ny-cocoapods-specs.git'
  
  # Test HTTPS access (with timeout to avoid hanging)
  https_works = false
  begin
    Timeout.timeout(5) do
      result = system("git ls-remote #{https_url} > /dev/null 2>&1")
      https_works = result == true
    end
  rescue Timeout::Error, StandardError
    https_works = false
  end
  
  if https_works
    Pod::UI.puts "✓ Using HTTPS for CocoaPods spec repo".green
    return https_url
  else
    # Test SSH access
    ssh_works = false
    begin
      Timeout.timeout(5) do
        result = system("git ls-remote #{ssh_url} > /dev/null 2>&1")
        ssh_works = result == true
      end
    rescue Timeout::Error, StandardError
      ssh_works = false
    end
    
    if ssh_works
      Pod::UI.puts "✓ Using SSH for CocoaPods spec repo (HTTPS failed)".yellow
      return ssh_url
    else
      Pod::UI.puts "⚠ Warning: Could not access private spec repo with HTTPS or SSH".yellow
      Pod::UI.puts "  Falling back to HTTPS. Ensure you have proper authentication configured.".yellow
      return https_url
    end
  end
rescue => e
  # If testing fails, default to HTTPS
  Pod::UI.puts "⚠ Could not test repo access, defaulting to HTTPS".yellow
  return https_url
end

