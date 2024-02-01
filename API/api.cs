using System;
using System.Collections.Generic;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.DependencyInjection;
using Newtonsoft.Json;

public class UrlMapping
{
    public string ShortCode { get; set; }
    public string OriginalUrl { get; set; }
}

public class Analytics
{
    public string Timestamp { get; set; }
    public string IPAddress { get; set; }
}

public class Startup
{
    private Dictionary<string, UrlMapping> urlMappings = new Dictionary<string, UrlMapping>();
    private Dictionary<string, List<Analytics>> analyticsData = new Dictionary<string, List<Analytics>>();

    public void ConfigureServices(IServiceCollection services)
    {
        services.AddMvc();
    }

    public void Configure(IApplicationBuilder app, IWebHostEnvironment env)
    {
        if (env.IsDevelopment())
        {
            app.UseDeveloperExceptionPage();
        }

        app.UseRouting();
        app.UseEndpoints(endpoints =>
        {
            endpoints.MapControllerRoute(
                name: "default",
                pattern: "{controller=Home}/{action=Index}/{id?}");
        });
    }
}

public class HomeController : Controller
{
    private readonly Dictionary<string, UrlMapping> urlMappings;
    private readonly Dictionary<string, List<Analytics>> analyticsData;

    public HomeController(Dictionary<string, UrlMapping> urlMappings, Dictionary<string, List<Analytics>> analyticsData)
    {
        this.urlMappings = urlMappings;
        this.analyticsData = analyticsData;
    }

    [HttpGet]
    public IActionResult Index()
    {
        return Content("Welcome to the URL Shortener Service");
    }

    [HttpPost]
    [Route("/shorten")]
    public IActionResult ShortenUrl([FromBody] UrlRequest request)
    {
        if (request == null || string.IsNullOrEmpty(request.OriginalUrl))
        {
            return BadRequest("Invalid request");
        }

        string shortCode = GenerateUniqueShortCode(request.OriginalUrl);

        urlMappings[shortCode] = new UrlMapping { ShortCode = shortCode, OriginalUrl = request.OriginalUrl };

        string shortUrl = $"{Request.Scheme}://{Request.Host}/{shortCode}";
        return Ok(new { ShortUrl = shortUrl });
    }

    [HttpGet]
    [Route("/{shortCode}")]
    public IActionResult RedirectOriginalUrl(string shortCode)
    {
        if (urlMappings.TryGetValue(shortCode, out var mapping))
        {
            LogAnalytics(shortCode);

            return Redirect(mapping.OriginalUrl);
        }

        return NotFound("Short URL not found");
    }

    [HttpGet]
    [Route("/analytics/{shortCode}")]
    public IActionResult GetAnalytics(string shortCode)
    {
        if (analyticsData.TryGetValue(shortCode, out var data))
        {
            return Ok(data);
        }

        return NotFound("No analytics data found for the given short code");
    }

    private string GenerateShortCode(string originalUrl)
    {
        using (SHA256 sha256 = SHA256.Create())
        {
            byte[] hashBytes = sha256.ComputeHash(Encoding.UTF8.GetBytes(originalUrl));
            string shortCode = BitConverter.ToString(hashBytes).Replace("-", "").Substring(0, 8);
            return shortCode;
        }
    }

    private bool CheckCollision(string shortCode)
    {
        return urlMappings.ContainsKey(shortCode);
    }

    private string GenerateUniqueShortCode(string originalUrl)
    {
        int tries = 0;

        while (tries < 10)
        {
            string shortCode = GenerateShortCode(originalUrl + tries);

            if (!CheckCollision(shortCode))
            {
                return shortCode;
            }

            tries++;
        }

        throw new InvalidOperationException("Failed to generate a unique short code");
    }

    private void LogAnalytics(string shortCode)
    {
        string timestamp = DateTime.UtcNow.ToString("yyyy-MM-dd HH:mm:ss");
        string ipAddress = HttpContext.Connection.RemoteIpAddress.ToString();

        if (!analyticsData.ContainsKey(shortCode))
        {
            analyticsData[shortCode] = new List<Analytics>();
        }

        analyticsData[shortCode].Add(new Analytics { Timestamp = timestamp, IPAddress = ipAddress });
    }

    public class UrlRequest
    {
        public string OriginalUrl { get; set; }
    }

    public class Analytics
    {
        public string Timestamp { get; set; }
        public string IPAddress { get; set; }
    }
}
