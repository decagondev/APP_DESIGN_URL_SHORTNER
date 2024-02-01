package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http"
	"sync"
	"time"

	"github.com/gorilla/mux"
)

// UrlMapping represents the mapping between short code and original URL.
type UrlMapping struct {
	ShortCode   string
	OriginalURL string
}

// Analytics represents the analytics data for a shortened URL access.
type Analytics struct {
	Timestamp string
	IPAddress string
}

var (
	urlMappings   = make(map[string]UrlMapping)
	analyticsData = make(map[string][]Analytics)
	mu            sync.Mutex
)

func main() {
	r := mux.NewRouter()

	r.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprint(w, "Welcome to the URL Shortener Service")
	}).Methods("GET")

	r.HandleFunc("/shorten", shortenURL).Methods("POST")
	r.HandleFunc("/{shortCode}", redirectToOriginalURL).Methods("GET")
	r.HandleFunc("/analytics/{shortCode}", getAnalytics).Methods("GET")

	http.Handle("/", r)

	fmt.Println("Server is running on :8080")
	http.ListenAndServe(":8080", nil)
}

func shortenURL(w http.ResponseWriter, r *http.Request) {
	var req struct {
		OriginalURL string `json:"original_url"`
	}

	err := json.NewDecoder(r.Body).Decode(&req)
	if err != nil {
		http.Error(w, "Invalid JSON payload", http.StatusBadRequest)
		return
	}

	shortCode := generateUniqueShortCode(req.OriginalURL)

	mu.Lock()
	defer mu.Unlock()

	urlMappings[shortCode] = UrlMapping{ShortCode: shortCode, OriginalURL: req.OriginalURL}

	shortURL := fmt.Sprintf("http://localhost:8080/%s", shortCode)
	response := map[string]string{"short_url": shortURL}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func redirectToOriginalURL(w http.ResponseWriter, r *http.Request) {
	params := mux.Vars(r)
	shortCode := params["shortCode"]

	mu.Lock()
	defer mu.Unlock()

	if mapping, exists := urlMappings[shortCode]; exists {
		// Log analytics data
		timestamp := time.Now().Format("2006-01-02 15:04:05")
		ipAddress := r.RemoteAddr

		analyticsData[shortCode] = append(analyticsData[shortCode], Analytics{Timestamp: timestamp, IPAddress: ipAddress})

		http.Redirect(w, r, mapping.OriginalURL, http.StatusMovedPermanently)
		return
	}

	http.NotFound(w, r)
}

func getAnalytics(w http.ResponseWriter, r *http.Request) {
	params := mux.Vars(r)
	shortCode := params["shortCode"]

	mu.Lock()
	defer mu.Unlock()

	if data, exists := analyticsData[shortCode]; exists {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(data)
		return
	}

	http.NotFound(w, r)
}

func generateShortCode(originalURL string) string {
	hash := sha256.New()
	hash.Write([]byte(originalURL))
	return hex.EncodeToString(hash.Sum(nil))[:8] // Using the first 8 characters for simplicity
}

func checkCollision(shortCode string) bool {
	_, exists := urlMappings[shortCode]
	return exists
}

func generateUniqueShortCode(originalURL string) string {
	for {
		shortCode := generateShortCode(originalURL)
		if !checkCollision(shortCode) {
			return shortCode
		}
	}
}
