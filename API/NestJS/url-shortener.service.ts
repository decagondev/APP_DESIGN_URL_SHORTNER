import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { UrlMapping } from './url-mapping.entity';
import { Analytics } from './analytics.entity';

@Injectable()
export class UrlShortenerService {
  constructor(
    @InjectRepository(UrlMapping)
    private readonly urlMappingRepository: Repository<UrlMapping>,
    @InjectRepository(Analytics)
    private readonly analyticsRepository: Repository<Analytics>,
  ) {}

  async shortenUrl(originalUrl: string): Promise<string> {
    const shortCode = await this.generateUniqueShortCode(originalUrl);
    await this.urlMappingRepository.save({ shortCode, originalUrl });

    return `http://localhost:3000/${shortCode}`;
  }

  async redirectToOriginalUrl(shortCode: string): Promise<{ url: string }> {
    const urlMapping = await this.urlMappingRepository.findOne({ shortCode });

    if (!urlMapping) {
      throw new NotFoundException('Short URL not found');
    }

    await this.logAnalytics(shortCode);

    return { url: urlMapping.originalUrl };
  }

  async getAnalytics(shortCode: string): Promise<Analytics[]> {
    const analytics = await this.analyticsRepository.find({ shortCode });

    if (!analytics || analytics.length === 0) {
      throw new NotFoundException('No analytics data found for the given short code');
    }

    return analytics;
  }

  private async generateShortCode(originalUrl: string): Promise<string> {
    const sha256 = require('crypto').createHash('sha256');
    sha256.update(originalUrl);
    return sha256.digest('hex').substring(0, 8);
  }

  private async checkCollision(shortCode: string): Promise<boolean> {
    return !!(await this.urlMappingRepository.findOne({ shortCode }));
  }

  private async generateUniqueShortCode(originalUrl: string, tries = 0): Promise<string> {
    if (tries >= 10) {
      throw new Error('Failed to generate a unique short code');
    }

    const shortCode = await this.generateShortCode(originalUrl + tries);

    if (await this.checkCollision(shortCode)) {
      return this.generateUniqueShortCode(originalUrl, tries + 1);
    }

    return shortCode;
  }

  private async logAnalytics(shortCode: string): Promise<void> {
    const timestamp = new Date().toISOString().replace('T', ' ').substr(0, 19);
    const ipAddress = '127.0.0.1'; // You may use a library to get the real IP address in a production environment

    await this.analyticsRepository.save({ shortCode, timestamp, ipAddress });
  }
}
