#include <iostream>
#include <unordered_map>
#include <vector>
#include <sstream>
#include <iomanip>
#include <ctime>
#include <crow_all.h>

// Data structures to store URL mappings and analytics data
std::unordered_map<std::string, std::string> urlMappings;
std::unordered_map<std::string, std::vector<std::string>> analyticsData;

// Function to generate a SHA-256 hash
std::string sha256(const std::string& input) {
    unsigned char hash[SHA256_DIGEST_LENGTH];
    SHA256_CTX sha256;
    SHA256_Init(&sha256);
    SHA256_Update(&sha256, input.c_str(), input.length());
    SHA256_Final(hash, &sha256);

    std::stringstream ss;
    for (int i = 0; i < SHA256_DIGEST_LENGTH; i++) {
        ss << std::hex << std::setw(2) << std::setfill('0') << (int)hash[i];
    }

    return ss.str();
}

// Function to generate a unique short code
std::string generateUniqueShortCode(const std::string& originalUrl) {
    int tries = 0;

    while (tries < 10) {
        std::string shortCode = sha256(originalUrl + std::to_string(tries)).substr(0, 8);

        if (urlMappings.find(shortCode) == urlMappings.end()) {
            return shortCode;
        }

        tries++;
    }

    throw std::runtime_error("Failed to generate a unique short code");
}

// Crow route handlers
int main() {
    crow::SimpleApp app;

    CROW_ROUTE(app, "/")
        ([]() {
            return "Welcome to the URL Shortener Service";
        });

    CROW_ROUTE(app, "/shorten")
        .methods("POST"_method)
        ([](const crow::request& req) {
            auto json = crow::json::load(req.body);
            if (!json) {
                return crow::response(400, "Invalid JSON payload");
            }

            std::string originalUrl = json["original_url"].s();

            if (originalUrl.empty()) {
                return crow::response(400, "Missing original_url parameter");
            }

            std::string shortCode = generateUniqueShortCode(originalUrl);
            urlMappings[shortCode] = originalUrl;

            std::string shortUrl = "http://localhost:8080/" + shortCode;
            return crow::response(201, crow::json::dump({"short_url", shortUrl}));
        });

    CROW_ROUTE(app, "/<string>")
        ([](const crow::request& req, std::string shortCode) {
            auto it = urlMappings.find(shortCode);
            if (it != urlMappings.end()) {
                // Log analytics data
                std::time_t t = std::time(nullptr);
                std::tm* tm = std::localtime(&t);
                std::ostringstream oss;
                oss << std::put_time(tm, "%Y-%m-%d %H:%M:%S");
                std::string timestamp = oss.str();
                std::string ipAddress = req.remote_address;

                analyticsData[shortCode].push_back(timestamp + " " + ipAddress);

                return crow::response(302, it->second);
            } else {
                return crow::response(404, "Short URL not found");
            }
        });

    CROW_ROUTE(app, "/analytics/<string>")
        ([](std::string shortCode) {
            auto it = analyticsData.find(shortCode);
            if (it != analyticsData.end()) {
                return crow::response(crow::json::dump(it->second));
            } else {
                return crow::response(404, "No analytics data found for the given short code");
            }
        });

    app.port(8080).multithreaded().run();
    return 0;
}
