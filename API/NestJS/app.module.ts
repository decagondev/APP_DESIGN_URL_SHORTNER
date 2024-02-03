
import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { UrlMapping } from './url-mapping.entity';
import { Analytics } from './analytics.entity';
import { UrlShortenerController } from './url-shortener.controller';
import { UrlShortenerService } from './url-shortener.service';

@Module({
  imports: [TypeOrmModule.forRoot(), TypeOrmModule.forFeature([UrlMapping, Analytics])],
  controllers: [UrlShortenerController],
  providers: [UrlShortenerService],
})
export class AppModule {}
