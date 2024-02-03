
import { Body, Controller, Get, Param, Post, Redirect } from '@nestjs/common';
import { UrlShortenerService } from './url-shortener.service';

@Controller()
export class UrlShortenerController {
  constructor(private readonly urlShortenerService: UrlShortenerService) {}

  @Get()
  getHello(): string {
    return 'Welcome to the URL Shortener Service';
  }

  @Post('/shorten')
  async shortenUrl(@Body() body: { originalUrl: string }): Promise<{ shortUrl: string }> {
    const shortUrl = await this.urlShortenerService.shortenUrl(body.originalUrl);
    return { shortUrl };
  }

  @Redirect(':shortCode', 302)
  @Get(':shortCode')
  redirectToOriginalUrl(@Param('shortCode') shortCode: string) {
    return this.urlShortenerService.redirectToOriginalUrl(shortCode);
  }

  @Get('/analytics/:shortCode')
  getAnalytics(@Param('shortCode') shortCode: string) {
    return this.urlShortenerService.getAnalytics(shortCode);
  }
}
