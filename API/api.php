<?php

use Psr\Http\Message\ResponseInterface as Response;
use Psr\Http\Message\ServerRequestInterface as Request;
use Slim\Factory\AppFactory;

require __DIR__ . '/vendor/autoload.php';

// Data structures to store URL mappings and analytics data
$urlMappings = [];
$analyticsData = [];

// Function to generate a SHA-256 hash
function sha256($input)
{
    return substr(hash('sha256', $input), 0, 8);
}

// Function to generate a unique short code
function generateUniqueShortCode($originalUrl)
{
    global $urlMappings;

    $tries = 0;

    while ($tries < 10) {
        $shortCode = sha256($originalUrl . $tries);

        if (!array_key_exists($shortCode, $urlMappings)) {
            return $shortCode;
        }

        $tries++;
    }

    throw new Exception('Failed to generate a unique short code');
}

// Slim application setup
$app = AppFactory::create();

// Define routes
$app->get('/', function (Request $request, Response $response) {
    $response->getBody()->write('Welcome to the URL Shortener Service');
    return $response;
});

$app->post('/shorten', function (Request $request, Response $response) {
    $data = json_decode($request->getBody(), true);

    if (!isset($data['original_url']) || empty($data['original_url'])) {
        return $response->withStatus(400)->write('Invalid request');
    }

    global $urlMappings;

    $originalUrl = $data['original_url'];
    $shortCode = generateUniqueShortCode($originalUrl);

    $urlMappings[$shortCode] = $originalUrl;

    $shortUrl = $request->getUri()->getBaseUrl() . '/' . $shortCode;

    return $response->withJson(['short_url' => $shortUrl])->withStatus(201);
});

$app->get('/{shortCode}', function (Request $request, Response $response, array $args) {
    global $urlMappings, $analyticsData;

    $shortCode = $args['shortCode'];

    if (array_key_exists($shortCode, $urlMappings)) {
        // Log analytics data
        $timestamp = date('Y-m-d H:i:s');
        $ipAddress = $request->getServerParams()['REMOTE_ADDR'];

        $analyticsData[$shortCode][] = ['timestamp' => $timestamp, 'ip_address' => $ipAddress];

        $originalUrl = $urlMappings[$shortCode];
        return $response->withRedirect($originalUrl, 302);
    } else {
        return $response->withStatus(404)->write('Short URL not found');
    }
});

$app->get('/analytics/{shortCode}', function (Request $request, Response $response, array $args) {
    global $analyticsData;

    $shortCode = $args['shortCode'];

    if (array_key_exists($shortCode, $analyticsData)) {
        return $response->withJson($analyticsData[$shortCode]);
    } else {
        return $response->withStatus(404)->write('No analytics data found for the given short code');
    }
});

// Run the Slim application
$app->run();
